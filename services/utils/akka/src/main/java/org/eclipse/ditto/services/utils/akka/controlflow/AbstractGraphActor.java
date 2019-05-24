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

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;
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
import akka.stream.FlowShape;
import akka.stream.OverflowStrategy;
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
 */
public abstract class AbstractGraphActor<T> extends AbstractActor {

    /**
     * Header field for marking that a wrapped Signal must be processed in a special enforcement lane.
     */
    public static final String DITTO_INTERNAL_SPECIAL_ENFORCEMENT_LANE = "ditto-internal-special-enforcement-lane";

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final int bufferSize;
    private final int parallelism;

    /**
     * @param bufferSize the buffer size used for the Source queue. After this limit is reached, the mailbox of this
     * actor will rise and buffer messages as part of the backpressure strategy.
     * @param parallelism parallelism to use for processing messages in parallel. When configured too low, throughput of
     * messages which perform blocking operations will be bad.
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
    protected abstract T mapMessage(WithDittoHeaders message);

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
                        // first: create substreams by namespace of the messages
                        .groupBy(100_000, msg -> { // TODO TJ configure max. amount of concurrent namespaces
                            if (msg instanceof WithId) {
                                final String id = ((WithId) msg).getId();
                                final int firstColon = id.indexOf(':');
                                if (firstColon >= 0) {
                                    return id.substring(0, firstColon);
                                }
                            }
                            return "";
                        })
                        .via(Flow.fromFunction(this::beforeProcessMessage))
                        // second: partition by the message's ID in order to maintain order per ID
                        .via(partitionById(processMessageFlow(), parallelism))
                        .to(processedMessageSink())
                        .run(materializer);

        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create();
        preEnhancement(receiveBuilder);
        return receiveBuilder
                .match(DittoRuntimeException.class, dittoRuntimeException -> {
                    log.debug("Received DittoRuntimeException: <{}>", dittoRuntimeException);
                    getSender().tell(dittoRuntimeException, getSelf());
                })
                .match(WithDittoHeaders.class, withDittoHeaders -> {
                    LogUtil.enhanceLogWithCorrelationId(log, withDittoHeaders);
                    if (withDittoHeaders instanceof WithId) {
                        log.debug("Received <{}> with id <{}>", withDittoHeaders.getClass().getSimpleName(),
                                ((WithId) withDittoHeaders).getId());
                    } else {
                        log.debug("Received WithDittoHeaders: <{}>", withDittoHeaders);
                    }
                    sourceQueue.offer(mapMessage(withDittoHeaders))
                            .toCompletableFuture()
                            .join(); // blocks the Actor from processing new messages
                    // used intentionally in combination with OverflowStrategy.backpressure()
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

    /**
     * Partitions the passed in {@code flowToPartition} based on the flowing through IDs of {@link WithId} messages.
     * That means that e.g. each Thing-ID gets its own partition, so messages to that Thing are sequentially processed
     * and thus the order is maintained.
     *
     * @param flowToPartition the Flow to apply the partitioning on.
     * @param parallelism the parallelism to use (how many partitions to process in parallel) - which should be based
     * on the amount of available CPUs.
     * @param <T> the type of the messages flowing through the stream
     * @return the partitioning flow
     */
    private static <T> Flow<T, T, NotUsed> partitionById(final Flow<T, T, NotUsed> flowToPartition,
            final int parallelism) {

        final int parallelismWithSpecialLane = parallelism + 1;

        return Flow.fromGraph(GraphDSL.create(
                Partition.<T>create(parallelismWithSpecialLane, msg -> {
                    if (checkForSpecialLane(msg))
                        return 0; // 0 is a special "lane" which is required in some special cases
                    if (msg instanceof WithId) {
                        return Math.abs(((WithId) msg).getId().hashCode() % parallelism) + 1;
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
     * @param <T> the type of the message
     * @return whether to use the special lane or not.
     */
    private static <T> boolean checkForSpecialLane(final T msg) {
        return msg instanceof WithDittoHeaders && Optional.ofNullable(((WithDittoHeaders) msg).getDittoHeaders()
                        .get(DITTO_INTERNAL_SPECIAL_ENFORCEMENT_LANE))
                        .isPresent();
    }

    /**
     * Provides the possibility to add custom matchers before applying the default matchers of the AbstractGraphActor.
     *
     * @param receiveBuilder the ReceiveBuilder to add other matchers to.
     */
    protected abstract void preEnhancement(final ReceiveBuilder receiveBuilder);

}
