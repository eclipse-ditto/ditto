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
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.metrics.mongo.MongoMetricsBuilder;

import akka.NotUsed;
import akka.stream.Attributes;
import akka.stream.SourceShape;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Source;

final class Credits {

    private final ThreadSafeDittoLogger logger;
    private final CreditDecisionConfig creditDecisionConfig;
    private final LongAccumulator dbTimerNanos;

    Credits(final CreditDecisionConfig creditDecisionConfig,
            final LongAccumulator dbTimerNanos) {
        logger = DittoLoggerFactory.getThreadSafeLogger(getClass());
        this.creditDecisionConfig = creditDecisionConfig;
        this.dbTimerNanos = dbTimerNanos;
    }

    /**
     * Regulate a source with this source of credits. 1 element is requested from the source per credit.
     *
     * @param unregulatedSource the source to regulate.
     * @param <T> the type of elements.
     * @param <M> the type of the source's materialized value.
     * @return the regulated source.
     */
    @SuppressWarnings("unchecked")
    public <T, M> Source<T, M> regulate(final Source<T, M> unregulatedSource) {
        return Source.fromGraph(GraphDSL.create(unregulatedSource, (builder, source) -> {
            final var credits = builder.add(getCreditSource());
            final var transistor = builder.add(Transistor.<T>of());
            builder.from(source).toInlet(transistor.in0());
            builder.from(credits).toInlet(transistor.in1());
            return SourceShape.of(transistor.out());
        }));
    }

    private Source<Integer, NotUsed> getCreditSource() {
        return Source.tick(Duration.ZERO, creditDecisionConfig.getInterval(), Tick.TICK)
                .mapMaterializedValue(cancellable -> NotUsed.getInstance())
                .flatMapConcat(this::computeCredit);
    }

    private Source<Integer, NotUsed> computeCredit(final Tick tick) {
        try {
            final Duration maxDuration = Duration.ofNanos(dbTimerNanos.getThenReset());
            return maxDuration.minus(creditDecisionConfig.getTimerThreshold()).isNegative()
                    ? Source.single(creditDecisionConfig.getCreditPerBatch())
                    : Source.empty();
        } catch (final Exception e) {
            logger.error("Failed to calculate credit", e);
            return Source.empty();
        }
    }

    private enum Tick {
        TICK
    }
}
