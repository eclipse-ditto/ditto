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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Converts the parameter to an uppercase hexadecimal string.
 */
public class BinaryToHexConverter {
    private static final char[] HEXCHARACTERS = "0123456789ABCDEF".toCharArray();

    /**
     * This class should not be instantiated.
     */
    private BinaryToHexConverter(){
        throw new AssertionError();
    }

    /**
     * Converts the parameter to an uppercase hexadecimal string.
     */
    public static String toHexString(byte[] array) throws IOException {
        return toHexString(new ByteArrayInputStream(array));
    }

    /**
     * Converts the parameter to an uppercase hexadecimal string.
     */
    public static String toHexString(ByteBuffer byteBuffer) throws IOException {
        return toHexString(ByteBufferInputStream.of(byteBuffer));
    }

    /**
     * Converts the parameter to an uppercase hexadecimal string.
     */
    public static String toHexString(InputStream inputStream) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder(inputStream.available());
        int currentByte = inputStream.read();
        while (currentByte >= 0){
            appendByte((byte) currentByte, stringBuilder);
            currentByte = inputStream.read();
        }
        return stringBuilder.toString();
    }

    /**
     * Converts the bytebuffer to an uppercase hexadecimal string.
     * In case of internal errors an error message is returned instead.
     */
    public static String tryToConvertToHexString(ByteBuffer byteBuffer){
        byteBuffer = byteBuffer.asReadOnlyBuffer(); // to avoid modifications especially to the position of the buffer
        try {
            return BinaryToHexConverter.toHexString(byteBuffer);
        } catch (IOException e) {
            return "Could not convert ByteBuffer to String due to " + e.getClass().getSimpleName();
        }
    }

    private static void appendByte(byte b, StringBuilder result){
        result.append(HEXCHARACTERS[(b & 0xF0) >> 4]);
        result.append(HEXCHARACTERS[b & 0x0F]);
    }
}
