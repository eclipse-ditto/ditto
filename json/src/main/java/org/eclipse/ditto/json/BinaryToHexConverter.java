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
            byteToHexChars((byte) currentByte, stringBuilder);
            currentByte = inputStream.read();
        }
        return stringBuilder.toString();
    }

    private static void byteToHexChars(byte b, StringBuilder result){
        result.append(HEXCHARACTERS[(b & 0xF0) >> 4]);
        result.append(HEXCHARACTERS[b & 0x0F]);
    }
}
