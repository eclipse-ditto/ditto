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
package org.eclipse.ditto.base.model.signals.commands;

import java.util.Optional;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonValue;

/**
 * Implementations of this interface are associated to an entity returned from
 * {@link #getEntity(org.eclipse.ditto.base.model.json.JsonSchemaVersion)}.
 *
 * @param <T> the type of the implementing class.
 */
public interface WithEntity<T extends WithEntity<T>> {

    /**
     * Returns the plain JSON string representation of the entity which is an
     * optimization: WithEntity implementations may work on a plain representation, e.g. responses contain directly a
     * plain JSON string which should be returned at API level.
     * <p>
     * By default, returns empty, so that not all implementing classes must be migrated at once.
     * </p>
     *
     * @return the plain JSON string representation of the entity.
     */
    default Optional<String> getEntityPlainString() {
        return Optional.empty();
    }

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
