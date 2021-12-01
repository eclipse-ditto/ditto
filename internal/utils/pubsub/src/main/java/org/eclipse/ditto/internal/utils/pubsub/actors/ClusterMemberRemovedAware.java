/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.actors;

import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataWriter;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ddata.Replicator;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Mixin to subscribe for cluster events.
 */
interface ClusterMemberRemovedAware extends Actor {

    /**
     * Return the write-local write consistency.
     *
     * @return the write consistency.
     */
    static Replicator.WriteConsistency writeLocal() {
        return (Replicator.WriteConsistency) Replicator.writeLocal();
    }

    /**
     * @return the logging adapter of this actor.
     */
    LoggingAdapter log();

    /**
     * @return the distributed data writer of this actor.
     */
    DDataWriter<?, ?> getDDataWriter();

    default AbstractActor.Receive receiveClusterMemberRemoved() {
        return ReceiveBuilder.create()
                .match(ClusterEvent.MemberRemoved.class, this::memberRemoved)
                .match(ClusterEvent.CurrentClusterState.class, this::logCurrentClusterState)
                .build();
    }

    /**
     * Subscribe for MemberRemoved event. This actor will receive a CurrentClusterState message as acknowledgement.
     */
    default void subscribeForClusterMemberRemovedAware() {
        Cluster.get(context().system()).subscribe(self(), ClusterEvent.MemberRemoved.class);
    }

    /**
     * Remove all actors matching an address from a DData-writer.
     *
     * @param memberRemoved the member-removed event.
     */
    default void memberRemoved(final ClusterEvent.MemberRemoved memberRemoved) {
        final var address = memberRemoved.member().address();
        if (Cluster.get(context().system()).isTerminated()) {
            log().debug("This instance was terminated from cluster, NOT removing removed member address <{}>",
                    address);
        } else {
            // detected unreachable remote. remove it from local ORMultiMap.
            log().info("Removing address of removed member from DData: <{}>", address);
            getDDataWriter().removeAddress(address, writeLocal())
                    .whenComplete((unused, error) -> {
                        if (error != null) {
                            log().error(error, "Failed to remove address of removed cluster member: <{}>", address);
                        }
                    });
        }
    }

    /**
     * Log the CurrentClusterState message at INFO level. The CurrentClusterState message is used as acknowledgement
     * for cluster event subscription.
     *
     * @param currentClusterState the CurrentClusterState message.
     */
    default void logCurrentClusterState(final ClusterEvent.CurrentClusterState currentClusterState) {
        log().info("Got <{}>", currentClusterState);
    }

}
