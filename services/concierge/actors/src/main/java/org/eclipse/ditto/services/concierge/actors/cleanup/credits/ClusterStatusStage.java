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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.concierge.actors.cleanup.messages.CreditDecision;
import org.eclipse.ditto.services.utils.akka.controlflow.Filter;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.stream.FanOutShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * Retrieve cluster status when prompted by a tick.
 * <ul>
 * <li>
 * Deliver the number of instances in the expected roles ({@code connectivity}, {@code policies}, {@code things}) in
 * outlet 0 if the cluster has no unreachable members and all expected roles are present.
 * </li>
 * <li>
 * Deliver rejection of credit in outlet 1 if the cluster has unreachable members or some expected roles are absent.
 * </li>
 * </ul>
 */
final class ClusterStatusStage {

    private static final List<String> EXPECTED_ROLES = Arrays.asList("connectivity", "policies", "things");

    static <T> Graph<FanOutShape2<T, Integer, CreditDecision>, NotUsed> create(
            final ActorSystem actorSystem) {
        return create(new ClusterStatusSupplier(Cluster.get(actorSystem)));
    }

    static <T> Graph<FanOutShape2<T, Integer, CreditDecision>, NotUsed> create(
            final ClusterStatusSupplier clusterStatusSupplier) {

        return GraphDSL.create(builder -> {
            final FlowShape<T, ClusterStatus> getStatus =
                    builder.add(Flow.fromFunction(tick -> clusterStatusSupplier.get()));
            final FanOutShape2<ClusterStatus, Integer, CreditDecision> fanout =
                    builder.add(Filter.multiplexByEither(ClusterStatusStage::getPersistenceInstances));
            builder.from(getStatus.out()).toInlet(fanout.in());
            return new FanOutShape2(getStatus.in(), fanout.out0(), fanout.out1());
        });
    }

    private static Either<CreditDecision, Integer> getPersistenceInstances(final ClusterStatus clusterStatus) {
        if (areAllMembersReachable(clusterStatus)) {
            return countMembersOfExpectedRoles(clusterStatus);
        } else {
            // not all cluster members are reachable
            return new Left<>(CreditDecision.no("Cluster has unreachable members: " + clusterStatus));
        }
    }

    private static boolean areAllMembersReachable(final ClusterStatus clusterStatus) {
        return clusterStatus.getUnreachable().isEmpty();
    }

    private static Either<CreditDecision, Integer> countMembersOfExpectedRoles(final ClusterStatus clusterStatus) {
        final List<Integer> reachableMembersOfExpectedRoles = clusterStatus.getRoles()
                .stream()
                .filter(role -> EXPECTED_ROLES.contains(role.getRole()))
                .map(role -> role.getReachable().size())
                .collect(Collectors.toList());

        if (reachableMembersOfExpectedRoles.size() == EXPECTED_ROLES.size()) {
            return new Right<>(reachableMembersOfExpectedRoles.stream().mapToInt(Integer::intValue).sum());
        } else {
            // some expected roles are not reachable; do not return members count.
            return new Left<>(
                    CreditDecision.no("Not all expected roles " + EXPECTED_ROLES + " are present: " + clusterStatus));
        }
    }
}
