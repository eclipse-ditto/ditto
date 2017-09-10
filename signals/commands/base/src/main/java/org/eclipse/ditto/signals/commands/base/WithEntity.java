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
package org.eclipse.ditto.signals.commands.base;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Implementations of this interface are associated to an entity returned from {@link #getEntity(JsonSchemaVersion)}.
 *
 * @param <T> the type of the implementing class.
 */
public interface WithEntity<T extends WithEntity> {

    /**
     * Returns the entity as JSON.
     *
     * @return the entity as JSON.
     */
    default JsonValue getEntity() {
        return getEntity(JsonSchemaVersion.LATEST);
    }

    /**
     * Sets the entity and returns a new object.
     *
     * @param entity the entity to set.
     * @return the newly created object with the set entity.
     * @throws NullPointerException if the passed {@code entity} is null.
     */
    T setEntity(JsonValue entity);

    /**
     * Returns the entity as JSON.
     *
     * @param schemaVersion the JsonSchemaVersion in which to return the JSON.
     * @return the entity as JSON.
     */
    JsonValue getEntity(JsonSchemaVersion schemaVersion);

}
