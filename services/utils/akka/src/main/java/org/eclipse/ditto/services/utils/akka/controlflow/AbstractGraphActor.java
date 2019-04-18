/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.akka.controlflow;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;

import akka.Done;
import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.Supervision;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.MergeHub;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Actor whose behavior is defined entirely by an Akka stream graph.
 */
public abstract class AbstractGraphActor<T> extends AbstractActor {

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    /**
     * @return the type of the messages this graph actor's Source emits.
     */
    protected abstract Class<T> getMessageClass();

    /**
     * Provides a Source by passing each single {@code message}s this Actor received.
     *
     * @param message the currently processed message of this Actor.
     * @return the created Source.
     */
    protected abstract Source<T, NotUsed> mapMessage(WithDittoHeaders<?> message);

    /**
     * @return the Sink handling the messages of type {@link #getMessageClass()} this graph actor handles.
     */
    protected abstract Flow<T, T, NotUsed> getHandler();

    @Override
    public Receive createReceive() {

        final ActorSystem actorSystem = getContext().getSystem();
        final ActorMaterializerSettings materializerSettings = ActorMaterializerSettings.create(actorSystem)
                .withSupervisionStrategy((Function<Throwable, Supervision.Directive>) exc -> {
                            if (exc instanceof DittoRuntimeException) {
                                LogUtil.enhanceLogWithCorrelationId(log, (DittoRuntimeException) exc);
                                log.warning("DittoRuntimeException during materialization of AbstractGraphActor: [{}] {}",
                                        exc.getClass().getSimpleName(), exc.getMessage());
                            } else {
                                log.error(exc,"Exception during materialization of of AbstractGraphActor: {}", exc.getMessage());
                            }
                            return Supervision.resume(); // in any case, resume!
                        }
                );
        final ActorMaterializer materializer = ActorMaterializer.create(materializerSettings, getContext());
        final Sink<T, NotUsed> messageHandler = createMessageHandler(materializer);

        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create();
        preEnhancement(receiveBuilder);
        return receiveBuilder
                .match(DittoRuntimeException.class, dittoRuntimeException -> {
                    log.debug("Received DittoRuntimeException: <{}>", dittoRuntimeException);
                    sender().tell(dittoRuntimeException, self());
                })
                .match(WithDittoHeaders.class, withDittoHeaders -> {
                    log.debug("Received WithDittoHeaders: <{}>", withDittoHeaders);
                    mapMessage(withDittoHeaders).runWith(messageHandler, materializer);
                })
                .match(Throwable.class, unknownThrowable -> {
                    log.warning("Received unknown Throwable: <{}>", unknownThrowable);
                    final GatewayInternalErrorException gatewayInternalError =
                            GatewayInternalErrorException.newBuilder()
                                    .cause(unknownThrowable)
                                    .build();
                    sender().tell(gatewayInternalError, self());
                })
                .matchAny(message -> log.warning("Received unknown message: <{}>", message))
                .build();
    }

    /**
     * Provides the possibility to add custom matchers before applying the default matchers of the AbstractGraphActor.
     *
     * @param receiveBuilder the ReceiveBuilder to add other matchers to.
     */
    protected abstract void preEnhancement(final ReceiveBuilder receiveBuilder);

    private Sink<T, NotUsed> createMessageHandler(final ActorMaterializer materializer) {
        final ActorRef self = getSelf();

        return MergeHub.of(getMessageClass(), 16) // default value according to Akka docs is 16
                .to(getHandler()
                        .watchTermination(handleTermination(self))
                        .to(Sink.foreach(msg -> log.debug("Unhandled message: <{}>", msg)))
                )
                .run(materializer);
    }

    private Function2<NotUsed, CompletionStage<Done>, NotUsed> handleTermination(final ActorRef self) {

        return (notUsed, doneCompletionStage) -> {
            doneCompletionStage.whenComplete((done, ex) -> {
                if (done != null) {
                    log.warning("Stream was completed which should never happen: <{}>", self);
                } else {
                    log.warning(
                            "Unexpected exception when watching Termination of stream - {}: {}",
                            ex.getClass().getSimpleName(), ex.getMessage());
                }
            });
            return notUsed;
        };
    }

}
