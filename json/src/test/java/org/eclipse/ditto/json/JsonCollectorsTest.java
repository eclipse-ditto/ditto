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

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.stream.Stream;

import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

/**
 * Unit test for {@link JsonCollectors}.
 */
@SuppressWarnings("ClassWithTooManyFields")
public final class JsonCollectorsTest {

    private static final JsonKey ID_KEY = JsonFactory.newKey("id");
    private static final JsonKey STATE_KEY = JsonFactory.newKey("state");
    private static final JsonKey MANUFACTURER_KEY = JsonFactory.newKey("manufacturer");
    private static final JsonKey NAME_KEY = JsonFactory.newKey("name");
    private static final JsonKey COUNTRY_KEY = JsonFactory.newKey("country");

    private static final JsonValue ID = JsonFactory.newValue(4223);
    private static final JsonValue STATE = JsonFactory.newValue("off");
    private static final JsonValue NAME = JsonFactory.newValue("ACME");
    private static final JsonValue COUNTRY = JsonFactory.newValue("Germany");
    private static final JsonValue MANUFACTURER = JsonFactory.newObjectBuilder() //
            .set(NAME_KEY, NAME) //
            .set(COUNTRY_KEY, COUNTRY) //
            .build();

    private static final JsonField ID_FIELD = JsonFactory.newField(ID_KEY, ID);
    private static final JsonField STATE_FIELD = JsonFactory.newField(STATE_KEY, STATE);
    private static final JsonField MANUFACTURER_FIELD = JsonFactory.newField(MANUFACTURER_KEY, MANUFACTURER);

    private static final JsonObject KNOWN_JSON_OBJECT = JsonFactory.newObjectBuilder() //
            .set(ID_FIELD) //
            .set(STATE_FIELD) //
            .set(MANUFACTURER_FIELD) //
            .build();

    private static final JsonArray KNOWN_JSON_ARRAY = JsonFactory.newArrayBuilder().add(ID) //
            .add(STATE) //
            .add(NAME) //
            .add(COUNTRY) //
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonCollectors.class, areImmutable());
    }


    @Test
    public void collectingFieldsOfObjectReturnsEqualObject() {
        final JsonObject jsonObject = KNOWN_JSON_OBJECT.stream() //
                .collect(JsonCollectors.fieldsToObject());

        DittoJsonAssertions.assertThat(jsonObject).isEqualTo(KNOWN_JSON_OBJECT);
    }


    @Test
    public void collectingFieldKeysOfObjectReturnsExpectedArray() {
        final JsonArray expectedArray = JsonFactory.newArrayBuilder() //
                .add(JsonFactory.newValue(ID_KEY.toString())) //
                .add(JsonFactory.newValue(STATE_KEY.toString())) //
                .add(JsonFactory.newValue(MANUFACTURER_KEY.toString())) //
                .build();

        final JsonArray actualArray = KNOWN_JSON_OBJECT.stream() //
                .collect(JsonCollectors.fieldKeysToArray());

        DittoJsonAssertions.assertThat(actualArray).isEqualTo(expectedArray);
    }


    @Test
    public void collectingFieldValuesOfObjectReturnsExpectedArray() {
        final JsonArray expectedArray = JsonFactory.newArrayBuilder() //
                .add(ID) //
                .add(STATE) //
                .add(MANUFACTURER) //
                .build();

        final JsonArray actualArray = KNOWN_JSON_OBJECT.stream() //
                .collect(JsonCollectors.fieldValuesToArray());

        DittoJsonAssertions.assertThat(actualArray).isEqualTo(expectedArray);
    }


    @Test
    public void collectingValuesOfArrayReturnsEqualArray() {
        final JsonArray jsonArray = KNOWN_JSON_ARRAY.stream() //
                .collect(JsonCollectors.valuesToArray());

        DittoJsonAssertions.assertThat(jsonArray).isEqualTo(KNOWN_JSON_ARRAY);
    }


    @Test
    public void collectingObjectsReturnsExpectedObject() {
        final JsonObject anotherJsonObject = JsonFactory.newObjectBuilder() //
                .set("Foo", "Bar") //
                .build();

        final JsonObject expectedObject = JsonFactory.newObjectBuilder() //
                .setAll(KNOWN_JSON_OBJECT) //
                .setAll(anotherJsonObject)//
                .build();

        final JsonObject actualObject =
                Stream.of(KNOWN_JSON_OBJECT, anotherJsonObject).collect(JsonCollectors.objectsToObject());

        DittoJsonAssertions.assertThat(actualObject).isEqualTo(expectedObject);
    }

}
