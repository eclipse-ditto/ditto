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

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.internal.utils.health.status.Status;
import org.eclipse.ditto.internal.utils.health.status.StatusSupplier;

/**
 * Provides aggregated status information for a cluster, grouped by the cluster's roles.
 */
final class ClusterStatusSupplier implements StatusSupplier {

    private final ClusterStatusAndHealthHelper clusterStatusAndHealthHelper;

    private ClusterStatusSupplier(final ClusterStatusAndHealthHelper clusterStatusAndHealthHelper) {
        this.clusterStatusAndHealthHelper = clusterStatusAndHealthHelper;
    }

    /**
     * Returns a new {@code ClusterStatusSupplier}.
     *
     * @param clusterStatusAndHealthHelper is used for retrieving status and health information via the cluster.
     * @return the ClusterStatusSupplier.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ClusterStatusSupplier of(final ClusterStatusAndHealthHelper clusterStatusAndHealthHelper) {
        return new ClusterStatusSupplier(clusterStatusAndHealthHelper);
    }

    @Override
    public CompletionStage<JsonObject> get() {
        // append roles statuses to static status
        return clusterStatusAndHealthHelper.retrieveOverallRolesStatus()
                .thenApply(Status.provideStaticStatus()::setAll);
    }

}
