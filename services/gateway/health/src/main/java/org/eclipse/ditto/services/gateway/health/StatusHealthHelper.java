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

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.services.utils.health.HealthStatus;

/**
 * Determines the status and health of a cluster.
 */
public interface StatusHealthHelper {

    /**
     * The Json key for the cluster roles.
     */
    String JSON_KEY_ROLES = "roles";

    /**
     * Retrieves the static "status" information from each reachable cluster member in the cluster grouped by the
     * cluster {@code role} of the services and containing the address in the cluster as additional Json key. nodes.
     *
     * @return a CompletionStage of a list of all "status" JsonObjects containing the cluster {@code role}s as keys
     */
    CompletionStage<List<JsonObject>> retrieveOverallRolesStatus();

    /**
     * Calculates the status of the overall cluster health and provides it as {@link CompletionStage}.
     *
     * @return CompletionStage of the overall cluster health
     */
    CompletionStage<JsonObject> calculateOverallHealthJson();

    /**
     * Checks in the passed {@code subSystemsStatus} if all contained JsonObjects have a {@link
     * HealthStatus#JSON_KEY_STATUS} value of "UP" or "UNKNOWN" - then this method returns {@code true}.
     *
     * @return {@code true} if all sub systems were "UP" or "UNKNOWN"
     */
    boolean checkIfAllSubStatusAreUp(final JsonObject subSystemsStatus);

}
