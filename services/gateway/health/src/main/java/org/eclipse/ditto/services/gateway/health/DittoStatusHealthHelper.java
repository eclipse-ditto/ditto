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

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;

import akka.actor.ActorSystem;

/**
 * Implementation of {@link StatusHealthHelper} for the ditto-cluster.
 */
public final class DittoStatusHealthHelper extends AbstractStatusHealthHelper {

    private DittoStatusHealthHelper(final ActorSystem actorSystem, final Supplier<ClusterStatus> clusterStateSupplier) {
        super(actorSystem, clusterStateSupplier);
    }

    /**
     * Returns a new {@code StatusHealthHelper} for the given {@code actorSystem}.
     *
     * @param actorSystem the ActorSystem to use
     * @param clusterStateSupplier the {@link ClusterStatus} supplier to use in order to find out the reachable cluster
     * nodes
     * @return the StatusHealthHelper.
     */
    public static StatusHealthHelper of(final ActorSystem actorSystem,
            final Supplier<ClusterStatus> clusterStateSupplier) {
        return new DittoStatusHealthHelper(actorSystem, clusterStateSupplier);
    }

    @Override
    public CompletionStage<JsonObject> calculateOverallHealthJson() {
        return retrieveOverallRolesHealth(actorSystem, clusterStateSupplier)
                .thenApply(this::combineHealth)
                .thenApply(this::setOverallHealth);
    }

}
