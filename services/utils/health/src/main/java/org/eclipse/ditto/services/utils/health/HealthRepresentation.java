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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Interface of all representations of the health of underlying systems.
 */
public interface HealthRepresentation extends Jsonifiable<JsonObject> {

    /**
     * Returns a concise summary of the health of an underlying system.
     *
     * @return the health summary.
     */
    HealthStatus getHealthStatus();
}
