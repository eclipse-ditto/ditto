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
     * The regex pattern for a Namespace.
     */
    String NAMESPACE_REGEX = "(?<ns>(?:(?:[a-zA-Z]\\w*+)(?:\\.[a-zA-Z]\\w*+)*+))";

    /**
     * The regex pattern for an Entity Name. Has to be conform to
     * <a href="http://www.ietf.org/rfc/rfc3986.txt">RFC-3986</a>.
     */
    String ENTITY_NAME_REGEX =
            "(?<id>(?:[-\\w:@&=+,.!~*'_;]|%\\p{XDigit}{2})(?:[-\\w:@&=+,.!~*'$_;]|%\\p{XDigit}{2})*+)";

    /**
     * The regex pattern for an Entity ID.
     * Combines "namespace" pattern (java package notation + a colon) and "name" pattern.
     */
    String ID_REGEX = NAMESPACE_REGEX + "\\:" + ENTITY_NAME_REGEX;

    /**
     * Returns the ID of this entity.
     *
     * @return the ID of this entity.
     */
    Optional<String> getId();

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
     * Returns whether this entitity is deleted.
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
