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
package org.eclipse.ditto.model.base.entity;

import java.time.Instant;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Base type for top level entities.
 *
 * @param <T> The type of the Revision of the entity.
 */
@Immutable
public interface Entity<T extends Revision<T>> extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns the ID of this entity.
     *
     * @return the ID of this entity.
     * @deprecated entity IDs are now typed. Use {@link #getEntityId()} instead.
     */
    @Deprecated
    default Optional<String> getId() {
        return getEntityId().map(String::valueOf);
    }

    /**
     * Returns the ID of this entity.
     *
     * @return the ID of this entity.
     */
    Optional<? extends EntityId> getEntityId();

    /**
     * Returns the current revision of this entity.
     *
     * @return the current revision of this entity.
     */
    Optional<T> getRevision();

    /**
     * Returns the modified timestamp of this entity.
     *
     * @return the timestamp.
     */
    Optional<Instant> getModified();

    /**
     * Returns whether this entity is deleted.
     *
     * @return {@code true}, if deleted; false, otherwise.
     */
    boolean isDeleted();

    /**
     * Returns all non hidden marked fields of this object.
     *
     * @return a JSON object representation of this object including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }
}
