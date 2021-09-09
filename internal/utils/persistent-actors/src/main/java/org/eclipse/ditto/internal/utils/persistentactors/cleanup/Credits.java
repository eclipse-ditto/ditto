/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors.cleanup;

import java.time.Duration;
import java.util.concurrent.atomic.LongAccumulator;

import org.eclipse.ditto.internal.utils.akka.controlflow.Transistor;
import org.eclipse.ditto.internal.utils.metrics.mongo.MongoMetricsBuilder;

import akka.NotUsed;
import akka.event.LoggingAdapter;
import akka.stream.SourceShape;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Source;

final class Credits {

    private final CleanupConfig cleanupConfig;
    private final LongAccumulator dbTimerNanos;

    Credits(final CleanupConfig cleanupConfig,
            final LongAccumulator dbTimerNanos) {
        this.cleanupConfig = cleanupConfig;
        this.dbTimerNanos = dbTimerNanos;
    }

    static Credits of(final CleanupConfig config) {
        return new Credits(config, MongoMetricsBuilder.maxTimerNanos());
    }

    /**
     * Regulate a source with this source of credits. 1 element is requested from the source per credit.
     *
     * @param unregulatedSource the source to regulate.
     * @param logger logger where credit decisions are logged.
     * @param <T> the type of elements.
     * @param <M> the type of the source's materialized value.
     * @return the regulated source.
     */
    @SuppressWarnings("unchecked")
    public <T, M> Source<T, M> regulate(final Source<T, M> unregulatedSource, final LoggingAdapter logger) {
        return Source.fromGraph(GraphDSL.create(unregulatedSource, (builder, source) -> {
            final var credits = builder.add(getCreditSource(logger));
            final var transistor = builder.add(Transistor.<T>of());
            builder.from(source).toInlet(transistor.in0());
            builder.from(credits).toInlet(transistor.in1());
            return SourceShape.of(transistor.out());
        }));
    }

    private Source<Integer, NotUsed> getCreditSource(final LoggingAdapter logger) {
        return Source.tick(Duration.ZERO, cleanupConfig.getInterval(), Tick.TICK)
                .mapMaterializedValue(cancellable -> NotUsed.getInstance())
                .flatMapConcat(tick -> computeCredit(logger));
    }

    private Source<Integer, NotUsed> computeCredit(final LoggingAdapter logger) {
        try {
            final Duration maxDuration = Duration.ofNanos(dbTimerNanos.getThenReset());
            final Duration threshold = cleanupConfig.getTimerThreshold();
            if (maxDuration.minus(threshold).isNegative()) {
                final var credits = cleanupConfig.getCreditsPerBatch();
                logger.debug("Credits={} Timer={}/{}", credits, maxDuration, threshold);
                return Source.single(credits);
            } else {
                logger.debug("Credits={} Timer={}/{}", 0, maxDuration, threshold);
                return Source.empty();
            }
        } catch (final Exception e) {
            logger.error(e, "Failed to calculate credit");
            return Source.empty();
        }
    }

    private enum Tick {
        TICK
    }
}
