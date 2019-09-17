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
package org.eclipse.ditto.services.concierge.actors.cleanup;

import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.utils.health.AbstractHealthCheckingActor;
import org.eclipse.ditto.services.utils.health.RetrieveHealth;
import org.eclipse.ditto.services.utils.health.RetrieveHealthResponse;
import org.eclipse.ditto.services.utils.health.StatusInfo;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor to report cleanup state on and only on the Concierge leader.
 */
public final class CleanupStatusReporter extends AbstractHealthCheckingActor {

    private static final StatusInfo NOT_MY_BUSINESS =
            StatusInfo.fromStatus(StatusInfo.Status.UP, "this is not the oldest member.");

    private final Cluster cluster = Cluster.get(getContext().getSystem());

    private final ActorRef cleanupCoordinatorProxy;

    private CleanupStatusReporter(final ActorRef cleanupCoordinatorProxy) {
        this.cleanupCoordinatorProxy = cleanupCoordinatorProxy;
    }

    /**
     * Create Props actor for this actor.
     *
     * @param cleanupCoordinatorProxy proxy of the cluster singleton cleanup coordinator.
     * @return Props to report status of the cleanup coordinator.
     */
    public static Props props(final ActorRef cleanupCoordinatorProxy) {
        return Props.create(CleanupStatusReporter.class, cleanupCoordinatorProxy);
    }

    @Override
    protected Receive matchCustomMessages() {
        return ReceiveBuilder.create()
                .match(RetrieveHealthResponse.class, response -> updateHealth(response.getStatusInfo()))
                .build();
    }

    @Override
    protected void triggerHealthRetrieval() {
        if (isThisConciergeLeader()) {
            cleanupCoordinatorProxy.tell(RetrieveHealth.newInstance(), getSelf());
        } else {
            updateHealth(NOT_MY_BUSINESS);
        }
    }

    private boolean isThisConciergeLeader() {
        return cluster.selfAddress().equals(cluster.state().getRoleLeader(ConciergeMessagingConstants.CLUSTER_ROLE));
    }
}
