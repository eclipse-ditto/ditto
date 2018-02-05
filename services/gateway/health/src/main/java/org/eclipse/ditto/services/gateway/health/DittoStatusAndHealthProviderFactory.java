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

import java.util.function.Supplier;

import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.services.utils.health.status.StatusHealthSupplier;
import org.eclipse.ditto.services.utils.health.status.StatusSupplier;

import akka.actor.ActorSystem;

/**
 * Provides a {@link StatusAndHealthProvider} for the ditto-cluster.
 */
public final class DittoStatusAndHealthProviderFactory {

    private DittoStatusAndHealthProviderFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new {@link StatusAndHealthProvider} for the ditto-cluster.
     *
     * @param actorSystem the ActorSystem to use.
     * @param clusterStateSupplier the {@link ClusterStatus} supplier to use in order to find out the reachable cluster
     * nodes.
     * @return the {@link StatusAndHealthProvider}.
     */
    public static StatusAndHealthProvider of(final ActorSystem actorSystem,
            final Supplier<ClusterStatus> clusterStateSupplier) {
        final StatusSupplier statusSupplier =
                ClusterStatusSupplier.of(actorSystem, clusterStateSupplier);
        final StatusHealthSupplier healthSupplier =
                ClusterStatusHealthSupplier.of(actorSystem, clusterStateSupplier);

        return ConfigurableStatusAndHealthProvider.of(statusSupplier, healthSupplier);
    }

}
