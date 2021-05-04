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
package org.eclipse.ditto.base.model.signals;

import java.util.Optional;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Implementations of this interface are associated to an entity returned from {@link #getEntity(org.eclipse.ditto.base.model.json.JsonSchemaVersion)} .
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
