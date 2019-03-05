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

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.services.utils.health.status.Status;
import org.eclipse.ditto.services.utils.health.status.StatusSupplier;

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
