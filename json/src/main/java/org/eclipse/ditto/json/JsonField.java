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
package org.eclipse.ditto.json;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

/**
 * Represents a single JSON field. A JSON field in its simplest form is a key-value-pair. Additionally a field can be
 * aware of its definition which allows to obtain meta information like the Java type of the value or the markers of the
 * field. A JSON object, for example, can be understood as a tree of JSON fields.
 * <p>
 * <em>Implementations of this interface are required to be immutable!</em>
 * </p>
 */
public interface JsonField {

    /**
     * Returns a new JSON field based on the specified key value pair.
     *
     * @param key the key of the field to be created.
     * @param value the value of the field to be created. {@code null} will be converted to the JSON NULL Literal.
     * @return a new JSON field containing the specified key value pair.
     * @throws NullPointerException if {@code key} is null.
     */
    static JsonField newInstance(final CharSequence key, @Nullable final JsonValue value) {
        return JsonFactory.newField(JsonFactory.newKey(key), value);
    }

    /**
     * Returns a new JSON field based on the specified key value pair and definition.
     *
     * @param key the key of the field to be created.
     * @param value the value of the field to be created. {@code null} will be converted to the JSON NULL Literal.
     * @param definition the definition of the field to be created.
     * @return a new JSON field containing the specified key value pair and definition.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    static JsonField newInstance(final CharSequence key, @Nullable final JsonValue value,
            @Nullable final JsonFieldDefinition<?> definition) {

        return JsonFactory.newField(JsonFactory.newKey(key), value, definition);
    }

    /**
     * Returns a {@code Predicate} for testing if the value of a JSON field is null.
     *
     * @return the predicate.
     * @see JsonValue#isNull()
     */
    static Predicate<JsonField> isValueNonNull() {
        return jsonField -> !jsonField.getValue().isNull();
    }

    /**
     * Returns the name of this JSON field's key.
     *
     * @return the name of this field's key.
     */
    String getKeyName();

    /**
     * Returns this JSON field's key.
     *
     * @return the key of this field.
     */
    JsonKey getKey();

    /**
     * Returns this JSON field's value.
     *
     * @return the value of this field.
     */
    JsonValue getValue();

    /**
     * Returns this JSON field's definition (meta-information).
     *
     * @return the definition of this field.
     */
    @SuppressWarnings({"rawtypes", "java:S3740"})
    Optional<JsonFieldDefinition> getDefinition();

    /**
     * Indicates whether this field is marked with at least all specified markers. This method can only return
     * {@code true} if it has a definition.
     *
     * @param fieldMarker the mandatory fieldMarker to check this field for.
     * @param furtherFieldMarkers additional markers to check this field for.
     * @return {@code true} if this field is marked with at least all specified markers, {@code false} else.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean isMarkedAs(JsonFieldMarker fieldMarker, JsonFieldMarker... furtherFieldMarkers);

    /**
     * Writes this JsonField's key and value into the provided serialization context.
     * This is intended to be used by serialization logic only.
     *
     * @param serializationContext the context for serialization bundling configuration and state needed for
     * serialization.
     * @throws IOException in case writing the value to the backing OutputStream causes an IOException.
     * @since 1.1.0
     */
    void writeKeyAndValue(SerializationContext serializationContext) throws IOException;
}
