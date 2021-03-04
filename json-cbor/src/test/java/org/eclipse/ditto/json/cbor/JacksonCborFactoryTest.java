/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link org.eclipse.ditto.json.cbor.JacksonCborFactory}.
 */
@RunWith(Parameterized.class)
public final class JacksonCborFactoryTest {

    @Parameterized.Parameters
    public static List<String> testValue_STRINGS() {
        return Arrays.asList(
                "      {\n" +
                "        \"Image\": {\n" +
                "            \"Width\":  800,\n" +
                "            \"Height\": 600,\n" +
                "            \"Title\":  \"View from 15th Floor\",\n" +
                "            \"Thumbnail\": {\n" +
                "                \"Url\":    \"http://www.example.com/image/481989943\",\n" +
                "                \"Height\": 125,\n" +
                "                \"Width\":  100\n" +
                "            },\n" +
                "            \"Animated\" : false,\n" +
                "            \"IDs\": [116, 943, 234, 38793]\n" +
                "          }\n" +
                "      }",
                "42",
                "false",
                "\"someString\""
        );
    }

    @Parameterized.Parameter
    public String testObjectString;

    private JsonValue testValue;
    private byte[] testBytes;
    private JacksonCborFactory cborFactory;

    @Before
    public void init() throws IOException {
        testValue = JsonFactory.newValue(testObjectString);
        testBytes = CborTestUtils.serializeWithJackson(testValue);
        cborFactory = new JacksonCborFactory();
    }
    @Test
    public void readFromByteArrayWithoutOffset() {
        final JsonValue result = cborFactory.readFrom(testBytes);
        assertThat(result).isEqualTo(testValue);
    }

    @Test
    public void readFromByteArrayWithOffset() {
        final int paddingFront = 20;
        final int paddingBack = 42;
        final byte[] arrayWithOffsetAndLength = new byte[ paddingFront + testBytes.length + paddingBack];
        System.arraycopy(testBytes, 0, arrayWithOffsetAndLength, paddingFront-1, testBytes.length);
        final JsonValue result = cborFactory.readFrom(arrayWithOffsetAndLength, paddingFront - 1, testBytes.length);
        assertThat(result).isEqualTo(testValue);
    }

    @Test
    public void readFromByteBuffer() {
        final JsonValue result = cborFactory.readFrom(ByteBuffer.wrap(testBytes));
        assertThat(result).isEqualTo(testValue);
    }

    @Test
    public void readFromByteBufferWithInaccessibleArray() {
        // ReadOnlyByteBuffers throw an exception when trying to access the backing array directly.
        // This test also avoids accessing the backing array directly, which causes issues with ByteBuffers that represent slices of other Buffers.
        final ByteBuffer readOnlyBuffer = ByteBuffer.wrap(testBytes).asReadOnlyBuffer();
        final JsonValue result = cborFactory.readFrom(readOnlyBuffer);
        assertThat(result).isEqualTo(testValue);
    }

    @Test
    public void toBytebufferWorks() throws IOException {
        Assertions.assertThat(BinaryToHexConverter.toHexString(cborFactory.toByteBuffer(testValue)))
                .isEqualTo(CborTestUtils.serializeToHexString(testValue));
    }

    @Test
    public void writeToByteBufferWorks() throws IOException {
        final ByteBuffer allocate = ByteBuffer.allocate(512);
        cborFactory.writeToByteBuffer(testValue, allocate);
        allocate.flip();
        assertThat(BinaryToHexConverter.toHexString(allocate)).isEqualTo(CborTestUtils.serializeToHexString(testValue));
    }
}
