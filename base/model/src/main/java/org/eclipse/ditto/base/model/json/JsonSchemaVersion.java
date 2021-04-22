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
package org.eclipse.ditto.base.model.json;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldMarker;
import org.eclipse.ditto.json.JsonKey;

/**
 * An enumeration of versions of JSON schemas. An enumeration of types of JSON fields. Schema version constant is
 * simultaneously a {@code Predicate} and can be used as argument for
 * {@link org.eclipse.ditto.base.model.json.Jsonifiable.WithPredicate#toJson(java.util.function.Predicate)} for example.
 */
public enum JsonSchemaVersion implements JsonFieldMarker, Predicate<JsonField> {

    /**
     * Version 2 of JSON schema.
     */
    V_2(2);

    /**
     * Defines the currently latest available schema version.
     */
    public static final JsonSchemaVersion LATEST = V_2;

    private static final JsonKey JSON_KEY = JsonFactory.newKey("__schemaVersion");

    private final int schemaVersionNumber;
    private final Predicate<JsonField> predicate;

    JsonSchemaVersion(final int theSchemaVersionNumber) {
        schemaVersionNumber = theSchemaVersionNumber;
        predicate = jsonField -> jsonField.isMarkedAs(this);
    }

    /**
     * Returns the JSON key of the schema version.
     *
     * @return the JSON key of the schema version.
     */
    public static JsonKey getJsonKey() {
        return JSON_KEY;
    }

    /**
     * Gets the JsonSchemaVersion for the passed integer.
     *
     * @param versionInt the version as integer to get the SchemaVersion for.
     * @return the JsonSchemaVersion for the passed integer.
     */
    public static Optional<JsonSchemaVersion> forInt(final int versionInt) {
        return Stream.of(values())
                .filter(sv -> versionInt == sv.toInt())
                .findFirst();
    }

    /**
     * Returns the {@code int} value of the version number of this schema.
     *
     * @return the version number of this schema.
     */
    public int toInt() {
        return schemaVersionNumber;
    }

    @Override
    public boolean test(final JsonField jsonField) {
        return predicate.test(jsonField);
    }

    /**
     * Returns the version number as String.
     *
     * @return the String representation of this version's number.
     * @see #toInt()
     */
    @Override
    public String toString() {
        return String.format("%d", toInt());
    }

}
