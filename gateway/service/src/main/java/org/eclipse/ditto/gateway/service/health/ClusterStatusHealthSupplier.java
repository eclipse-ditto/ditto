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

import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.internal.utils.health.status.StatusHealthSupplier;

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
