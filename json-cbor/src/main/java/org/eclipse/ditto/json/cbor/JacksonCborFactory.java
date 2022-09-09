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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.CborFactory;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonNumber;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

/**
 * Jackson backed implementation of {@link org.eclipse.ditto.json.CborFactory} which loads Jackson and Jackson-CBOR classes in order to
 * actually do the CBOR based serialization/deserialization.
 *
 * @since 1.2.1
 */
public final class JacksonCborFactory implements CborFactory {

    private static final CBORFactory JACKSON_CBOR_FACTORY = new CBORFactory();

    /**
     * Constructs the JacksonCborFactory - must be public as loaded via {@link java.util.ServiceLoader}.
     */
    public JacksonCborFactory() {
        super();
    }

    @Override
    public boolean isCborAvailable() {
        return true;
    }

    @Override
    public JsonValue readFrom(final byte[] bytes) {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        try {
            final CBORParser parser = JACKSON_CBOR_FACTORY.createParser(bytes);
            return parseValue(parser, byteBuffer);
        } catch (final IOException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw createJsonParseException(byteBuffer, e);
        }
    }

    @Override
    public JsonValue readFrom(final byte[] bytes, final int offset, final int length) {
        // ensure that buffers position is zero so that offsets determined by CBORParser map directly to positions in
        // this buffer.
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, offset, length).slice();
        try {
            final CBORParser parser = JACKSON_CBOR_FACTORY.createParser(bytes, offset, length);
            return parseValue(parser, byteBuffer);
        } catch (final IOException | IllegalArgumentException e) {
            throw createJsonParseException(byteBuffer, e);
        }
    }

    @Override
    public JsonValue readFrom(final ByteBuffer byteBuffer) {
        // ensure that buffers position is zero so that offsets determined by CBORParser map directly to positions in
        // this buffer.
        final ByteBuffer slicedByteBuffer = byteBuffer.slice();
        try {
            final CBORParser parser = JACKSON_CBOR_FACTORY.createParser(ByteBufferInputStream.of(slicedByteBuffer));
            return parseValue(parser, slicedByteBuffer);
        } catch (final IOException | IllegalArgumentException e) {
            throw createJsonParseException(slicedByteBuffer, e);
        }
    }

    @Override
    public byte[] toByteArray(final JsonValue jsonValue) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeToOutputStream(jsonValue, baos);
        return baos.toByteArray();
    }

    @Override
    public ByteBuffer toByteBuffer(final JsonValue jsonValue) throws IOException {
        return ByteBuffer.wrap(toByteArray(jsonValue));
    }

    @Override
    public void writeToByteBuffer(final JsonValue jsonValue, final ByteBuffer byteBuffer) throws IOException {
        final ByteBufferOutputStream byteBufferOutputStream = new ByteBufferOutputStream(byteBuffer);
        writeToOutputStream(jsonValue, byteBufferOutputStream);
    }

    @Override
    public byte[] createCborRepresentation(final Map<String, JsonField> jsonFieldMap, final int guessedSerializedSize)
            throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(guessedSerializedSize);

        try (final JacksonSerializationContext serializationContext = new JacksonSerializationContext(baos)) {
            writeStartObjectWithLength(serializationContext, jsonFieldMap.size());
            for (final JsonField jsonField : jsonFieldMap.values()) {
                jsonField.writeKeyAndValue(serializationContext);
            }
            serializationContext.getJacksonGenerator().writeEndObject();
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] createCborRepresentation(final List<JsonValue> list, final int guessedSerializedSize)
            throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(guessedSerializedSize);

        try (final JacksonSerializationContext serializationContext = new JacksonSerializationContext(baos)) {
            serializationContext.getJacksonGenerator().writeStartArray(list.size());
            for (final JsonValue jsonValue : list) {
                jsonValue.writeValue(serializationContext);
            }
            serializationContext.getJacksonGenerator().writeEndArray();
        }
        return baos.toByteArray();
    }

    private static void writeStartObjectWithLength(final JacksonSerializationContext serializationContext, int length)
            throws IOException {
            /*
            This is a workaround to ensure that length is encoded in CBOR-Objects.
            A proper API should be available in version 2.11. (2020-02)
            see: https://github.com/FasterXML/jackson-dataformats-binary/issues/3
             */
        final JsonGenerator jacksonGenerator = serializationContext.getJacksonGenerator();
        if (jacksonGenerator instanceof CBORGenerator) {
            CBORGenerator cborGenerator = (CBORGenerator) jacksonGenerator;
            cborGenerator.writeStartObject(length);
        } else {
            jacksonGenerator.writeStartObject();
        }
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
        final JacksonSerializationContext
                serializationContext = new JacksonSerializationContext(JACKSON_CBOR_FACTORY, outputStream);
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
                return JsonValue.of(parser.getValueAsString());
            case VALUE_NUMBER_INT:
                return getIntegerOrLong(parser.getLongValue());
            case VALUE_NUMBER_FLOAT:
                return JsonValue.of(parser.getDoubleValue());
            case VALUE_TRUE:
                return JsonValue.of(true);
            case VALUE_FALSE:
                return JsonValue.of(false);
            case VALUE_NULL:
                return JsonFactory.nullLiteral();

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
        return JsonFactory.createJsonObject(map, getBytesFromInputSource(startOffset, endOffset, byteBuffer));
    }

    private static JsonArray parseArray(final CBORParser parser, final ByteBuffer byteBuffer) throws IOException {
        final LinkedList<JsonValue> list = new LinkedList<>();
        final long startOffset = parser.getTokenLocation().getByteOffset();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            final JsonValue jsonValue = parseValue(parser, byteBuffer, parser.currentToken());
            list.add(jsonValue);
        }
        final long endOffset = parser.getTokenLocation().getByteOffset();
        return JsonFactory.createJsonArray(list, getBytesFromInputSource(startOffset, endOffset, byteBuffer));
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
     * Returns the appropriate {@link org.eclipse.ditto.json.JsonNumber} based on the actual value of the parameter.
     *
     * @param longValue The value to convert.
     * @return Either an {@code JSON int} or an {@code JSON long}.
     */
    private static JsonNumber getIntegerOrLong(final long longValue) {
        if (longValue <= Integer.MAX_VALUE && longValue >= Integer.MIN_VALUE) {
            return JsonValue.of((int) longValue);
        }
        return JsonValue.of(longValue);
    }
}
