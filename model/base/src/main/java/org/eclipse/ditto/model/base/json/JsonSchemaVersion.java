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
package org.eclipse.ditto.model.base.json;

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
 * {@link Jsonifiable.WithPredicate#toJson(Predicate)} for example.
 */
public enum JsonSchemaVersion implements JsonFieldMarker, Predicate<JsonField> {

    /**
     * Version 1 of JSON schema.
     */
    V_1(1),

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

    private JsonSchemaVersion(final int theSchemaVersionNumber) {
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
