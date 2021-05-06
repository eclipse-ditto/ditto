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

import java.util.Set;

import javax.annotation.Nullable;

/**
 * A {@code JsonFieldDefinition} is a formal description of a single {@link JsonField}. A JSON field consists of a key
 * (or name) and a value. A JsonFieldDefinition differs in the way that it consists not only of a simple JSON key but
 * its super type {@link JsonPointer}. With the help of this interface one can explicitly define a schema of a JSON
 * document including all sub documents.
 * <p>
 * The following example shows how a JSON document would be described with the help of JsonFieldDefinition.
 * </p>
 * <pre>
 *      {
 *         "thingId": "myThing",
 *         "attributes": {
 *            "someAttr": {
 *               "subsel": 42
 *            },
 *            "anotherAttr": "baz"
 *         }
 *      }
 * </pre>
 * <p>
 * Within an according class the structure of this JSON document could be described as follows:
 * </p>
 * <pre>
 *    import static JsonFactory.newIntFieldDefinition;
 *    import static JsonFactory.newStringFieldDefinition;
 *    ...
 *
 *    public final class Thing {
 *
 *        private static final JsonFieldDefinition THING_ID = newStringFieldDefinition("thingId");
 *        private static final JsonFieldDefinition SUBSEL = newIntFieldDefinition("attributes/someAttr/subsel");
 *        private static final JsonFieldDefinition ANOTHER_ATTR = newStringFieldDefinition("attributes/anotherAttr");
 *
 *       ...
 *
 *    }
 * </pre>
 * <p>
 * In this case {@code attributes} and {@code someAttr} are implicitly defined with the value type {@link JsonObject}.
 * </p>
 * <p>
 * Additionally, a JSON field definition can be marked with zero to n {@link JsonFieldMarker}s. The semantics of a
 * marker is defined by you rather than Ditto JSON. One possible usage scenario would be to define the fields which
 * belong to a particular schema version with a maker according to that version.
 * </p>
 * <p>
 * <em>Implementations of this interface are required to be immutable!</em>
 * </p>
 */
public interface JsonFieldDefinition<T> {

    /**
     * Returns a new JSON field definition for a String value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     * @see JsonFactory#newStringFieldDefinition(CharSequence, JsonFieldMarker...)
     */
    static JsonFieldDefinition<String> ofString(final CharSequence pointer, final JsonFieldMarker ... markers) {
        return JsonFactory.newStringFieldDefinition(pointer, markers);
    }

    /**
     * Returns a new JSON field definition for an {@code int} value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     * @see JsonFactory#newIntFieldDefinition(CharSequence, JsonFieldMarker...)
     */
    static JsonFieldDefinition<Integer> ofInt(final CharSequence pointer, final JsonFieldMarker ... markers) {
        return JsonFactory.newIntFieldDefinition(pointer, markers);
    }

    /**
     * Returns a new JSON field definition for a {@code long} value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     * @see JsonFactory#newLongFieldDefinition(CharSequence, JsonFieldMarker...)
     */
    static JsonFieldDefinition<Long> ofLong(final CharSequence pointer, final JsonFieldMarker ... markers) {
        return JsonFactory.newLongFieldDefinition(pointer, markers);
    }

    /**
     * Returns a new JSON field definition for a {@code double} value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     * @see JsonFactory#newDoubleFieldDefinition(CharSequence, JsonFieldMarker...)
     */
    static JsonFieldDefinition<Double> ofDouble(final CharSequence pointer, final JsonFieldMarker ... markers) {
        return JsonFactory.newDoubleFieldDefinition(pointer, markers);
    }

    /**
     * Returns a new JSON field definition for a {@code boolean} value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     * @see JsonFactory#newBooleanFieldDefinition(CharSequence, JsonFieldMarker...)
     */
    static JsonFieldDefinition<Boolean> ofBoolean(final CharSequence pointer, final JsonFieldMarker ... markers) {
        return JsonFactory.newBooleanFieldDefinition(pointer, markers);
    }

    /**
     * Returns a new JSON field definition for a {@link JsonObject} value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     * @see JsonFactory#newJsonObjectFieldDefinition(CharSequence, JsonFieldMarker...)
     */
    static JsonFieldDefinition<JsonObject> ofJsonObject(final CharSequence pointer, final JsonFieldMarker ... markers) {
        return JsonFactory.newJsonObjectFieldDefinition(pointer, markers);
    }

    /**
     * Returns a new JSON field definition for a {@link JsonArray} value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     * @see JsonFactory#newJsonArrayFieldDefinition(CharSequence, JsonFieldMarker...)
     */
    static JsonFieldDefinition<JsonArray> ofJsonArray(final CharSequence pointer, final JsonFieldMarker ... markers) {
        return JsonFactory.newJsonArrayFieldDefinition(pointer, markers);
    }

    /**
     * Returns a new JSON field definition for a {@link JsonValue} value.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if {@code pointer} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     * @see JsonFactory#newJsonValueFieldDefinition(CharSequence, JsonFieldMarker...)
     */
    static JsonFieldDefinition<JsonValue> ofJsonValue(final CharSequence pointer, final JsonFieldMarker ... markers) {
        return JsonFactory.newJsonValueFieldDefinition(pointer, markers);
    }

    /**
     * Returns the JSON pointer which refers to this field.
     *
     * @return the JSON pointer to this field.
     */
    JsonPointer getPointer();

    /**
     * Returns the type of the value of this field.
     *
     * @return the value type of this field.
     */
    Class<T> getValueType();

    /**
     * Returns an unordered and unmodifiable set of markers which provide this field with semantics, e.g. schema
     * version number.
     *
     * @return the markers of this field or an empty set.
     */
    Set<JsonFieldMarker> getMarkers();

    /**
     * Indicates whether this definition is marked with at least all specified markers.
     *
     * @param fieldMarker the mandatory fieldMarker to check this definition for.
     * @param furtherFieldMarkers additional markers to check this definition for.
     * @return true if this definition is marked with at least all specified markers, {@code false} else.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean isMarkedAs(JsonFieldMarker fieldMarker, JsonFieldMarker... furtherFieldMarkers);

    /**
     * Maps the specified JsonValue to the value type of this field definition. If the JsonValue cannot be mapped a
     * {@link JsonParseException} is thrown.
     *
     * @param jsonValue the JsonValue to be mapped.
     * @return the JsonValue as the Java type of this field definition.
     * @throws NullPointerException if {@code jsonValue} is {@code null}.
     * @throws JsonParseException if {@code jsonValue} does not represent this definition's expected Java type.
     * @see #getValueType()
     */
    @Nullable
    T mapValue(JsonValue jsonValue);

}
