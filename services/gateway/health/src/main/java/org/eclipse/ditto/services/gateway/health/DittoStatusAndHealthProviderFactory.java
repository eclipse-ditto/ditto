/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.health;

import java.util.function.Supplier;

import org.eclipse.ditto.services.gateway.health.config.HealthCheckConfig;
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
     * @param clusterStateSupplier the {@link org.eclipse.ditto.services.utils.health.cluster.ClusterStatus} supplier
     * to use in order to find out the reachable cluster nodes.
     * @param healthCheckConfig the configuration settings for health checking.
     * @return the {@link StatusAndHealthProvider}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static StatusAndHealthProvider of(final ActorSystem actorSystem,
            final Supplier<ClusterStatus> clusterStateSupplier, final HealthCheckConfig healthCheckConfig) {

        final ClusterStatusAndHealthHelper clusterStatusAndHealthHelper =
                ClusterStatusAndHealthHelper.of(actorSystem, clusterStateSupplier, healthCheckConfig);

        final StatusSupplier statusSupplier = ClusterStatusSupplier.of(clusterStatusAndHealthHelper);
        final StatusHealthSupplier healthSupplier = ClusterStatusHealthSupplier.of(clusterStatusAndHealthHelper);

        return ConfigurableStatusAndHealthProvider.of(statusSupplier, healthSupplier);
    }

}
