/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.health;

import java.util.function.Supplier;

import org.eclipse.ditto.gateway.service.util.config.health.HealthCheckConfig;
import org.eclipse.ditto.internal.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.internal.utils.health.status.StatusHealthSupplier;
import org.eclipse.ditto.internal.utils.health.status.StatusSupplier;

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
     * @param clusterStateSupplier the {@link org.eclipse.ditto.internal.utils.health.cluster.ClusterStatus} supplier
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
