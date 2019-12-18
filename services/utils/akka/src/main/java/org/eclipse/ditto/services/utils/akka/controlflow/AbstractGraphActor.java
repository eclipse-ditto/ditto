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

import java.util.Optional;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.function.Function;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.OverflowStrategy;
import akka.stream.QueueOfferResult;
import akka.stream.Supervision;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Partition;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;

/**
 * Actor whose behavior is defined entirely by an Akka stream graph.
 *
 * @param <T> the type of the messages this actor processes in the stream graph.
 * @param <M> the type of the incoming messages which is translated to a message of type {@code <T>} in
 *  {@link #mapMessage(Object)}.
 */
public abstract class AbstractGraphActor<T, M> extends AbstractActor {

    /**
     * For {@code signals} marked with a DittoHeader with that key,  the "special enforcement lane" shall be used -
     * meaning that those messages are processed not based on the hash of their ID but in a common "special lane".
     * <p>
     * Be aware that when using this all those signals will be effectively sequentially processed but they could
     * be processed in parallel to other signals whose IDs have the same hash partition in {@link AbstractGraphActor}.
     * </p>
     */
    public static final String DITTO_INTERNAL_SPECIAL_ENFORCEMENT_LANE = "ditto-internal-special-enforcement-lane";

    private static final String CLASS = "class";

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final Counter receiveCounter = DittoMetrics.counter("graph_actor_receive")
            .tag(CLASS, getClass().getSimpleName());

    private final Counter enqueueSuccessCounter = DittoMetrics.counter("graph_actor_enqueue_success")
            .tag(CLASS, getClass().getSimpleName());

    private final Counter enqueueDroppedCounter = DittoMetrics.counter("graph_actor_enqueue_dropped")
            .tag(CLASS, getClass().getSimpleName());

    private final Counter enqueueFailureCounter = DittoMetrics.counter("graph_actor_enqueue_failure")
            .tag(CLASS, getClass().getSimpleName());

    private final Counter dequeueCounter = DittoMetrics.counter("graph_actor_dequeue")
            .tag(CLASS, getClass().getSimpleName());


    private final Class<M> matchClass;

    protected AbstractGraphActor(final Class<M> matchClass) {
        this.matchClass = matchClass;
    }

    /**
     * Provides a {@code T} by passing each single {@code message}s this Actor received.
     *
     * @param message the currently processed message of this Actor.
     * @return the created Source.
     */
    protected abstract T mapMessage(M message);

    /**
     * Called before handling the actual message via the {@link #processMessageFlow()} in order to being able to enhance the
     * message.
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

        final String graphActorClassName = getClass().getSimpleName();
        final ActorSystem actorSystem = getContext().getSystem();
        final ActorMaterializerSettings materializerSettings = ActorMaterializerSettings.create(actorSystem)
                .withSupervisionStrategy((Function<Throwable, Supervision.Directive>) exc -> {
                            if (exc instanceof DittoRuntimeException) {
                                LogUtil.enhanceLogWithCorrelationId(log, (DittoRuntimeException) exc);
                                log.warning("DittoRuntimeException in stream of {}: [{}] {}",
                                        graphActorClassName, exc.getClass().getSimpleName(), exc.getMessage());
                            } else {
                                log.error(exc, "Exception in stream of {}: {}",
                                        graphActorClassName, exc.getMessage());
                            }
                            return Supervision.resume(); // in any case, resume!
                        }
                );
        final ActorMaterializer materializer = ActorMaterializer.create(materializerSettings, getContext());

        // log stream completion and failure at level ERROR because the stream is supposed to survive forever.
        final Attributes streamLogLevels =
                Attributes.logLevels(Attributes.logLevelDebug(), Attributes.logLevelError(),
                        Attributes.logLevelError());

        final SourceQueueWithComplete<T> sourceQueue = Source.<T>queue(getBufferSize(), OverflowStrategy.dropNew())
                .map(this::incrementDequeueCounter)
                .log("graph-actor-stream-1-dequeued", log)
                .withAttributes(streamLogLevels)
                .via(Flow.fromFunction(this::beforeProcessMessage))
                .log("graph-actor-stream-2-preprocessed", log)
                .withAttributes(streamLogLevels)
                // partition by the message's ID in order to maintain order per ID
                .via(partitionById(processMessageFlow(), getParallelism()))
                .log("graph-actor-stream-3-partitioned", log)
                .withAttributes(streamLogLevels)
                .to(processedMessageSink())
                .run(materializer);

        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create();
        preEnhancement(receiveBuilder);
        return receiveBuilder
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(matchClass, match -> {
                    if (match instanceof WithDittoHeaders) {
                        LogUtil.enhanceLogWithCorrelationId(log, (WithDittoHeaders<?>) match);
                    }
                    if (match instanceof WithId) {
                        log.debug("Received <{}> with id <{}>", match.getClass().getSimpleName(),
                                ((WithId) match).getEntityId());
                    } else {
                        log.debug("Received match: <{}>", match);
                    }
                    incrementReceiveCounter();
                    sourceQueue.offer(mapMessage(match)).handle(this::incrementEnqueueCounters);
                })
                .match(Throwable.class, unknownThrowable -> {
                    log.warning("Received unknown Throwable: <{}>", unknownThrowable);
                    final GatewayInternalErrorException gatewayInternalError =
                            GatewayInternalErrorException.newBuilder()
                                    .cause(unknownThrowable)
                                    .build();
                    getSender().tell(gatewayInternalError, getSelf());
                })
                .matchAny(message -> log.warning("Received unknown message: <{}>", message))
                .build();
    }

    private void incrementReceiveCounter() {
        receiveCounter.increment();
    }

    private Void incrementEnqueueCounters(final QueueOfferResult result, final Throwable error) {
        if  (QueueOfferResult.enqueued().equals(result)) {
            enqueueSuccessCounter.increment();
        } else if (QueueOfferResult.dropped().equals(result)) {
            enqueueDroppedCounter.increment();
        } else if (result instanceof QueueOfferResult.Failure) {
            final QueueOfferResult.Failure failure = (QueueOfferResult.Failure) result;
            log.error(failure.cause(), "enqueue failed");
            enqueueFailureCounter.increment();
        } else {
            log.error(error, "enqueue failed without acknowledgement");
            enqueueFailureCounter.increment();
        }
        return null;
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
    private Flow<T, T, NotUsed> partitionById(final Flow<T, T, NotUsed> flowToPartition,
            final int parallelism) {

        final int parallelismWithSpecialLane = parallelism + 1;

        return Flow.fromGraph(GraphDSL.create(
                Partition.<T>create(parallelismWithSpecialLane, msg -> {
                    if (checkForSpecialLane(msg)) {
                        return 0; // 0 is a special "lane" which is required in some special cases
                    } else if (msg instanceof WithId) {
                        final EntityId id = ((WithId) msg).getEntityId();
                        if (id.isDummy()) {
                            // e.g. the case for RetrieveThings command - in that case it is important that not all
                            // RetrieveThings message are processed in the same "lane", so use msg hash instead:
                            return (msg.hashCode() % parallelism) + 1;
                        } else {
                            return Math.abs(id.hashCode() % parallelism) + 1;
                        }
                    } else {
                        return 0;
                    }
                }),
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
     * Checks whether a special lane is required for the passed {@code msg}. This is for example required when during
     * an enforcement another call to the enforcer is done, the hash of the 2 messages might collide and block
     * each other.
     *
     * @param msg the message to check for whether to use the special lane.
     * @return whether to use the special lane or not.
     */
    protected boolean checkForSpecialLane(final T msg) {
        return msg instanceof WithDittoHeaders && Optional.ofNullable(((WithDittoHeaders<?>) msg).getDittoHeaders()
                .get(DITTO_INTERNAL_SPECIAL_ENFORCEMENT_LANE))
                .isPresent();
    }

    /**
     * Provides the possibility to add custom matchers before applying the default matchers of the AbstractGraphActor.
     *
     * @param receiveBuilder the ReceiveBuilder to add other matchers to.
     */
    protected abstract void preEnhancement(final ReceiveBuilder receiveBuilder);

    /**
     * Handles DittoRuntimeExceptions by sending them back to the {@link #getSender() sender}.
     * Overwrite to introduce a custom exception handling.
     *
     * @param dittoRuntimeException the DittoRuntimeException to handle.
     */
    protected void handleDittoRuntimeException(final DittoRuntimeException dittoRuntimeException) {
        log.debug("Received DittoRuntimeException: <{}>", dittoRuntimeException);
        getSender().tell(dittoRuntimeException, getSelf());
    }

}
