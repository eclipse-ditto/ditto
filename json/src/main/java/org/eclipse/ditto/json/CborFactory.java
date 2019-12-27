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

public final class CborFactory {

    private static CBORFactory jacksonCborFactory = new CBORFactory();
    /*
     * This utility class is not meant to be instantiated.
     */
    private CborFactory(){
        throw new AssertionError();
    }

    public static JsonValue readFrom(final byte[] bytes) {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        try{
            final CBORParser parser = jacksonCborFactory.createParser(bytes);
            return parseValue(parser, byteBuffer);
        } catch (IOException | IllegalArgumentException | ArrayIndexOutOfBoundsException e){
            throw createJsonParseException(byteBuffer, e);
        }
    }

    public static JsonValue readFrom(final byte[] bytes, int offset, int length) {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, offset, length).slice(); // ensure that buffers position is zero so that offsets determined by CBORParser map directly to positions in this buffer.
        try{
            final CBORParser parser = jacksonCborFactory.createParser(bytes, offset, length);
            return parseValue(parser, byteBuffer);
        } catch (IOException | IllegalArgumentException e){
            throw createJsonParseException(byteBuffer, e);
        }
    }

    public static JsonValue readFrom(ByteBuffer byteBuffer) {
        // TODO: move / duplicate the bytebuffer array shortcut here?
        byteBuffer = byteBuffer.slice(); // ensure that buffers position is zero so that offsets determined by CBORParser map directly to positions in this buffer.
        try{
            final CBORParser parser = jacksonCborFactory.createParser(ByteBufferInputStream.of(byteBuffer));
            return parseValue(parser, byteBuffer);
        } catch (IOException | IllegalArgumentException e){
            throw createJsonParseException(byteBuffer, e);
        }
    }

    public static byte[] toByteArray(JsonValue jsonValue) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        writeToOutputStream(jsonValue, byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    public static ByteBuffer toByteBuffer(JsonValue jsonValue) throws IOException {
        return ByteBuffer.wrap(toByteArray(jsonValue));
    }

    public static void writeToByteBuffer(JsonValue jsonValue, ByteBuffer byteBuffer) throws IOException {
        final ByteBufferOutputStream byteBufferOutputStream = new ByteBufferOutputStream(byteBuffer);
        writeToOutputStream(jsonValue, byteBufferOutputStream);
    }

    private static JsonParseException createJsonParseException(ByteBuffer byteBuffer, Exception e){
        return JsonParseException.newBuilder()
                .message(MessageFormat.format(
                        "Failed to parse CBOR value ''{0}''",
                        BinaryToHexConverter.tryToConvertToHexString(byteBuffer)))
                .cause(e)
                .build();
    }

    private static void writeToOutputStream(JsonValue jsonValue, OutputStream outputStream) throws IOException {
        final SerializationContext serializationContext = new SerializationContext(jacksonCborFactory, outputStream);
        jsonValue.writeValue(serializationContext);
        serializationContext.close();
    }

    private static JsonValue parseValue(CBORParser parser, ByteBuffer byteBuffer) throws IOException {
        return parseValue(parser, byteBuffer, parser.nextToken());
    }

    private static JsonValue parseValue(CBORParser parser, ByteBuffer byteBuffer, @Nullable JsonToken currentToken) throws IOException {
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

    private static JsonObject parseObject(CBORParser parser, ByteBuffer byteBuffer) throws IOException {
        final LinkedHashMap<String, JsonField> map = new LinkedHashMap<>();
        final long startOffset = parser.getTokenLocation().getByteOffset();
        while (parser.nextToken() == JsonToken.FIELD_NAME){
            final String key = parser.currentName();
            final JsonField jsonField = JsonField.newInstance(key, parseValue(parser, byteBuffer));
            map.put(key, jsonField);
        }
        final long endOffset = parser.getTokenLocation().getByteOffset();
        return ImmutableJsonObject.of(map, getBytesFromInputSource(startOffset, endOffset, byteBuffer));
        // TODO: trust that implementations in this package don't use passed maps to avoid copying them in JsonObject?
    }

    private static JsonArray parseArray(CBORParser parser, ByteBuffer byteBuffer) throws IOException {
        final LinkedList<JsonValue> list = new LinkedList<>();
        final long startOffset = parser.getTokenLocation().getByteOffset();
        while (parser.nextToken() != JsonToken.END_ARRAY){
            final JsonValue jsonValue = parseValue(parser, byteBuffer, parser.currentToken());
            list.add(jsonValue);
        }
        final long endOffset = parser.getTokenLocation().getByteOffset() ;
        return ImmutableJsonArray.of(list, getBytesFromInputSource(startOffset, endOffset, byteBuffer));
        // TODO: trust that implementations in this package don't use passed lists to avoid copying them in JsonArray?
    }

    private static byte[] getBytesFromInputSource(final long startOffset, final long endOffset, final ByteBuffer byteBuffer){
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
     * @param longValue The value to convert.
     * @return Either an {@link ImmutableJsonInt} or an {@link ImmutableJsonLong}.
     */
    private static JsonNumber getIntegerOrLong(long longValue){
        if (longValue <= Integer.MAX_VALUE && longValue >= Integer.MIN_VALUE){
            return ImmutableJsonInt.of((int) longValue);
        }
        return ImmutableJsonLong.of(longValue);
    }
}
