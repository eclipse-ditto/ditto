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

import java.io.IOException;
import java.nio.ByteBuffer;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.SerializationContext;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

final class CborTestUtils {

    private CborTestUtils() {
        throw new AssertionError();
    }

    static byte[] serializeWithJackson(final JsonValue jsonValue) throws IOException {
        final JsonFactory jacksonFactory = new CBORFactory();
        final ByteBuffer byteBuffer = ByteBuffer.allocate(2048);
        final SerializationContext serializationContext =
                new JacksonSerializationContext(jacksonFactory, new ByteBufferOutputStream(byteBuffer));
        jsonValue.writeValue(serializationContext);
        serializationContext.close();
        byteBuffer.flip();

        return sizedByteArrayFromByteBuffer(byteBuffer);
    }

    static String serializeToHexString(final JsonValue jsonValue) throws IOException {
        return BinaryToHexConverter.toHexString(serializeWithJackson(jsonValue));
    }

    private static byte[] sizedByteArrayFromByteBuffer(final ByteBuffer byteBuffer) {
        final byte[] sizedArray = new byte[byteBuffer.remaining()];
        byteBuffer.get(sizedArray, 0, sizedArray.length);
        return sizedArray;
    }
}
