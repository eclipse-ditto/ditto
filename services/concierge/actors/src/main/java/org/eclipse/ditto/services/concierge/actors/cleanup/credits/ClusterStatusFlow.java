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

import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.stream.javadsl.Flow;

/**
 * Retrieve cluster health when prompted by a tick.
 */
public final class ClusterStatusFlow {

    // TODO: document
    public static <T> Flow<T, ClusterStatus, NotUsed> create(final ActorSystem actorSystem) {
        return create(new ClusterStatusSupplier(Cluster.get(actorSystem)));
    }

    // TODO: document
    public static <T> Flow<T, ClusterStatus, NotUsed> create(final ClusterStatusSupplier clusterStatusSupplier) {
        return Flow.fromFunction(tick -> clusterStatusSupplier.get());
    }
}
