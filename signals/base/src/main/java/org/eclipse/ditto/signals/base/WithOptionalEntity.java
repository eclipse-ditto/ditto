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
package org.eclipse.ditto.signals.base;

import java.util.Optional;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Implementations of this interface are associated to an entity returned from {@link #getEntity(JsonSchemaVersion)} .
 */
public interface WithOptionalEntity {

    /**
     * Returns the entity as JSON.
     *
     * @return the entity as JSON.
     */
    default Optional<JsonValue> getEntity() {
        return getEntity(JsonSchemaVersion.LATEST);
    }

    /**
     * Returns the entity as JSON.
     *
     * @param schemaVersion the JsonSchemaVersion in which to return the JSON.
     * @return the entity as JSON.
     */
    default Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.empty();
    }

}
