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

import java.util.List;

import org.eclipse.ditto.services.concierge.actors.cleanup.messages.CreditDecision;
import org.eclipse.ditto.services.concierge.common.CreditDecisionConfig;
import org.eclipse.ditto.services.utils.health.StatusInfo;

import akka.NotUsed;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.event.LoggingAdapter;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.SourceShape;
import akka.stream.UniformFanInShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Merge;
import akka.stream.javadsl.Source;

/**
 * Decide how many cleanup actions to permit based on cluster status and persistence metrics.
 *
 * <pre>{@code
 *                             Start
 *                               +
 *                               |
 *                               |
 *                               v
 * +-----------------------------+---------------------------+  Yes
 * |Are there unreachable members?                           +------> No credit: cluster unhealthy
 * +-----------------------------+---------------------------+
 *                               |
 *                               |No
 *                               v
 * +-----------------------------+---------------------------+  No
 * |Do cluster roles <things, policies, connectivity> exist? +------> No credit: missing roles
 * +-----------------------------+---------------------------+
 *                               |
 *                               |Yes
 *                               v
 * +-----------------------------+---------------------------+  No
 * |Do all members of <things, policies, connectivity> report+------> No credit: no metric reporting
 * |MongoDB metrics?                                         |
 * +-----------------------------+---------------------------+
 *                               |
 *                               |Yes
 *                               v
 *                    Give out DeleteOldEvent credit
 *                    based on MongoDB metrics
 * }</pre>
 */
public final class CreditDecisionSource {

    // TODO: refactor into settings class
    // TODO: document
    public static Graph<SourceShape<CreditDecision>, NotUsed> create(
            final CreditDecisionConfig config,
            final ActorContext context,
            final ActorRef pubSubMediator,
            final LoggingAdapter log) {

        final Source<Tick, NotUsed> tickSource =
                Source.tick(config.getInterval(), config.getInterval(), new Tick())
                        .mapMaterializedValue(whatever -> NotUsed.getInstance());

        // TODO: better to give ClusterStatusSupplier instead of ActorSystem?
        final Graph<FanOutShape2<Tick, Integer, CreditDecision>, NotUsed> clusterStatusStage =
                ClusterStatusStage.create(context.system());

        final Graph<FanOutShape2<Integer, List<StatusInfo>, CreditDecision>, NotUsed> persistenceStatusStage =
                PersistenceStatusStage.create(pubSubMediator, context, config.getMetricReportTimeout());

        final Graph<FlowShape<List<StatusInfo>, CreditDecision>, NotUsed> decisionByMetricStage =
                DecisionByMetricStage.create(config.getTimerThreshold(), config.getCreditPerBatch());

        return GraphDSL.create(builder -> {
            final SourceShape<Tick> tick = builder.add(tickSource);

            final FanOutShape2<Tick, Integer, CreditDecision> clusterStatus = builder.add(clusterStatusStage);

            final FanOutShape2<Integer, List<StatusInfo>, CreditDecision> persistenceStatus =
                    builder.add(persistenceStatusStage);

            final FlowShape<List<StatusInfo>, CreditDecision> decisionByMetric = builder.add(decisionByMetricStage);

            final UniformFanInShape<CreditDecision, CreditDecision> merge = builder.add(Merge.create(3, true));

            final Logger logger = new Logger(builder, log);

            final FlowShape<CreditDecision, CreditDecision> logDecision = logger.log("creditDecision");

            builder.from(tick.out()).toInlet(clusterStatus.in());
            builder.from(clusterStatus.out0()).via(logger.log("persistInstances")).toInlet(persistenceStatus.in());
            builder.from(clusterStatus.out1()).toInlet(merge.in(0));
            builder.from(persistenceStatus.out0()).via(logger.log("persistMetrics")).toInlet(decisionByMetric.in());
            builder.from(persistenceStatus.out1()).toInlet(merge.in(1));
            builder.from(decisionByMetric.out()).toInlet(merge.in(2));
            builder.from(merge.out()).toInlet(logDecision.in());

            return SourceShape.of(logDecision.out());
        });
    }

    private static final class Tick {}

    private static final class Logger {

        final GraphDSL.Builder<NotUsed> builder;
        final LoggingAdapter loggingAdapter;

        private Logger(final GraphDSL.Builder<NotUsed> builder, final LoggingAdapter loggingAdapter) {
            this.builder = builder;
            this.loggingAdapter = loggingAdapter;
        }

        private <T> FlowShape<T, T> log(final String name) {
            return builder.add(Flow.<T>create().log(name, loggingAdapter));
        }
    }
}
