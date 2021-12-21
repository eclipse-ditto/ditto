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
package org.eclipse.ditto.concierge.api.actors;

import java.util.List;
import java.util.Set;

import org.eclipse.ditto.concierge.api.ConciergeMessagingConstants;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.cluster.routing.ClusterRouterGroup;
import akka.cluster.routing.ClusterRouterGroupSettings;
import akka.routing.ConsistentHashingGroup;

/**
 * Factory for creating a cluster routing actor delegating to concierge's {@code EnforcerActor}.
 * <p>
 * All messages sent to the created {@link ClusterRouterGroup} must be wrapped in a
 * {@link akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope} which contains the hashed routing key.
 * </p>
 */
public final class ConciergeEnforcerClusterRouterFactory {

    private static final String CONCIERGE_SERVICE_ENFORCER_PATH = ConciergeMessagingConstants.ENFORCER_ACTOR_PATH;
    private static final String CONCIERGE_ENFORCER_ROUTER_ACTOR_NAME = "conciergeEnforcerRouter";

    private ConciergeEnforcerClusterRouterFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a new {@link ClusterRouterGroup} for the {@code EnforcerActor} at path
     * {@value #CONCIERGE_SERVICE_ENFORCER_PATH} for cluster role of
     * {@value org.eclipse.ditto.concierge.api.ConciergeMessagingConstants#CLUSTER_ROLE}.
     *
     * @param context the ActorContext in which to create the cluster router (e.g. a RootActor).
     * @param numberOfRoutees the number of routees to which to "dispatch" messages based on the hashing of the sent
     * messages.
     * @return the ActorRef of the created cluster router
     */
    public static ActorRef createConciergeEnforcerClusterRouter(final ActorContext context, final int numberOfRoutees) {
        final var routeesPaths = List.of(CONCIERGE_SERVICE_ENFORCER_PATH);
        final var clusterRouterGroup = new ClusterRouterGroup(
                new ConsistentHashingGroup(routeesPaths),
                new ClusterRouterGroupSettings(numberOfRoutees,
                        routeesPaths,
                        true,
                        Set.of(ConciergeMessagingConstants.CLUSTER_ROLE))
        );

        return context.actorOf(clusterRouterGroup.props(), CONCIERGE_ENFORCER_ROUTER_ACTOR_NAME);
    }

}
