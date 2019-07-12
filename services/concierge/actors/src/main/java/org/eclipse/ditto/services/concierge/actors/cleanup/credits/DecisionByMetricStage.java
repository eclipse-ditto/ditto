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
package org.eclipse.ditto.services.concierge.actors.cleanup.credits;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.services.concierge.actors.cleanup.messages.CreditDecision;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoMetrics;

import akka.NotUsed;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.UniformFanInShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * Decide how many cleanup actions to permit based on persistence metrics.
 * <ul>
 * <li>
 * Give out a set amount of credits if all status info messages are well-formed and all timers are below the configured
 * threshold.
 * </li>
 * <li>
 * Deliver credit rejection if some status info messages are ill-formed or some timers are above the configured
 * threshold.
 * </li>
 * </ul>
 */
final class DecisionByMetricStage {

    private DecisionByMetricStage() {
        throw new AssertionError();
    }

    /**
     * Creates the decision by metric stage - whenever a message (as a list of {@link StatusInfo}) triggers the stage,
     * <ul>
     * <li>the max time is determined from the {@code maxTimerNanos} from {@link StatusInfo} is determined</li>
     * <li>based in that max time a {@link CreditDecision} is made based on the passed {@code timerThreshold}</li>
     * <li>if the extracted max time from {@link StatusInfo} is lesser than the passed in {@code timerThreshold},
     * a positive {@link CreditDecision} is emitted to the outlet with the passed in {@code creditPerBatch}</li>
     * <li>if the extracted max time from {@link StatusInfo} is greater than the passed {@code timerThreshold},
     * a negative {@link CreditDecision} is emitted to the outlet</li>
     * </ul>
     *
     * @param timerThreshold the duration defining the threshold below which a positive {@link CreditDecision} is
     * emitted by this stage.
     * @param creditPerBatch the batch credit which will be included in a positive {@link CreditDecision}.
     * @return the created decision by metric stage.
     */
    static Graph<FlowShape<List<StatusInfo>, CreditDecision>, NotUsed> create(final Duration timerThreshold,
            final int creditPerBatch) {

        return GraphDSL.create(builder -> {
            final FanOutShape2<List<StatusInfo>, Long, CreditDecision> fanout =
                    builder.add(Filter.multiplexByEither(DecisionByMetricStage::getMaxTimerNanos));

            final FlowShape<Long, CreditDecision> decision =
                    builder.add(Flow.fromFunction(nanos -> decide(nanos, timerThreshold.toNanos(), creditPerBatch)));

            final UniformFanInShape<CreditDecision, CreditDecision> merge =
                    builder.add(Merge.create(2, true));

            builder.from(fanout.out0()).toInlet(decision.in());
            builder.from(fanout.out1()).toInlet(merge.in(0));
            builder.from(decision.out()).toInlet(merge.in(1));

            return FlowShape.of(fanout.in(), merge.out());
        });
    }

    private static CreditDecision decide(final long maxTimerNanos, final long timerThreshold,
            final int creditPerBatch) {
        if (maxTimerNanos <= timerThreshold) {
            return CreditDecision.yes(creditPerBatch,
                    "maxTimeNanos=" + maxTimerNanos + " is below threshold=" + timerThreshold);
        } else {
            return CreditDecision.no("maxTimerNanos=" + maxTimerNanos + " is above threshold=" + timerThreshold);
        }
    }

    private static Either<CreditDecision, Long> getMaxTimerNanos(final List<StatusInfo> statusInfos) {
        long maxTimerNanos = 0L;
        for (final StatusInfo statusInfo : statusInfos) {
            final Either<CreditDecision, Long> statusTimerValue = getMaxTimerNanosFromStatusInfo(statusInfo);
            if (statusTimerValue.isLeft()) {
                return statusTimerValue;
            } else {
                maxTimerNanos = Math.max(maxTimerNanos, statusTimerValue.right().get());
            }
        }
        return new Right<>(maxTimerNanos);
    }

    private static Either<CreditDecision, Long> getMaxTimerNanosFromStatusInfo(final StatusInfo statusInfo) {
        if (statusInfo.getDetails().size() != 1) {
            return left("StatusInfo has non-singleton detail: " + statusInfo);
        }
        final JsonValue detail = statusInfo.getDetails().get(0).getMessage();
        if (!detail.isObject()) {
            return left("StatusDetailMessage is not an object: " + statusInfo);
        }

        final List<Long> maxTimerNanos = MongoMetrics.fromJson(detail.asObject()).getMaxTimerNanos();

        // extract max long value
        final Optional<Long> max = maxTimerNanos.stream().max(Long::compareTo);
        if (!max.isPresent()) {
            return left("Field maxTimerNanos not found or empty in status detail: " + statusInfo);
        }
        return new Right<>(max.get());
    }

    private static Either<CreditDecision, Long> left(final String explanation) {
        return new Left<>(CreditDecision.no(explanation));
    }
}
