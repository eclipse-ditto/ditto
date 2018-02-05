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
