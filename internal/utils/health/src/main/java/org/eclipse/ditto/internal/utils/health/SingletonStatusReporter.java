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
package org.eclipse.ditto.internal.utils.health;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor to report health status for a singleton on and only on the role leader of a cluster.
 */
public final class SingletonStatusReporter extends AbstractHealthCheckingActor {

    private static final StatusInfo NOT_MY_BUSINESS =
            StatusInfo.fromStatus(StatusInfo.Status.UP, "this is not the oldest member.");

    private final Cluster cluster = Cluster.get(getContext().getSystem());

    private final String clusterRole;
    private final ActorRef healthReportingSingletonProxy;

    @SuppressWarnings("unused")
    private SingletonStatusReporter(final String clusterRole, final ActorRef healthReportingSingletonProxy) {
        this.clusterRole = clusterRole;
        this.healthReportingSingletonProxy = healthReportingSingletonProxy;
    }

    /**
     * Create Props actor for this actor.
     *
     * @param clusterRole cluster role where the singleton starts.
     * @param cleanupCoordinatorProxy proxy of the cluster singleton cleanup coordinator.
     * @return Props to report status of the cleanup coordinator.
     */
    public static Props props(final String clusterRole, final ActorRef cleanupCoordinatorProxy) {
        return Props.create(SingletonStatusReporter.class, clusterRole, cleanupCoordinatorProxy);
    }

    @Override
    protected Receive matchCustomMessages() {
        return ReceiveBuilder.create()
                .match(RetrieveHealthResponse.class, response -> updateHealth(response.getStatusInfo()))
                .build();
    }

    @Override
    protected void triggerHealthRetrieval() {
        if (isThisClusterRoleLeader()) {
            healthReportingSingletonProxy.tell(RetrieveHealth.newInstance(), getSelf());
        } else {
            updateHealth(NOT_MY_BUSINESS);
        }
    }

    private boolean isThisClusterRoleLeader() {
        return cluster.selfAddress().equals(cluster.state().getRoleLeader(clusterRole));
    }
}
