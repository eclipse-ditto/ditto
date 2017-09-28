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
package org.eclipse.ditto.json;

import java.util.Set;

/**
 * A {@code JsonFieldDefinition} is a formal description of a single {@link JsonField}. A JSON field consists of a key
 * (or name) and a value. A JsonFieldDefinition differs in the way that it consists not only of a simple JSON key but
 * its super type {@link JsonPointer}. With the help of this interface one can explicitly define a schema of a JSON
 * document including all sub documents. <p> The following example shows how a JSON document would be described with the
 * help of JsonFieldDefinition.
 * <p>
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
 * <p>
 * <pre>
 *       import static JsonFactory.newFieldDefinition;
 *       ...
 *
 *        public final class Thing {
 *
 *           private static final JsonFieldDefinition THING_ID = newFieldDefinition("thingId", String.class);
 *           private static final JsonFieldDefinition SUBSEL = newFieldDefinition("attributes/someAttr/subsel",
 * int.class);
 *           private static final JsonFieldDefinition ANOTHER_ATTR = newFieldDefinition("attributes/anotherAttr",
 * String.class);
 *
 *           ...
 *
 *        }
 * </pre>
 * <p>
 * In this case {@code attributes} and {@code someAttr} are implicitly defined with the value type {@link JsonObject}.
 * <p> Additionally, a JSON field definition can be marked with zero to n {@link JsonFieldMarker}s. The semantics of a
 * marker is defined by you rather than Ditto JSON. One possible usage scenario would be to define the fields which belong
 * to a particular schema version with a maker according to that version. </p> <p> <em>Implementations of this interface
 * are required to be immutable!</em> </p>
 */
public interface JsonFieldDefinition {

    /**
     * Returns a new JSON field definition which is based on the given arguments.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param valueType the type of the value of the defined JSON field.
     * @param markers an optional array of markers which add user defined semantics to the defined JSON field.
     * @return the new JSON field definition.
     * @throws NullPointerException if any argument but {@code markers} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     * @see JsonFactory#newFieldDefinition(JsonPointer, Class, Set)
     */
    static JsonFieldDefinition newInstance(final CharSequence pointer, final Class<?> valueType,
            final JsonFieldMarker... markers) {
        return JsonFactory.newFieldDefinition(pointer, valueType, markers);
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
    Class<?> getValueType();

    /**
     * Returns an unordered and unmodifiable set of markers which provide this field with semantics, e. g. schema
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

}
