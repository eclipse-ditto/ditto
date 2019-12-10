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
import java.util.LinkedHashMap;
import java.util.LinkedList;

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

    public static JsonValue readFrom(final byte[] bytes) throws IOException {
        return parseValue(jacksonCborFactory.createParser(bytes));
    }

    public static JsonValue readFrom(final byte[] bytes, int offset, int length) throws IOException {
        return parseValue(jacksonCborFactory.createParser(bytes, offset, length));
    }

    public static JsonValue readFrom(ByteBuffer byteBuffer) throws IOException {
        return parseValue(jacksonCborFactory.createParser(ByteBufferInputStream.of(byteBuffer)));
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

    private static void writeToOutputStream(JsonValue jsonValue, OutputStream outputStream) throws IOException {
        final SerializationContext serializationContext = new SerializationContext(jacksonCborFactory, outputStream);
        jsonValue.writeValue(serializationContext);
        serializationContext.close();
    }

    private static JsonValue parseValue(CBORParser parser) throws IOException {
        return parseValue(parser, parser.nextToken());
    }

    private static JsonValue parseValue(CBORParser parser, JsonToken currentToken) throws IOException {
        switch (currentToken) {
            case START_OBJECT:
                return parseObject(parser);
            case START_ARRAY:
                return parseArray(parser);
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

    private static JsonObject parseObject(CBORParser parser) throws IOException {
        final LinkedHashMap<String, JsonField> map = new LinkedHashMap<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME){
            final String key = parser.currentName();
            final JsonField jsonField = JsonField.newInstance(key, parseValue(parser));
            map.put(key, jsonField);
        }
        return ImmutableJsonObject.of(map);
        // TODO: trust that implementations in this package don't use passed maps to avoid copying them in JsonObject?
    }

    private static JsonArray parseArray(CBORParser parser) throws IOException {
        final LinkedList<JsonValue> list = new LinkedList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY){
            final JsonValue jsonValue = parseValue(parser, parser.currentToken());
            list.add(jsonValue);
        }
        return ImmutableJsonArray.of(list);
        // TODO: trust that implementations in this package don't use passed lists to avoid copying them in JsonArray?
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
