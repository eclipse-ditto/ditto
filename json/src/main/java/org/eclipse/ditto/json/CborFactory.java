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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * This interface abstracts serialization/deserialization via CBOR.
 *
 * Registration of an implementation is done via a {@link java.util.ServiceLoader} (putting a file named
 * {@code org.eclipse.ditto.json.CborFactory} inside the {@code META-INF/services} directory of the providing module.
 * Use {@link #isCborAvailable()} in order to check for CBOR availability.
 *
 * <p>
 * <b>This is a Ditto internal class which is not intended for re-use.</b>
 * It therefore is not treated as API which is held binary compatible to previous versions.
 * </p>
 *
 * @since 1.2.1
 */
public interface CborFactory {

    /**
     * Determines whether the libraries providing CBOR serializations are available (classes can be loaded).
     *
     * @return {@code true} when CBOR is available and can be used for serialization.
     */
    boolean isCborAvailable();

    /**
     * Deserializes a {@code JsonValue} by parsing the passed {@code bytes} with CBOR.
     *
     * @param bytes the bytes to parse with CBOR.
     * @return the parsed JsonValue.
     * @throws JsonParseException if the content of {@code bytes} cannot be deserialized to a {@link JsonValue}.
     */
    JsonValue readFrom(byte[] bytes);

    /**
     * Deserializes a {@code JsonValue} by parsing the passed {@code bytes} with CBOR applying a {@code offset} and
     * {@code length}.
     *
     * @param bytes the bytes to parse with CBOR.
     * @param offset the offset where to start reading from.
     * @param length the length of how much bytes to read.
     * @return the parsed JsonValue.
     * @throws JsonParseException if the content of {@code bytes} cannot be deserialized to a {@link JsonValue}.
     */
    JsonValue readFrom(byte[] bytes, int offset, int length);

    /**
     * Deserializes a {@code JsonValue} by parsing the passed {@code byteBuffer} with CBOR.
     *
     * @param byteBuffer the ByteBuffer to parse with CBOR.
     * @return the parsed JsonValue.
     * @throws JsonParseException if the content of {@code byteBuffer} cannot be deserialized to a {@link JsonValue}.
     */
    JsonValue readFrom(ByteBuffer byteBuffer);

    /**
     * Serializes a CBOR byte array from the passed {@code jsonValue}.
     *
     * @param jsonValue the JsonValue to serialize into CBOR.
     * @return the CBOR bytes.
     * @throws IOException in case writing the value to the backing OutputStream causes an IOException.
     */
    byte[] toByteArray(JsonValue jsonValue) throws IOException;

    /**
     * Serializes a CBOR ByteBuffer from the passed {@code jsonValue}.
     *
     * @param jsonValue the JsonValue to serialize into CBOR.
     * @return the CBOR ByteBuffer.
     * @throws IOException in case writing the value to the backing OutputStream causes an IOException.
     */
    ByteBuffer toByteBuffer(JsonValue jsonValue) throws IOException;

    /**
     * Serializes the passed {@code jsonValue} into the passed {@code byteBuffer} applying CBOR.
     *
     * @param jsonValue the JsonValue to serialize into CBOR.
     * @param byteBuffer the ByteBuffer to serialize into.
     * @throws IOException in case writing the value to the backing OutputStream causes an IOException.
     */
    void writeToByteBuffer(JsonValue jsonValue, ByteBuffer byteBuffer) throws IOException;

    /**
     * Creates the CBOR representation of the passed JSON fieldMap and the estimated required serialized size of it.
     *
     * @param jsonFieldMap the map of JsonFields to create the CBOR representation for.
     * @param guessedSerializedSize the estimated serialized size of the CBOR structure.
     * @return the CBOR representation as bytes.
     * @throws IOException in case writing the value to the backing OutputStream causes an IOException.
     */
    byte[] createCborRepresentation(Map<String, JsonField> jsonFieldMap, int guessedSerializedSize) throws IOException;

    /**
     * Creates the CBOR representation of the passed JSON value list and the estimated required serialized size of it.
     *
     * @param list the list of JsonValues to create the CBOR representation for.
     * @param guessedSerializedSize the estimated serialized size of the CBOR structure.
     * @return the CBOR representation as bytes.
     * @throws IOException in case writing the value to the backing OutputStream causes an IOException.
     */
    byte[] createCborRepresentation(List<JsonValue> list, int guessedSerializedSize) throws IOException;
}
