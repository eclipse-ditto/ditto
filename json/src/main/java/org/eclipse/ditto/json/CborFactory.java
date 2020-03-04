/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

/**
 * This class can not be loaded by Java unless jackson-core and jackson-dataformats-cbor are both available on the
 * classpath.
 * If they are not available, interacting with this class will cause NoClassDefFoundErrors.
 * Use {@link org.eclipse.ditto.json.CborAvailabilityChecker#isCborAvailable()} in order to check for CBOR availability.
 *
 * @since 1.1.0
 */
public final class CborFactory {

    /**
     * Causes NoClassDefFoundErrors if loaded without Jackson support. See comment above class definition.
     */
    private static final CBORFactory JACKSON_CBOR_FACTORY = new CBORFactory();

    private CborFactory() {
        throw new AssertionError();
    }

    /**
     * Deserializes a {@code JsonValue} by parsing the passed {@code bytes} with CBOR.
     *
     * @param bytes the bytes to parse with CBOR.
     * @return the parsed JsonValue.
     */
    public static JsonValue readFrom(final byte[] bytes) {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        try {
            final CBORParser parser = JACKSON_CBOR_FACTORY.createParser(bytes);
            return parseValue(parser, byteBuffer);
        } catch (final IOException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw createJsonParseException(byteBuffer, e);
        }
    }

    /**
     * Deserializes a {@code JsonValue} by parsing the passed {@code bytes} with CBOR applying a {@code offset} and
     * {@code length}.
     *
     * @param bytes the bytes to parse with CBOR.
     * @param offset the offset where to start reading from.
     * @param length the lenght of how much bytes to read.
     * @return the parsed JsonValue.
     */
    public static JsonValue readFrom(final byte[] bytes, final int offset, final int length) {
        // ensure that buffers position is zero so that offsets determined by CBORParser map directly to positions in this buffer.
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, offset, length).slice();
        try {
            final CBORParser parser = JACKSON_CBOR_FACTORY.createParser(bytes, offset, length);
            return parseValue(parser, byteBuffer);
        } catch (final IOException | IllegalArgumentException e) {
            throw createJsonParseException(byteBuffer, e);
        }
    }

    /**
     * Deserializes a {@code JsonValue} by parsing the passed {@code byteBuffer} with CBOR.
     *
     * @param byteBuffer the ByteBuffer to parse with CBOR.
     * @return the parsed JsonValue.
     */
    public static JsonValue readFrom(final ByteBuffer byteBuffer) {
        // ensure that buffers position is zero so that offsets determined by CBORParser map directly to positions in this buffer.
        final ByteBuffer slicedByteBuffer = byteBuffer.slice();
        try {
            final CBORParser parser = JACKSON_CBOR_FACTORY.createParser(ByteBufferInputStream.of(slicedByteBuffer));
            return parseValue(parser, slicedByteBuffer);
        } catch (final IOException | IllegalArgumentException e) {
            throw createJsonParseException(slicedByteBuffer, e);
        }
    }

    /**
     * Serializes a CBOR byte array from the passed {@code jsonValue}.
     *
     * @param jsonValue the JsonValue to serialize into CBOR.
     * @return the CBOR bytes.
     * @throws IOException in case writing the value to the backing OutputStream causes an IOException.
     */
    public static byte[] toByteArray(final JsonValue jsonValue) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        writeToOutputStream(jsonValue, byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * Serializes a CBOR ByteBuffer from the passed {@code jsonValue}.
     *
     * @param jsonValue the JsonValue to serialize into CBOR.
     * @return the CBOR ByteBuffer.
     * @throws IOException in case writing the value to the backing OutputStream causes an IOException.
     */
    public static ByteBuffer toByteBuffer(final JsonValue jsonValue) throws IOException {
        return ByteBuffer.wrap(toByteArray(jsonValue));
    }

    /**
     * Serializes the passed {@code jsonValue} into the passed {@code byteBuffer} applying CBOR.
     *
     * @param jsonValue the JsonValue to serialize into CBOR.
     * @param byteBuffer the ByteBuffer to serialize into.
     * @throws IOException in case writing the value to the backing OutputStream causes an IOException.
     */
    public static void writeToByteBuffer(final JsonValue jsonValue, final ByteBuffer byteBuffer) throws IOException {
        final ByteBufferOutputStream byteBufferOutputStream = new ByteBufferOutputStream(byteBuffer);
        writeToOutputStream(jsonValue, byteBufferOutputStream);
    }

    private static JsonParseException createJsonParseException(final ByteBuffer byteBuffer, final Exception e) {
        return JsonParseException.newBuilder()
                .message(MessageFormat.format(
                        "Failed to parse CBOR value <{0}>",
                        BinaryToHexConverter.createDebugMessageByTryingToConvertToHexString(byteBuffer)))
                .cause(e)
                .build();
    }

    private static void writeToOutputStream(final JsonValue jsonValue, final OutputStream outputStream)
            throws IOException {
        final SerializationContext serializationContext = new SerializationContext(JACKSON_CBOR_FACTORY, outputStream);
        jsonValue.writeValue(serializationContext);
        serializationContext.close();
    }

    private static JsonValue parseValue(final CBORParser parser, final ByteBuffer byteBuffer) throws IOException {
        return parseValue(parser, byteBuffer, parser.nextToken());
    }

    private static JsonValue parseValue(final CBORParser parser, final ByteBuffer byteBuffer,
            @Nullable final JsonToken currentToken)
            throws IOException {
        if (currentToken == null) {
            throw new IOException("Unexpected end of input while expecting value.");
        }
        switch (currentToken) {
            case START_OBJECT:
                return parseObject(parser, byteBuffer);
            case START_ARRAY:
                return parseArray(parser, byteBuffer);
            case VALUE_STRING:
                return ImmutableJsonString.of(parser.getValueAsString());
            case VALUE_NUMBER_INT:
                return getIntegerOrLong(parser.getLongValue());
            case VALUE_NUMBER_FLOAT:
                return ImmutableJsonDouble.of(parser.getDoubleValue());
            case VALUE_TRUE:
                return ImmutableJsonBoolean.TRUE;
            case VALUE_FALSE:
                return ImmutableJsonBoolean.FALSE;
            case VALUE_NULL:
                return ImmutableJsonNull.getInstance();

            // Unexpected cases:
            case END_ARRAY:
            case FIELD_NAME:
            case VALUE_EMBEDDED_OBJECT:
            case END_OBJECT:
                throw new IOException(
                        "Encountered unexpected token " + parser.currentToken()
                                + " at position " + parser.getCurrentLocation()
                                + " while parsing CBOR value.");

                // Programming errors:
            default:
            case NOT_AVAILABLE:
                // This is a blocking parser that should never return this value.
                throw new IOException("CBORParser returned unexpected token type: " + parser.currentToken());

        }
    }

    private static JsonObject parseObject(final CBORParser parser, final ByteBuffer byteBuffer) throws IOException {
        final LinkedHashMap<String, JsonField> map = new LinkedHashMap<>();
        final long startOffset = parser.getTokenLocation().getByteOffset();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            final String key = parser.currentName();
            final JsonField jsonField = JsonField.newInstance(key, parseValue(parser, byteBuffer));
            map.put(key, jsonField);
        }
        final long endOffset = parser.getTokenLocation().getByteOffset();
        return ImmutableJsonObject.of(map, getBytesFromInputSource(startOffset, endOffset, byteBuffer));
    }

    private static JsonArray parseArray(final CBORParser parser, final ByteBuffer byteBuffer) throws IOException {
        final LinkedList<JsonValue> list = new LinkedList<>();
        final long startOffset = parser.getTokenLocation().getByteOffset();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            final JsonValue jsonValue = parseValue(parser, byteBuffer, parser.currentToken());
            list.add(jsonValue);
        }
        final long endOffset = parser.getTokenLocation().getByteOffset();
        return ImmutableJsonArray.of(list, getBytesFromInputSource(startOffset, endOffset, byteBuffer));
    }

    private static byte[] getBytesFromInputSource(final long startOffset, final long endOffset,
            final ByteBuffer byteBuffer) {
        assert endOffset > startOffset;
        assert endOffset < Integer.MAX_VALUE;

        final int length = (int) (endOffset - startOffset);
        final byte[] bytes = new byte[length];

        final ByteBuffer duplicate = byteBuffer.duplicate();
        duplicate.position((int) startOffset);
        duplicate.get(bytes);

        return bytes;
    }

    /**
     * Returns the appropriate {@link JsonNumber} based on the actual value of the parameter.
     *
     * @param longValue The value to convert.
     * @return Either an {@link ImmutableJsonInt} or an {@link ImmutableJsonLong}.
     */
    private static JsonNumber getIntegerOrLong(final long longValue) {
        if (longValue <= Integer.MAX_VALUE && longValue >= Integer.MIN_VALUE) {
            return ImmutableJsonInt.of((int) longValue);
        }
        return ImmutableJsonLong.of(longValue);
    }
}
