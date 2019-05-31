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
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.concierge.actors.cleanup.messages.CreditDecision;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.health.RetrieveHealth;
import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoMetricsReporter;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.Pair;
import akka.pattern.Patterns;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

 /**
 * Retrieve persistence metrics when given the number of instances that report them.
 * <ul>
 * <li>
 * Deliver persistence metrics in outlet 0 if all instances report before timeout.
 * </li>
 * <li>
 * Deliver credit rejection in outlet 1 if some instances fail to report before timeout.
 * </li>
 * </ul>
 */
final class PersistenceStatusStage {

    static Graph<FanOutShape2<Integer, List<StatusInfo>, CreditDecision>, NotUsed> create(
            final ActorRef pubSubMediator, final ActorRefFactory actorRefFactory, final Duration timeout) {

        return GraphDSL.create(builder -> {

            final FlowShape<Integer, Pair<Integer, Object>> askAggregator =
                    builder.add(askAggregatorFlow(pubSubMediator, actorRefFactory, timeout));

            final FanOutShape2<Pair<Integer, Object>, List<StatusInfo>, CreditDecision> decision =
                    builder.add(Filter.multiplexByEither(PersistenceStatusStage::checkAggregatorReply));

            builder.from(askAggregator.out()).toInlet(decision.in());

            return new FanOutShape2<>(askAggregator.in(), decision.out0(), decision.out1());
        });
    }

    private static CompletionStage<Object> askAggregator(final int expectedMessages,
            final ActorRef pubSubMediator, final ActorRefFactory actorRefFactory, final Duration timeout) {

        final Props aggregatorProps =
                MessageAggregator.props(pubSubMediator, StatusInfo.class, expectedMessages, timeout);

        final Object publish =
                new DistributedPubSubMediator.Publish(MongoMetricsReporter.PUBSUB_TOPIC, RetrieveHealth.newInstance());

        final ActorRef aggregator = actorRefFactory.actorOf(aggregatorProps);

        // set ask-timeout to be twice as long to ensure message is received from aggregator
        final Duration askTimeout = timeout.multipliedBy(2L);

        return Patterns.ask(aggregator, publish, askTimeout);
    }

    private static Flow<Integer, Pair<Integer, Object>, NotUsed> askAggregatorFlow(final ActorRef pubSubMediator,
            final ActorRefFactory actorRefFactory, final Duration timeout) {

        return Flow.<Integer>create()
                .mapAsync(1, expectedMessages ->
                        askAggregator(expectedMessages, pubSubMediator, actorRefFactory, timeout)
                                .thenApply(reply -> new Pair<>(expectedMessages, reply)));
    }

    private static Either<CreditDecision, List<StatusInfo>> checkAggregatorReply(
            final Pair<Integer, Object> aggregatedReplies) {

        final int expectedAnswers = aggregatedReplies.first();
        final Object reply = aggregatedReplies.second();
        final Optional<List<StatusInfo>> statusInfosOptional = safeCastAsStatusInfoList(reply);
        if (statusInfosOptional.isPresent()) {
            final List<StatusInfo> statusInfos = statusInfosOptional.get();
            if (statusInfos.size() == expectedAnswers) {
                return new Right<>(statusInfos);
            } else {
                final String explanation =
                        String.format("Expect %d StatusInfo replies, got %d: <%s>", expectedAnswers, statusInfos.size(),
                                statusInfos.toString());
                return new Left<>(CreditDecision.no(explanation));
            }
        } else {
            final String explanation =
                    String.format("Expect a list of StatusInfo, got %s: <%s>", reply.getClass(), reply.toString());
            return new Left<>(CreditDecision.no(explanation));
        }
    }

    private static Optional<List<StatusInfo>> safeCastAsStatusInfoList(final Object object) {
        if (object instanceof List) {
            final List<?> list = (List) object;
            if (list.stream().allMatch(x -> x instanceof StatusInfo)) {
                return Optional.of(list.stream()
                        .map(x -> (StatusInfo) x)
                        .collect(Collectors.toList()));
            }
        }
        return Optional.empty();
    }

}
