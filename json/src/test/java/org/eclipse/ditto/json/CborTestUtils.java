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
import java.math.BigInteger;
import java.nio.ByteBuffer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

class CborTestUtils {

    static byte[] serializeWithJackson(JsonValue jsonValue) throws IOException {
        JsonFactory jacksonFactory = new CBORFactory();
        final ByteBuffer byteBuffer = ByteBuffer.allocate(128);
        final SerializationContext serializationContext =
                new SerializationContext(jacksonFactory, new ByteBufferOutputStream(byteBuffer));
        jsonValue.writeValue(serializationContext);
        serializationContext.close();
        return byteBuffer.array();
    }

    static String byteArrayToHexString(byte[] array){
        return new BigInteger(array).toString(16);
    }

    static String serializeToHexString(JsonValue jsonValue) throws IOException {
        return byteArrayToHexString(serializeWithJackson(jsonValue));
    }

    /**
     * Converts an integer value to its representation in bytes used by CBOR.
     * Follows RFC 7049.
     * @see <a href="https://tools.ietf.org/html/rfc7049#section-2.1">RFC 7049</a>
     * @param longValue The integer to convert.
     * @return The byte representation.
     * @throws IOException if conversion failed (should not happen)
     */
    static byte[] longToBytes(long longValue) throws IOException {
        byte type = 0;
        if (longValue < 0){
            longValue = -1 - longValue;
            type = 1 << 5;
        }

        if (longValue > 0 && longValue < 23){
            return BigInteger.valueOf(longValue).toByteArray();
        }
        if (Math.abs(longValue) < (2^8)){
            ByteBuffer bb = ByteBuffer.allocate(1+1);
            bb.put((byte) (type & 24));
            bb.put((byte) longValue);
            return bb.array();
        }
        if (Math.abs(longValue) < (2^16)){
            ByteBuffer bb = ByteBuffer.allocate(1+2);
            bb.put((byte) (type & 25));
            bb.putShort((short) longValue);
            return bb.array();
        }
        if (Math.abs(longValue) < (2^32)){
            ByteBuffer bb = ByteBuffer.allocate(1+4);
            bb.put((byte) (type & 26));
            bb.putInt((int) longValue);
            return bb.array();
        }
        if (Math.abs(longValue) < (2^64)){
            ByteBuffer bb = ByteBuffer.allocate(1+8);
            bb.put((byte) (type & 27));
            bb.putLong(longValue);
            return bb.array();
        }
        throw new IOException("this case can never occur");
    }
}
