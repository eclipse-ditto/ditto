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
import akka.actor.ActorSystem;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.OverflowStrategy;
import akka.stream.QueueOfferResult;
import akka.stream.Supervision;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;

/**
 * Actor whose behavior is defined entirely by an Akka stream graph.
 */
public abstract class AbstractGraphActor<T> extends AbstractActor {

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final int bufferSize;
    private final int parallelism;

    /**
     * @param bufferSize the buffer size used for the Source queue. After this limit is reached, the mailbox
     * of this actor will rise and buffer messages as part of the backpressure strategy.
     * @param parallelism parallelism to use for processing messages in parallel.
     * When configured too low, throughput of messages which perform blocking operations will be bad.
     */
    protected AbstractGraphActor(final int bufferSize, final int parallelism) {

        this.bufferSize = bufferSize;
        this.parallelism = parallelism;
    }

    /**
     * Provides a {@code T} by passing each single {@code message}s this Actor received.
     *
     * @param message the currently processed message of this Actor.
     * @return the created Source.
     */
    protected abstract T mapMessage(WithDittoHeaders<?> message);

    /**
     * @return the Sink handling the messages of type {@code T} this graph actor handles.
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
                                log.error(exc, "Exception during materialization of of AbstractGraphActor: {}",
                                        exc.getMessage());
                            }
                            return Supervision.resume(); // in any case, resume!
                        }
                );
        final ActorMaterializer materializer = ActorMaterializer.create(materializerSettings, getContext());

        final SourceQueueWithComplete<T> sourceQueue =
                Source.<T>queue(bufferSize, OverflowStrategy.backpressure())
                        .mapAsyncUnordered(parallelism, msg ->
                                Source.single(msg)
                                        .via(getHandler())
                                        .runWith(Sink.ignore(), materializer)
                        )
                        .watchTermination(handleTermination())
                        .toMat(Sink.ignore(), Keep.left())
                        .run(materializer);

        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create();
        preEnhancement(receiveBuilder);
        return receiveBuilder
                .match(DittoRuntimeException.class, dittoRuntimeException -> {
                    log.debug("Received DittoRuntimeException: <{}>", dittoRuntimeException);
                    sender().tell(dittoRuntimeException, self());
                })
                .match(WithDittoHeaders.class, withDittoHeaders -> {
                    LogUtil.enhanceLogWithCorrelationId(log, withDittoHeaders);
                    log.debug("Received WithDittoHeaders: <{}>", withDittoHeaders);
                    final QueueOfferResult queueOfferResult = sourceQueue.offer(mapMessage(withDittoHeaders))
                            .toCompletableFuture()
                            .join(); // blocks the Actor from processing new messages
                    // used intentionally in combination with OverflowStrategy.backpressure()
                    log.debug("queueOfferResult: <{}>", queueOfferResult);
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

    private Function2<SourceQueueWithComplete<T>, CompletionStage<Done>, SourceQueueWithComplete<T>> handleTermination() {

        return (notUsed, doneCompletionStage) -> {
            doneCompletionStage.whenComplete((done, ex) -> {
                if (done != null) {
                    log.warning("Stream was completed which should never happen.");
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
