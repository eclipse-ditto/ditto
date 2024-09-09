/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.thingsearch.service.starter.actors;

import java.util.concurrent.CompletableFuture;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.PFBuilder;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.stream.Graph;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SourceShape;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.bson.Document;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetrics;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetricsResponse;
import org.eclipse.ditto.thingsearch.service.persistence.read.ThingsAggregationPersistence;

/**
 * Actor handling custom metrics aggregations {@link org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetrics}.
 */
public final class AggregateThingsMetricsActor extends AbstractActor {

    /**
     * The name of this actor in the system.
     */
    public static final String ACTOR_NAME = "aggregateThingsMetrics";
    /**
     * Name of the pekko cluster role.
     */
    public static final String CLUSTER_ROLE = "search";
    private static final String TRACING_THINGS_AGGREGATION = "aggregate_things_metrics";

    private final ThreadSafeDittoLoggingAdapter log;
    private final ThingsAggregationPersistence thingsAggregationPersistence;
    private final Materializer materializer;

    private AggregateThingsMetricsActor(final ThingsAggregationPersistence aggregationPersistence) {
        log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);
        thingsAggregationPersistence = aggregationPersistence;
        materializer = SystemMaterializer.get(getContext().getSystem()).materializer();
    }

    public static Props props(final ThingsAggregationPersistence aggregationPersistence) {
        return Props.create(AggregateThingsMetricsActor.class, aggregationPersistence);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(AggregateThingsMetrics.class, this::aggregate)
                .matchAny(any -> {
                    log.warning("Got unknown message '{}'", any);
                })
                .build();
    }

    private void aggregate(final AggregateThingsMetrics aggregateThingsMetrics) {
        log.debug("Received aggregate command for {}", aggregateThingsMetrics);
        final StartedTimer aggregationTimer = startNewTimer(aggregateThingsMetrics);
        final Source<Document, NotUsed> source =
                DittoJsonException.wrapJsonRuntimeException(aggregateThingsMetrics,
                        aggregateThingsMetrics.getDittoHeaders(),
                        (command, headers) -> thingsAggregationPersistence.aggregateThings(command));
        final Source<AggregateThingsMetricsResponse, NotUsed> aggregationResult =
                processAggregationPersistenceResult(source, aggregateThingsMetrics.getDittoHeaders())
                        .map(aggregation -> JsonFactory.newObject(aggregation.toJson()))
                        .map(aggregation -> AggregateThingsMetricsResponse.of(aggregation, aggregateThingsMetrics));
        final ActorRef sender = getSender(); // Save sender as it is not available after the first element is processed
        final Source<Object, ?> replySourceWithErrorHandling =
                aggregationResult.via(stopTimerAndHandleError(aggregationTimer, aggregateThingsMetrics));

        replySourceWithErrorHandling.runWith(Sink.foreach(
                        elem -> Patterns.pipe(CompletableFuture.completedFuture(elem), getContext().dispatcher()).to(sender)),
                materializer);
    }

    private <T> Source<T, NotUsed> processAggregationPersistenceResult(final Source<T, NotUsed> source,
            final DittoHeaders dittoHeaders) {

        final Flow<T, T, NotUsed> logAndFinishPersistenceSegmentFlow =
                Flow.fromFunction(result -> {
                    log.withCorrelationId(dittoHeaders)
                            .debug("aggregation element: {}", result);
                    return result;
                });
        return source.via(logAndFinishPersistenceSegmentFlow);
    }

    private static StartedTimer startNewTimer(final WithDittoHeaders withDittoHeaders) {
        final StartedTimer startedTimer = DittoMetrics.timer(TRACING_THINGS_AGGREGATION)
                .start();
        DittoTracing.newStartedSpanByTimer(withDittoHeaders.getDittoHeaders(), startedTimer);

        return startedTimer;
    }

    private static void stopTimer(final StartedTimer timer) {
        try {
            timer.stop();
        } catch (final IllegalStateException e) {
            // it is okay if the timer was stopped.
        }
    }

    private <T> Flow<T, Object, NotUsed> stopTimerAndHandleError(final StartedTimer searchTimer,
            final WithDittoHeaders command) {
        return Flow.<T, Object>fromFunction(
                        element -> {
                            stopTimer(searchTimer);
                            return element;
                        })
                .recoverWithRetries(1, new PFBuilder<Throwable, Graph<SourceShape<Object>, NotUsed>>()
                        .matchAny(error -> {
                            stopTimer(searchTimer);
                            return Source.single(asDittoRuntimeException(error, command));
                        })
                        .build()
                );
    }

    private DittoRuntimeException asDittoRuntimeException(final Throwable error, final WithDittoHeaders trigger) {
        return DittoRuntimeException.asDittoRuntimeException(error, t -> {
            log.error(error, "AggregateThingsMetricsActor failed to execute <{}>", trigger);

            return DittoInternalErrorException.newBuilder()
                    .dittoHeaders(trigger.getDittoHeaders())
                    .message(error.getClass() + ": " + error.getMessage())
                    .cause(t)
                    .build();
        });
    }
}
