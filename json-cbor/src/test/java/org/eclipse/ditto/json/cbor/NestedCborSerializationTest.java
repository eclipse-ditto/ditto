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

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

/**
 * Tests that nested JSON values are written directly into the CBOR output of the value containing them.
 */
public final class NestedCborSerializationTest {

    private final JacksonCborFactory cborFactory = new JacksonCborFactory();

    @Test
    public void writesNestedObjectsAndArrays() throws IOException {
        final JsonObject underTest = JsonObject.newBuilder()
                .set("a", JsonArray.of(JsonValue.of(1), JsonObject.newBuilder().set("b", true).build()))
                .build();

        assertThat(CborTestUtils.serializeToHexString(underTest)).isEqualToIgnoringCase(
                "A1" + "6161" // {"a":
                        + "82" + "01" // [1,
                        + "A1" + "6162" + "F5"); // {"b":true}]}
    }

    @Test
    public void writesEmptyNestedObjectsAndArrays() throws IOException {
        final JsonObject underTest = JsonObject.newBuilder()
                .set("e", JsonObject.empty())
                .set("f", JsonArray.empty())
                .build();

        assertThat(CborTestUtils.serializeToHexString(underTest)).isEqualToIgnoringCase(
                "A2" + "6165" + "A0" + "6166" + "80"); // {"e":{},"f":[]}
    }

    @Test
    public void writesNestedValuesWithCachedCborRepresentation() throws IOException {
        // parsed from CBOR, so it carries its CBOR representation, which is written as cached element
        final JsonValue parsed = cborFactory.readFrom(cborFactory.toByteArray(JsonObject.newBuilder()
                .set("b", true)
                .build()));
        final JsonObject underTest = JsonObject.newBuilder()
                .set("x", parsed)
                .set("y", 2)
                .build();

        assertThat(CborTestUtils.serializeToHexString(underTest)).isEqualToIgnoringCase(
                "A2" + "6178" + "A1" + "6162" + "F5" + "6179" + "02"); // {"x":{"b":true},"y":2}
    }

    @Test
    public void deeplyNestedValueSurvivesRoundTrip() throws IOException {
        final JsonObject underTest = deeplyNested(6);

        final JsonValue parsed = cborFactory.readFrom(cborFactory.toByteArray(underTest));

        assertThat(parsed).isEqualTo(underTest);
        assertThat(cborFactory.toByteArray(parsed)).isEqualTo(cborFactory.toByteArray(underTest));
    }

    @Test
    public void serializationCachesNoCborRepresentationInNestedValues() throws Exception {
        final JsonObject nested = JsonObject.newBuilder().set("b", true).build();
        final JsonArray nestedArray = JsonArray.of(JsonValue.of(1), nested);
        final JsonObject underTest = JsonObject.newBuilder().set("a", nestedArray).build();

        cborFactory.writeToByteBuffer(underTest, ByteBuffer.allocate(1024));

        for (final Object jsonValue : new Object[]{underTest, nested}) {
            assertThat(getInternalField(jsonValue, "fieldMap", "cborObjectRepresentation")).isNull();
            assertThat(getInternalField(jsonValue, "fieldMap", "fieldsRef")).isNotInstanceOf(SoftReference.class);
        }
        assertThat(getInternalField(nestedArray, "valueList", "cborArrayRepresentation")).isNull();
        assertThat(getInternalField(nestedArray, "valueList", "valuesRef")).isNotInstanceOf(SoftReference.class);
    }

    @Test
    public void concurrentSerializationOfSameValueWritesSameBytes() throws Exception {
        final JsonObject underTest = deeplyNested(5);
        final String expected = CborTestUtils.serializeToHexString(deeplyNested(5));
        final ExecutorService executorService = Executors.newFixedThreadPool(8);
        try {
            final List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                final Callable<String> serialization = () -> CborTestUtils.serializeToHexString(underTest);
                results.add(executorService.submit(serialization));
            }
            for (final Future<String> result : results) {
                assertThat(result.get()).isEqualTo(expected);
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    public void parsingKeepsNoCborRepresentationInNestedValues() throws Exception {
        final JsonObject parsed = cborFactory.readFrom(cborFactory.toByteArray(deeplyNested(3))).asObject();
        final JsonObject child = parsed.getValue("child").map(JsonValue::asObject).orElseThrow(AssertionError::new);
        final JsonArray siblings =
                parsed.getValue("siblings").map(JsonValue::asArray).orElseThrow(AssertionError::new);

        for (final Object jsonValue : new Object[]{parsed, child}) {
            assertThat(getInternalField(jsonValue, "fieldMap", "cborObjectRepresentation")).isNull();
            assertThat(getInternalField(jsonValue, "fieldMap", "fieldsRef")).isNotInstanceOf(SoftReference.class);
        }
        assertThat(getInternalField(siblings, "valueList", "cborArrayRepresentation")).isNull();
        assertThat(getInternalField(siblings, "valueList", "valuesRef")).isNotInstanceOf(SoftReference.class);
    }

    @Test
    public void parsingDuplicateKeysKeepsLastValue() {
        // {"a":1,"a":2}
        final byte[] cbor = {(byte) 0xA2, 0x61, 0x61, 0x01, 0x61, 0x61, 0x02};

        assertThat(cborFactory.readFrom(cbor)).isEqualTo(JsonObject.newBuilder().set("a", 2).build());
    }

    private static JsonObject deeplyNested(final int depth) {
        JsonObject result = JsonObject.newBuilder()
                .set("value", 21.5)
                .set("list", JsonArray.of(JsonValue.of("a"), JsonValue.of(false), JsonValue.nullLiteral()))
                .build();
        for (int i = 0; i < depth; i++) {
            result = JsonObject.newBuilder()
                    .set("level", i)
                    .set("child", result)
                    .set("siblings", JsonArray.of(JsonObject.newBuilder().set("i", i).build(),
                            JsonArray.of(JsonValue.of(i))))
                    .build();
        }
        return result;
    }

    private static Object getInternalField(final Object jsonValue, final String containerFieldName,
            final String fieldName) throws Exception {
        final Field containerField = jsonValue.getClass().getDeclaredField(containerFieldName);
        containerField.setAccessible(true);
        final Object container = containerField.get(jsonValue);
        final Field target = container.getClass().getDeclaredField(fieldName);
        target.setAccessible(true);
        return target.get(container);
    }

}
