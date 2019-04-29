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
package org.eclipse.ditto.services.gateway.health;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.services.utils.health.StatusInfo;

/**
 * Determines the status and health of a cluster.
 */
public interface StatusAndHealthProvider {

    /**
     * Retrieves the status information and provides it as {@link CompletionStage}.
     *
     * @return the status information.
     */
    CompletionStage<JsonObject> retrieveStatus();

    /**
     * Retrieves the health information and provides it as {@link CompletionStage}.
     *
     * @return the health information.
     */
    CompletionStage<StatusInfo> retrieveHealth();

}
