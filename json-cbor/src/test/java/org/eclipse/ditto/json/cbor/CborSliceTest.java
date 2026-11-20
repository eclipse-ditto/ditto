/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.json.cbor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;

/**
 * Tests that values parsed from CBOR reference slices of one copy of the parsed bytes as their CBOR representation,
 * which lets them release and recover their fields.
 */
public final class CborSliceTest {

    private static final JacksonCborFactory CBOR = new JacksonCborFactory();

    private static final JsonObject THING = JsonFactory.readFrom("{" +
            "\"thingId\":\"org.eclipse.ditto:thing\"," +
            "\"attributes\":{\"location\":{\"room\":{\"id\":\"r1\",\"floor\":2}},\"tags\":[\"a\",\"b\",{\"c\":[1,2.5]}]}," +
            "\"features\":{" +
            "\"thermostat\":{\"properties\":{\"target\":21.5,\"modes\":[\"eco\",\"comfort\"],\"motor\":{\"position\":7}}}," +
            "\"status\":{\"properties\":{\"dimensions\":{\"connectivity\":{\"level\":{\"name\":\"OK\"}}}}}," +
            "\"empty\":{},\"emptyArray\":[],\"nothing\":null,\"flag\":true,\"big\":12345678901234" +
            "}}").asObject();

    @Test
    public void parsedValuesEqualTheSerializedOnes() {
        final JsonValue parsed = CBOR.readFrom(toCbor(THING));

        assertThat(parsed).isEqualTo(THING);
        assertNestedValuesEqual(parsed.asObject(), THING);
    }

    @Test
    public void nestedParsedValuesReserializeToTheirOriginalBytes() {
        final JsonObject parsed = CBOR.readFrom(toCbor(THING)).asObject();

        assertThat(toCbor(parsed)).isEqualTo(toCbor(THING));
        for (final String pointer : new String[]{"/features", "/features/thermostat/properties",
                "/attributes/tags", "/features/empty", "/features/emptyArray"}) {
            final JsonValue nested = valueAt(parsed, pointer);
            assertThat(toCbor(nested)).as(pointer).isEqualTo(toCbor(valueAt(THING, pointer)));
        }
        final JsonValue objectInArray = valueAt(parsed, "/attributes/tags").asArray().get(2).orElseThrow(
                AssertionError::new);
        assertThat(toCbor(objectInArray)).isEqualTo(
                toCbor(valueAt(THING, "/attributes/tags").asArray().get(2).orElseThrow(AssertionError::new)));
    }

    @Test
    public void parsedValuesDoNotDependOnTheInputArray() {
        final byte[] bytes = toCbor(THING);
        final JsonObject parsed = CBOR.readFrom(bytes).asObject();

        Arrays.fill(bytes, (byte) 0);
        clearFields(parsed);

        assertThat(parsed).isEqualTo(THING);
        assertThat(toCbor(parsed)).isEqualTo(toCbor(THING));
    }

    @Test
    public void readFromWithOffsetSlicesTheRightBytes() {
        final byte[] cbor = toCbor(THING);
        final byte[] padded = new byte[cbor.length + 13];
        Arrays.fill(padded, (byte) 0xFF);
        System.arraycopy(cbor, 0, padded, 7, cbor.length);

        final JsonObject parsed = CBOR.readFrom(padded, 7, cbor.length).asObject();
        clearFields(parsed);

        assertThat(parsed).isEqualTo(THING);
        assertThat(toCbor(valueAt(parsed, "/features/thermostat"))).isEqualTo(
                toCbor(valueAt(THING, "/features/thermostat")));
    }

    @Test
    public void readFromByteBufferWithPositionSlicesTheRightBytes() {
        final byte[] cbor = toCbor(THING);
        final ByteBuffer buffer = ByteBuffer.allocate(cbor.length + 5);
        buffer.put(new byte[]{1, 2, 3, 4, 5});
        buffer.put(cbor);
        buffer.position(5);

        final JsonObject parsed = CBOR.readFrom(buffer).asObject();
        clearFields(parsed);

        assertThat(parsed).isEqualTo(THING);
        assertThat(buffer.position()).isEqualTo(5);
    }

    @Test
    public void clearedFieldsAreRecoveredFromTheSlice() {
        final JsonObject parsed = CBOR.readFrom(toCbor(THING)).asObject();
        final JsonObject features = valueAt(parsed, "/features").asObject();
        final JsonArray tags = valueAt(parsed, "/attributes/tags").asArray();

        clearFields(features);
        clearValues(tags);
        assertThat(features).isEqualTo(valueAt(THING, "/features"));
        assertThat(tags).isEqualTo(valueAt(THING, "/attributes/tags"));

        clearFields(parsed);
        assertThat(parsed).isEqualTo(THING);
        assertThat(parsed.toString()).isEqualTo(THING.toString());
        assertNestedValuesEqual(parsed, THING);
    }

    @Test
    public void valuesRecoveredFromNestedSlicesHaveCorrectSlicesThemselves() {
        final byte[] cbor = toCbor(THING);
        final JsonObject parsed = CBOR.readFrom(cbor).asObject();
        final JsonObject otherParse = CBOR.readFrom(cbor).asObject();
        final JsonObject features = valueAt(parsed, "/features").asObject();

        // recover a nested object (its slice does not start at offset 0), then clear and recover below it again
        clearFields(features);
        final JsonObject thermostat = valueAt(features, "/thermostat").asObject();
        final JsonObject properties = valueAt(thermostat, "/properties").asObject();
        clearFields(thermostat);
        clearFields(properties);
        final JsonArray modes = valueAt(properties, "/modes").asArray();
        clearValues(modes);

        for (final String pointer : new String[]{"/features", "/features/thermostat",
                "/features/thermostat/properties", "/features/thermostat/properties/modes",
                "/features/thermostat/properties/motor"}) {
            final JsonValue recovered = valueAt(parsed, pointer);
            assertThat(toCbor(recovered)).as(pointer).isEqualTo(toCbor(valueAt(THING, pointer)));
            // both sides carry slices: compared by their bytes
            assertThat(recovered).as(pointer).isEqualTo(valueAt(otherParse, pointer));
        }
        assertThat(parsed).isEqualTo(otherParse).isEqualTo(THING);
    }

    @Test
    public void indefiniteLengthCborIsSlicedIncludingItsBreakBytes() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (final CBORGenerator generator = new CBORFactory().createGenerator(out)) {
            generator.writeStartObject(); // no size: indefinite length
            generator.writeFieldName("outer");
            generator.writeStartObject();
            generator.writeFieldName("list");
            generator.writeStartArray();
            generator.writeNumber(1);
            generator.writeString("two");
            generator.writeEndArray();
            generator.writeFieldName("x");
            generator.writeBoolean(true);
            generator.writeEndObject();
            generator.writeFieldName("after");
            generator.writeNumber(3);
            generator.writeEndObject();
        }
        final JsonObject expected = JsonFactory.readFrom("{\"outer\":{\"list\":[1,\"two\"],\"x\":true},\"after\":3}")
                .asObject();

        final JsonObject parsed = CBOR.readFrom(out.toByteArray()).asObject();
        final JsonValue outer = valueAt(parsed, "/outer");

        assertThat(parsed).isEqualTo(expected);
        // the slice of "outer" must be a complete CBOR value, including its break byte
        assertThat(CBOR.readFrom(toCbor(outer))).isEqualTo(valueAt(expected, "/outer"));
        clearFields(parsed);
        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    public void parsedObjectsShareTheirKeys() {
        final byte[] cbor = toCbor(THING);
        final JsonObject first = CBOR.readFrom(cbor).asObject();
        final JsonObject second = CBOR.readFrom(cbor).asObject();

        assertThat(second.getKeys().get(1)).isSameAs(first.getKeys().get(1));
    }

    private static void assertNestedValuesEqual(final JsonObject actual, final JsonObject expected) {
        for (final String pointer : new String[]{"/attributes/location/room/floor", "/attributes/tags",
                "/features/thermostat/properties/modes", "/features/status/properties/dimensions",
                "/features/big", "/features/nothing"}) {
            assertThat(valueAt(actual, pointer)).as(pointer).isEqualTo(valueAt(expected, pointer));
        }
        assertThat(valueAt(actual, "/attributes/tags").asArray().get(2).map(JsonValue::asObject)
                .flatMap(o -> o.getValue("c"))).isEqualTo(valueAt(expected, "/attributes/tags").asArray().get(2)
                .map(JsonValue::asObject).flatMap(o -> o.getValue("c")));
    }

    private static JsonValue valueAt(final JsonObject jsonObject, final String pointer) {
        return jsonObject.getValue(JsonPointer.of(pointer)).orElseThrow(() -> new AssertionError(pointer));
    }

    private static byte[] toCbor(final JsonValue jsonValue) {
        try {
            return CBOR.toByteArray(jsonValue);
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
    }

    // simulates the garbage collector clearing the soft references to the fields under memory pressure
    private static void clearFields(final JsonObject jsonObject) {
        clearSoftReference(jsonObject, "fieldMap", "fieldsRef");
    }

    private static void clearValues(final JsonArray jsonArray) {
        clearSoftReference(jsonArray, "valueList", "valuesRef");
    }

    private static void clearSoftReference(final Object jsonValue, final String holderField, final String refField) {
        try {
            final Field holder = jsonValue.getClass().getDeclaredField(holderField);
            holder.setAccessible(true);
            final Object softReferenced = holder.get(jsonValue);
            final Field ref = softReferenced.getClass().getDeclaredField(refField);
            ref.setAccessible(true);
            assertThat(ref.get(softReferenced)).as("values parsed from CBOR are softly referenced")
                    .isInstanceOf(SoftReference.class);
            ref.set(softReferenced, new SoftReference<>(null));
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

}
