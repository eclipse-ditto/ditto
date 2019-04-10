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

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.Done;
import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.function.Function2;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
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
    protected abstract Source<T, NotUsed> mapMessage(Object message);

    /**
     * @return the Sink handling the messages of type {@link #getMessageClass()} this graph actor handles.
     */
    protected abstract Flow<T, T, NotUsed> getHandler();

    @Override
    public Receive createReceive() {

        final ActorMaterializer materializer = ActorMaterializer.create(getContext());
        final Sink<T, NotUsed> messageHandler = createMessageHandler(materializer);

        return ReceiveBuilder.create()
                .matchAny(message -> {
                    log.debug("Received message: <{}>", message);
                    mapMessage(message).runWith(messageHandler, materializer);
                })
                .build();
    }

    private Sink<T, NotUsed> createMessageHandler(final ActorMaterializer materializer) {
        final ActorContext context = getContext();
        final ActorRef self = getSelf();

        return MergeHub.of(getMessageClass(), 1)
                .to(getHandler()
                        .watchTermination(handleTermination(context, self))
                        .to(Sink.foreach(msg -> log.warning("Unhandled message: <{}>", msg)))
                )
                .run(materializer);
    }

    private Function2<NotUsed, CompletionStage<Done>, NotUsed> handleTermination(
            final ActorContext context, final ActorRef self) {

        return (notUsed, doneCompletionStage) -> {
            doneCompletionStage.whenComplete((done, ex) -> {
                if (done != null) {
                    log.info("Terminating actor due to inactivity: <{}>", self);
                } else {
                    log.warning(
                            "Unexpected exception when watching Termination of stream - {}: {}",
                            ex.getClass().getSimpleName(), ex.getMessage());
                }
                // stop actor in both cases:
                context.stop(self);
            });
            return notUsed;
        };
    }

}
