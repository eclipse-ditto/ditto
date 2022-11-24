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
package org.eclipse.ditto.internal.utils.akka.controlflow;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorAttributes;
import akka.stream.Attributes;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.QueueOfferResult;
import akka.stream.Supervision;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueue;
import akka.stream.javadsl.SourceQueueWithComplete;

/**
 * Actor whose behavior is defined entirely by an Akka stream graph.
 *
 * @param <T> the type of the messages this actor processes in the stream graph.
 * @param <M> the type of the incoming messages which is translated to a message of type {@code <T>} in
 * {@link #mapMessage(Object)}.
 */
public abstract class AbstractGraphActor<T, M> extends AbstractActor {

    protected final ThreadSafeDittoLoggingAdapter logger;
    protected final Materializer materializer;

    private final Class<M> matchClass;
    private final Counter receiveCounter;
    private final Counter enqueueSuccessCounter;
    private final Counter enqueueDroppedCounter;
    private final Counter enqueueFailureCounter;
    private final Counter dequeueCounter;

    /**
     * Constructs a new AbstractGraphActor object.
     *
     * @param matchClass the type of the message to be streamed if matched in this actor's receive handler.
     * @param loggerEnhancer a function which can enhance the {@code logger} of this instance, e.g. with additional MDC
     * values.
     * @throws NullPointerException if {@code matchClass} is {@code null}.
     */
    @SuppressWarnings("unchecked")
    protected AbstractGraphActor(final Class<?> matchClass,
            final UnaryOperator<ThreadSafeDittoLoggingAdapter> loggerEnhancer) {
        this.matchClass = checkNotNull((Class<M>) matchClass, "matchClass");

        final var tagSet = TagSet.ofTag(Tag.of("class", getClass().getSimpleName()));
        receiveCounter = DittoMetrics.counter("graph_actor_receive", tagSet);
        enqueueSuccessCounter = DittoMetrics.counter("graph_actor_enqueue_success", tagSet);
        enqueueDroppedCounter = DittoMetrics.counter("graph_actor_enqueue_dropped", tagSet);
        enqueueFailureCounter = DittoMetrics.counter("graph_actor_enqueue_failure", tagSet);
        dequeueCounter = DittoMetrics.counter("graph_actor_dequeue", tagSet);

        this.logger = loggerEnhancer.apply(DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this));
        materializer = Materializer.createMaterializer(this::getContext);
    }

    /**
     * Provides a {@code T} by passing each single {@code message}s this Actor received.
     *
     * @param message the currently processed message of this Actor.
     * @return the created Source.
     */
    protected abstract T mapMessage(M message);

    /**
     * Called when a message is dropped as result of backpressure strategy.
     * Provides the possibility to add custom handling of dropped messages by implementations of AbstractGraphActor.
     *
     * @param message the dropped message.
     */
    protected void messageDiscarded(M message, QueueOfferResult result) {
        // do nothing by default
    }

    /**
     * Called before handling the actual message via the {@link #createSink()} in order to being able to enhance
     * the message.
     *
     * @param message the message to be handled.
     * @return the (potentially) adjusted message before handling.
     */
    protected T beforeProcessMessage(final T message) {
        return message;
    }

    /**
     * @return the Sink handling the messages of type {@code T} this graph actor handles.
     */
    protected abstract Sink<T, ?> createSink();

    /**
     * @return the buffer size used for the Source queue.
     */
    protected abstract int getBufferSize();

    @Override
    public Receive createReceive() {
        final SourceQueueWithComplete<T> sourceQueue = getSourceQueue(materializer);

        final var receiveBuilder = ReceiveBuilder.create();
        preEnhancement(receiveBuilder);
        return receiveBuilder
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(matchClass, match -> handleMatched(sourceQueue, match))
                .match(Throwable.class, this::handleUnknownThrowable)
                .matchAny(message -> logger.warning("Received unknown message <{}>.", message))
                .build();
    }

    private Attributes getSupervisionStrategyAttribute() {
        final String graphActorClassName = getClass().getSimpleName();
        return ActorAttributes.withSupervisionStrategy(exc -> {
                    if (exc instanceof DittoRuntimeException) {
                        logger.withCorrelationId((WithDittoHeaders) exc)
                                .warning("DittoRuntimeException in stream of {}: [{}] {}",
                                        graphActorClassName, exc.getClass().getSimpleName(), exc.getMessage());
                    } else {
                        logger.error(exc, "Exception in stream of {}: {}",
                                graphActorClassName, exc.getMessage());
                    }
                    return (Supervision.Directive) Supervision.resume(); // in any case, resume!
                }
        );
    }

    private SourceQueueWithComplete<T> getSourceQueue(final Materializer materializer) {
        // Log stream completion and failure at level ERROR because the stream is supposed to survive forever.
        final var streamLogLevels =
                Attributes.logLevels(Attributes.logLevelDebug(), Attributes.logLevelError(),
                        Attributes.logLevelError());

        return Source.<T>queue(getBufferSize(), OverflowStrategy.dropNew())
                .map(this::incrementDequeueCounter)
                .via(Flow.fromFunction(this::beforeProcessMessage))
                .to(createSink())
                .withAttributes(streamLogLevels.and(getSupervisionStrategyAttribute()))
                .run(materializer);
    }

    private <E> E incrementDequeueCounter(final E element) {
        dequeueCounter.increment();
        return element;
    }

    /**
     * Provides the possibility to add custom matchers before applying the default matchers of the AbstractGraphActor.
     *
     * @param receiveBuilder the ReceiveBuilder to add other matchers to.
     */
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        // do nothing by default
    }

    /**
     * Handles DittoRuntimeExceptions by sending them back to the {@link #getSender() sender}.
     * Overwrite to introduce a custom exception handling.
     *
     * @param dittoRuntimeException the DittoRuntimeException to handle.
     */
    protected void handleDittoRuntimeException(final DittoRuntimeException dittoRuntimeException) {
        logger.withCorrelationId(dittoRuntimeException).debug("Received <{}>.", dittoRuntimeException);
        getSender().tell(dittoRuntimeException, getSelf());
    }

    private void handleMatched(final SourceQueue<T> sourceQueue, final M match) {
        final ThreadSafeDittoLoggingAdapter loggerWithCID;
        if (match instanceof final WithDittoHeaders withDittoHeaders) {
            loggerWithCID = logger.withCorrelationId(withDittoHeaders);
        } else {
            loggerWithCID = logger;
        }
        if (match instanceof final WithEntityId withEntityId) {
            loggerWithCID.debug("Received <{}> with ID <{}>.", match.getClass().getSimpleName(),
                    withEntityId.getEntityId());
        } else {
            loggerWithCID.debug("Received match: <{}>.", match);
        }
        receiveCounter.increment();
        sourceQueue.offer(mapMessage(match))
                .whenComplete(this::incrementEnqueueCounters)
                .thenAccept(result -> {
                    if (!result.isEnqueued()) {
                        messageDiscarded(match, result);
                    }
                });
    }

    private void incrementEnqueueCounters(@Nullable final QueueOfferResult result, @Nullable final Throwable error) {
        if (QueueOfferResult.enqueued().equals(result)) {
            enqueueSuccessCounter.increment();
        } else if (QueueOfferResult.dropped().equals(result)) {
            enqueueDroppedCounter.increment();
            logger.error("Dropped message as result of backpressure strategy! - result was: {} - adjust queue " +
                    "size or scaling if this appears regularly", result);
        } else if (result instanceof QueueOfferResult.Failure failure) {
            logger.error(failure.cause(), "Enqueue failed!");
            enqueueFailureCounter.increment();
        } else {
            enqueueFailureCounter.increment();
            if (error == null) {
                logger.error("Enqueue failed - result was: {}", result);
            } else {
                logger.error(error, "Enqueue failed without acknowledgement!");
            }
        }
    }

    private void handleUnknownThrowable(final Throwable unknownThrowable) {
        logger.warning("Received unknown Throwable <{}>!", unknownThrowable);
        final DittoInternalErrorException gatewayInternalError = DittoInternalErrorException.newBuilder()
                .cause(unknownThrowable)
                .build();
        getSender().tell(gatewayInternalError, getSelf());
    }

}
