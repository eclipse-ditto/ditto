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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

class CborTestUtils {

    static byte[] serializeWithJackson(JsonValue jsonValue) throws IOException {
        JsonFactory jacksonFactory = new CBORFactory();
        final ByteBuffer byteBuffer = ByteBuffer.allocate(2048);
        final SerializationContext serializationContext =
                new SerializationContext(jacksonFactory, new ByteBufferOutputStream(byteBuffer));
        jsonValue.writeValue(serializationContext);
        serializationContext.close();
        byteBuffer.flip(); // TODO is this needed?

        return sizedByteArrayFromByteBuffer(byteBuffer);
    }

    static String serializeToHexString(JsonValue jsonValue) throws IOException {
        return BinaryToHexConverter.toHexString(serializeWithJackson(jsonValue));
    }

    private static byte[] sizedByteArrayFromByteBuffer(ByteBuffer byteBuffer){
        byte[] sizedArray = new byte[byteBuffer.remaining()];
        byteBuffer.get(sizedArray, 0, sizedArray.length);
        return sizedArray;
    }
}
