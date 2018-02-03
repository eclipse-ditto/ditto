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
package org.eclipse.ditto.services.utils.health;

import javax.annotation.Nullable;

import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Factory for health of the underlying system including cluster and persistence.
 */
@AllValuesAreNonnullByDefault
public final class PersistenceClusterHealth {

    static final String PERSISTENCE = "persistence";

    static final String CLUSTER = "cluster";

    /**
     * Returns a new {@code Health} instance.
     *
     * @return the Health instance.
     */
    public static Health newInstance() {
        return Health.newBuilder().build();
    }

    /**
     * Returns a new {@code Health} instance with the specified {@code statusPersistence} and
     * {@code statusCluster}.
     *
     * @param statusPersistence the persistence's status.
     * @param statusCluster the cluster's status.
     * @return the Health instance.
     */
    public static Health of(@Nullable final StatusInfo statusPersistence,
            @Nullable final StatusInfo statusCluster) {
        final Health.Builder builder = Health.newBuilder()
                .setOverallStatus(getOverallStatus(statusPersistence, statusCluster));
        if (statusPersistence != null) {
            builder.setComponentStatus(PERSISTENCE, statusPersistence);
        }
        if (statusCluster != null) {
            builder.setComponentStatus(CLUSTER, statusCluster);
        }
        return builder.build();
    }

    /**
     * Sets the persistence {@code healthStatus}.
     *
     * @param health the starting immutable health object.
     * @param healthStatus the healthStatus.
     * @return a copy of the given health object with the new healthStatus set.
     */
    public static Health setHealthStatusPersistence(final Health health, final StatusInfo healthStatus) {
        return health.toBuilder()
                .setComponentStatus(PERSISTENCE, healthStatus)
                .build();
    }

    private static StatusInfo getOverallStatus(@Nullable final StatusInfo statusPersistence,
            @Nullable final StatusInfo statusCluster) {

        final boolean allUp;

        if (statusPersistence != null && statusCluster != null) {
            switch (statusPersistence.getStatus()) {
                case UP:
                case UNKNOWN:
                    switch (statusCluster.getStatus()) {
                        case UP:
                        case UNKNOWN:
                            allUp = true;
                            break;
                        default:
                            allUp = false;
                    }
                    break;
                default:
                    allUp = false;
            }
        } else {
            allUp = false;
        }

        final StatusInfo.Status status = allUp ? StatusInfo.Status.UP : StatusInfo.Status.DOWN;
        return StatusInfo.fromStatus(status);
    }
}
