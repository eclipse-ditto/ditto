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
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
import org.eclipse.ditto.json.SerializationContext;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonLocation;
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

    // The passed bytes are copied once - the caller may reuse them, e.g. a pooled buffer - and the parsed objects and
    // arrays reference slices of that copy as their CBOR representation, which lets them release their fields under
    // memory pressure.

    @Override
    public JsonValue readFrom(final byte[] bytes) {
        return parseOwnedBytes(bytes.clone(), 0, bytes.length);
    }

    @Override
    public JsonValue readFrom(final byte[] bytes, final int offset, final int length) {
        return parseOwnedBytes(Arrays.copyOfRange(bytes, offset, offset + length), 0, length);
    }

    @Override
    public JsonValue readFrom(final ByteBuffer byteBuffer) {
        final ByteBuffer slicedByteBuffer = byteBuffer.slice();
        final byte[] bytes = new byte[slicedByteBuffer.remaining()];
        slicedByteBuffer.get(bytes);
        return parseOwnedBytes(bytes, 0, bytes.length);
    }

    @Override
    public JsonValue readFromOwnedBytes(final byte[] bytes, final int offset, final int length) {
        return parseOwnedBytes(bytes, offset, length);
    }

    private static JsonValue parseOwnedBytes(final byte[] bytes, final int offset, final int length) {
        try {
            final CBORParser parser = JACKSON_CBOR_FACTORY.createParser(bytes, offset, length);
            final ParseContext context = new ParseContext(parser, bytes);
            return parseValue(context, parser.nextToken());
        } catch (final IOException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw createJsonParseException(ByteBuffer.wrap(bytes, offset, length).slice(), e);
        }
    }

    @Override
    public byte[] toByteArray(final JsonValue jsonValue) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(determineSize(jsonValue));
        writeToOutputStream(jsonValue, baos);
        return baos.toByteArray();
    }

    @Override
    public ByteBuffer toByteBuffer(final JsonValue jsonValue) throws IOException {

        final int capacity = determineSize(jsonValue);
        final ByteBuffer buffer = ByteBuffer.allocate(capacity);
        final ByteBufferOutputStream outputStream = new ByteBufferOutputStream(buffer);
        try {
            writeToOutputStream(jsonValue, outputStream);
            buffer.flip();
            return buffer;
        } catch (final BufferOverflowException bufferOverflowException) {
            System.err.println("Buffer overflow when serializing CBOR value with determined size " +
                    "<" + capacity + ">: " + jsonValue);
            // retry with a bigger buffer
            final int capacityAfterOverflow = capacity * 2;
            final ByteBuffer bufferAfterOverflow = ByteBuffer.allocate(capacityAfterOverflow);
            final ByteBufferOutputStream outputStreamAfterOverflow = new ByteBufferOutputStream(bufferAfterOverflow);
            writeToOutputStream(jsonValue, outputStreamAfterOverflow);
            bufferAfterOverflow.flip();
            return bufferAfterOverflow;
        }
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
            writeObject(jsonFieldMap, serializationContext);
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] createCborRepresentation(final List<JsonValue> list, final int guessedSerializedSize)
            throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(guessedSerializedSize);

        try (final JacksonSerializationContext serializationContext = new JacksonSerializationContext(baos)) {
            writeArray(list, serializationContext);
        }
        return baos.toByteArray();
    }

    @Override
    public void writeObject(final Map<String, JsonField> jsonFieldMap,
            final SerializationContext serializationContext) throws IOException {
        if (serializationContext instanceof JacksonSerializationContext) {
            final JacksonSerializationContext jacksonSerializationContext =
                    (JacksonSerializationContext) serializationContext;
            writeStartObjectWithLength(jacksonSerializationContext, jsonFieldMap.size());
            for (final JsonField jsonField : jsonFieldMap.values()) {
                jsonField.writeKeyAndValue(jacksonSerializationContext);
            }
            jacksonSerializationContext.getJacksonGenerator().writeEndObject();
        } else {
            CborFactory.super.writeObject(jsonFieldMap, serializationContext);
        }
    }

    @Override
    public void writeArray(final List<JsonValue> list, final SerializationContext serializationContext)
            throws IOException {
        if (serializationContext instanceof JacksonSerializationContext) {
            final JsonGenerator jacksonGenerator =
                    ((JacksonSerializationContext) serializationContext).getJacksonGenerator();
            jacksonGenerator.writeStartArray(list.size());
            for (final JsonValue jsonValue : list) {
                jsonValue.writeValue(serializationContext);
            }
            jacksonGenerator.writeEndArray();
        } else {
            CborFactory.super.writeArray(list, serializationContext);
        }
    }

    private static int determineSize(final JsonValue jsonValue) {
        return (int) jsonValue.getUpperBoundForStringSize();
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

    private static JsonValue parseValue(final ParseContext context, @Nullable final JsonToken currentToken)
            throws IOException {
        final CBORParser parser = context.parser;
        if (currentToken == null) {
            throw new IOException("Unexpected end of input while expecting value.");
        }
        switch (currentToken) {
            case START_OBJECT:
                return parseObject(context);
            case START_ARRAY:
                return parseArray(context);
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
                                + " at position " + parser.currentTokenLocation()
                                + " while parsing CBOR value.");

            // Programming errors:
            case NOT_AVAILABLE:
            default:
                // This is a blocking parser that should never return this value.
                throw new IOException("CBORParser returned unexpected token type: " + parser.currentToken());

        }
    }

    private static JsonObject parseObject(final ParseContext context) throws IOException {
        final CBORParser parser = context.parser;
        final int start = context.offsetOf(parser.currentTokenLocation());
        final int level = context.depth++;
        JsonField[] fields = context.fieldBuffer(level);
        int size = 0;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            final String key = parser.currentName();
            final JsonField jsonField = JsonField.newInstance(key, parseValue(context, parser.nextToken()));
            if (size == fields.length) {
                fields = context.growFieldBuffer(level);
            }
            fields[size++] = jsonField;
        }
        context.depth = level;
        final int end = context.offsetOf(parser.currentLocation());
        return JsonFactory.createJsonObjectFromCborSlice(Arrays.copyOf(fields, size), context.bytes, start,
                end - start);
    }

    private static JsonArray parseArray(final ParseContext context) throws IOException {
        final CBORParser parser = context.parser;
        final int start = context.offsetOf(parser.currentTokenLocation());
        final int level = context.depth++;
        JsonValue[] values = context.valueBuffer(level);
        int size = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            final JsonValue jsonValue = parseValue(context, parser.currentToken());
            if (size == values.length) {
                values = context.growValueBuffer(level);
            }
            values[size++] = jsonValue;
        }
        context.depth = level;
        final int end = context.offsetOf(parser.currentLocation());
        return JsonFactory.createJsonArrayFromCborSlice(Arrays.copyOf(values, size), context.bytes, start,
                end - start);
    }

    /**
     * State of parsing one CBOR message: the parsed bytes and per nesting level a reusable buffer for the fields or
     * values of the object or array being parsed on that level, so only exactly sized arrays are allocated per value.
     */
    private static final class ParseContext {

        private final CBORParser parser;
        private final byte[] bytes;
        private JsonField[][] fieldBuffers;
        private JsonValue[][] valueBuffers;
        private int depth;

        private ParseContext(final CBORParser parser, final byte[] bytes) {
            this.parser = parser;
            this.bytes = bytes;
            fieldBuffers = new JsonField[8][];
            valueBuffers = new JsonValue[8][];
            depth = 0;
        }

        private int offsetOf(final JsonLocation location) {
            // the parser reports absolute offsets in the parsed array, also when created with an offset
            return (int) location.getByteOffset();
        }

        private JsonField[] fieldBuffer(final int level) {
            if (level >= fieldBuffers.length) {
                fieldBuffers = Arrays.copyOf(fieldBuffers, level * 2);
            }
            JsonField[] buffer = fieldBuffers[level];
            if (null == buffer) {
                buffer = new JsonField[16];
                fieldBuffers[level] = buffer;
            }
            return buffer;
        }

        private JsonField[] growFieldBuffer(final int level) {
            final JsonField[] grown = Arrays.copyOf(fieldBuffers[level], fieldBuffers[level].length * 2);
            fieldBuffers[level] = grown;
            return grown;
        }

        private JsonValue[] valueBuffer(final int level) {
            if (level >= valueBuffers.length) {
                valueBuffers = Arrays.copyOf(valueBuffers, level * 2);
            }
            JsonValue[] buffer = valueBuffers[level];
            if (null == buffer) {
                buffer = new JsonValue[16];
                valueBuffers[level] = buffer;
            }
            return buffer;
        }

        private JsonValue[] growValueBuffer(final int level) {
            final JsonValue[] grown = Arrays.copyOf(valueBuffers[level], valueBuffers[level].length * 2);
            valueBuffers[level] = grown;
            return grown;
        }
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
