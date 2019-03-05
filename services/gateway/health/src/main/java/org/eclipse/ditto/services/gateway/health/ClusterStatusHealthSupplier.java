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

import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.eclipse.ditto.services.utils.health.status.StatusHealthSupplier;

/**
 * Provides aggregated health information for a cluster, grouped by the cluster's roles.
 */
final class ClusterStatusHealthSupplier implements StatusHealthSupplier {

    private final ClusterStatusAndHealthHelper clusterStatusAndHealthHelper;

    private ClusterStatusHealthSupplier(final ClusterStatusAndHealthHelper clusterStatusAndHealthHelper) {
        this.clusterStatusAndHealthHelper = clusterStatusAndHealthHelper;
    }

    /**
     * Returns a new {@code ClusterStatusHealthSupplier}.
     *
     * @param clusterStatusAndHealthHelper is used for retrieving status and health information via the cluster.
     * @return the ClusterStatusHealthSupplier.
     */
    public static StatusHealthSupplier of(final ClusterStatusAndHealthHelper clusterStatusAndHealthHelper) {
        return new ClusterStatusHealthSupplier(clusterStatusAndHealthHelper);
    }

    @Override
    public CompletionStage<StatusInfo> get() {
        return clusterStatusAndHealthHelper.retrieveOverallRolesHealth();
    }

}
