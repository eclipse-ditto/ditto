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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Map;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.japi.function.Function;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.OverflowStrategy;
import akka.stream.QueueOfferResult;
import akka.stream.Supervision;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Partition;
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

    /**
     * For {@code signals} marked with a DittoHeader with that key, the "special enforcement lane" shall be used &ndash;
     * meaning that those messages are processed not based on the hash of their ID but in a common "special lane".
     * <p>
     * Be aware that when using this, all those signals will be effectively sequentially processed but they could
     * be processed in parallel to other signals whose IDs have the same hash partition in {@link AbstractGraphActor}.
     * </p>
     */
    public static final String DITTO_INTERNAL_SPECIAL_ENFORCEMENT_LANE = "ditto-internal-special-enforcement-lane";

    protected final DittoDiagnosticLoggingAdapter logger;
    protected final ActorMaterializer materializer;

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
     * @throws NullPointerException if {@code matchClass} is {@code null}.
     */
    protected AbstractGraphActor(final Class<M> matchClass) {
        this.matchClass = checkNotNull(matchClass, "matchClass");

        final Map<String, String> tags = Collections.singletonMap("class", getClass().getSimpleName());
        receiveCounter = DittoMetrics.counter("graph_actor_receive", tags);
        enqueueSuccessCounter = DittoMetrics.counter("graph_actor_enqueue_success", tags);
        enqueueDroppedCounter = DittoMetrics.counter("graph_actor_enqueue_dropped", tags);
        enqueueFailureCounter = DittoMetrics.counter("graph_actor_enqueue_failure", tags);
        dequeueCounter = DittoMetrics.counter("graph_actor_dequeue", tags);

        logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        materializer = ActorMaterializer.create(getActorMaterializerSettings(), getContext());
    }

    /**
     * Provides a {@code T} by passing each single {@code message}s this Actor received.
     *
     * @param message the currently processed message of this Actor.
     * @return the created Source.
     */
    protected abstract T mapMessage(M message);

    /**
     * Called before handling the actual message via the {@link #processMessageFlow()} in order to being able to enhance
     * the message.
     *
     * @param message the message to be handled.
     * @return the (potentially) adjusted message before handling.
     */
    protected T beforeProcessMessage(final T message) {
        return message;
    }

    /**
     * @return the Flow processing the messages of type {@code T} this graph actor handles.
     */
    protected abstract Flow<T, T, NotUsed> processMessageFlow();

    /**
     * @return the Sink handling the processed messages of type {@code T} this graph actor handles.
     */
    protected abstract Sink<T, ?> processedMessageSink();

    /**
     * @return the buffer size used for the Source queue.
     */
    protected abstract int getBufferSize();

    /**
     * @return parallelism to use for processing messages in parallel. When configured too low, throughput of
     * messages which perform blocking operations will be bad.
     */
    protected abstract int getParallelism();

    @Override
    public Receive createReceive() {
        final SourceQueueWithComplete<T> sourceQueue = getSourceQueue(materializer);

        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create();
        preEnhancement(receiveBuilder);
        return receiveBuilder
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(matchClass, match -> handleMatched(sourceQueue, match))
                .match(Throwable.class, this::handleUnknownThrowable)
                .matchAny(message -> logger.warning("Received unknown message <{}>.", message))
                .build();
    }

    private ActorMaterializerSettings getActorMaterializerSettings() {
        final String graphActorClassName = getClass().getSimpleName();
        return ActorMaterializerSettings.create(getContext().getSystem())
                .withSupervisionStrategy((Function<Throwable, Supervision.Directive>) exc -> {
                            if (exc instanceof DittoRuntimeException) {
                                logger.withCorrelationId((WithDittoHeaders<?>) exc)
                                        .warning("DittoRuntimeException in stream of {}: [{}] {}",
                                                graphActorClassName, exc.getClass().getSimpleName(), exc.getMessage());
                            } else {
                                logger.error(exc, "Exception in stream of {}: {}",
                                        graphActorClassName, exc.getMessage());
                            }
                            return Supervision.resume(); // in any case, resume!
                        }
                );
    }

    private SourceQueueWithComplete<T> getSourceQueue(final ActorMaterializer materializer) {
        // Log stream completion and failure at level ERROR because the stream is supposed to survive forever.
        final Attributes streamLogLevels =
                Attributes.logLevels(Attributes.logLevelDebug(), Attributes.logLevelError(),
                        Attributes.logLevelError());

        return Source.<T>queue(getBufferSize(), OverflowStrategy.dropNew())
                .map(this::incrementDequeueCounter)
                .log("graph-actor-stream-1-dequeued", logger)
                .withAttributes(streamLogLevels)
                .via(Flow.fromFunction(this::beforeProcessMessage))
                .log("graph-actor-stream-2-preprocessed", logger)
                .withAttributes(streamLogLevels)
                // partition by the message's ID in order to maintain order per ID
                .via(partitionById(processMessageFlow(), getParallelism()))
                .log("graph-actor-stream-3-partitioned", logger)
                .withAttributes(streamLogLevels)
                .to(processedMessageSink())
                .run(materializer);
    }

    private <E> E incrementDequeueCounter(final E element) {
        dequeueCounter.increment();
        return element;
    }

    /**
     * Partitions the passed in {@code flowToPartition} based on the flowing through IDs of {@link WithId} messages.
     * That means that e.g. each Thing-ID gets its own partition, so messages to that Thing are sequentially processed
     * and thus the order is maintained.
     *
     * @param flowToPartition the Flow to apply the partitioning on.
     * @param parallelism the parallelism to use (how many partitions to process in parallel) - which should be based
     * on the amount of available CPUs.
     * @return the partitioning flow
     */
    private static <T> Flow<T, T, NotUsed> partitionById(final Graph<FlowShape<T, T>, NotUsed> flowToPartition,
            final int parallelism) {

        final int parallelismWithSpecialLane = parallelism + 1;

        final IdPartitioner<T> partitioner = IdPartitioner.of(DITTO_INTERNAL_SPECIAL_ENFORCEMENT_LANE, parallelism);

        return Flow.fromGraph(GraphDSL.create(Partition.<T>create(parallelismWithSpecialLane, partitioner),
                Merge.<T>create(parallelismWithSpecialLane, true),
                (nA, nB) -> nA,
                (builder, partition, merge) -> {
                    for (int i = 0; i < parallelismWithSpecialLane; i++) {
                        builder.from(partition.out(i))
                                .via(builder.add(flowToPartition))
                                .toInlet(merge.in(i));
                    }
                    return FlowShape.of(partition.in(), merge.out());
                }));
    }

    /**
     * Provides the possibility to add custom matchers before applying the default matchers of the AbstractGraphActor.
     *
     * @param receiveBuilder the ReceiveBuilder to add other matchers to.
     */
    protected abstract void preEnhancement(ReceiveBuilder receiveBuilder);

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
        if (match instanceof WithDittoHeaders) {
            logger.setCorrelationId((WithDittoHeaders<?>) match);
        }
        if (match instanceof WithId) {
            logger.debug("Received <{}> with ID <{}>.", match.getClass().getSimpleName(),
                    ((WithId) match).getEntityId());
        } else {
            logger.debug("Received match: <{}>.", match);
        }
        logger.discardCorrelationId();
        receiveCounter.increment();
        sourceQueue.offer(mapMessage(match)).handle(this::incrementEnqueueCounters);
    }

    private Void incrementEnqueueCounters(final QueueOfferResult result, final Throwable error) {
        if (QueueOfferResult.enqueued().equals(result)) {
            enqueueSuccessCounter.increment();
        } else if (QueueOfferResult.dropped().equals(result)) {
            enqueueDroppedCounter.increment();
        } else if (result instanceof QueueOfferResult.Failure) {
            final QueueOfferResult.Failure failure = (QueueOfferResult.Failure) result;
            logger.error(failure.cause(), "Enqueue failed!");
            enqueueFailureCounter.increment();
        } else {
            logger.error(error, "Enqueue failed without acknowledgement!");
            enqueueFailureCounter.increment();
        }
        return null;
    }

    private void handleUnknownThrowable(final Throwable unknownThrowable) {
        logger.warning("Received unknown Throwable <{}>!", unknownThrowable);
        final GatewayInternalErrorException gatewayInternalError = GatewayInternalErrorException.newBuilder()
                .cause(unknownThrowable)
                .build();
        getSender().tell(gatewayInternalError, getSelf());
    }

}
