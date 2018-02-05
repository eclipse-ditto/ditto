/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.health;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.services.utils.health.status.StatusHealthSupplier;

import akka.actor.ActorSystem;

/**
 * Provides aggregated health information for a cluster, grouped by the cluster's roles.
 */
public class ClusterStatusHealthSupplier implements StatusHealthSupplier {

    private ClusterStatusAndHealthHelper clusterStatusHealthHelper;

    private ClusterStatusHealthSupplier(final ActorSystem actorSystem,
            final Supplier<ClusterStatus> clusterStateSupplier) {
        this.clusterStatusHealthHelper = ClusterStatusAndHealthHelper.of(actorSystem, clusterStateSupplier);
    }

    /**
     * Returns a new {@link ClusterStatusHealthSupplier}.
     *
     * @param actorSystem the ActorSystem to use.
     * @param clusterStateSupplier the {@link ClusterStatus} supplier to use in order to find out the reachable cluster
     * nodes.
     * @return the {@link ClusterStatusHealthSupplier}.
     */
    public static StatusHealthSupplier of(final ActorSystem actorSystem,
            final Supplier<ClusterStatus> clusterStateSupplier) {

        requireNonNull(actorSystem);
        requireNonNull(clusterStateSupplier);

        return new ClusterStatusHealthSupplier(actorSystem, clusterStateSupplier);
    }

    @Override
    public CompletionStage<StatusInfo> get() {
        return clusterStatusHealthHelper.retrieveOverallRolesHealth();
    }
}
