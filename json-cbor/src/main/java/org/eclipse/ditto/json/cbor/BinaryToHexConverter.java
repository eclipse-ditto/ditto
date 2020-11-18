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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Helper for converting binary data to an uppercase hexadecimal string for debugging and logging purposes.
 *
 * @since 1.2.1
 */
public final class BinaryToHexConverter {

    private static final char[] HEXCHARACTERS = "0123456789ABCDEF".toCharArray();

    private BinaryToHexConverter() {
        throw new AssertionError();
    }

    /**
     * Converts the passed {@code byteBuffer} to an uppercase hexadecimal string.
     * In case of internal errors an error message is returned instead.
     *
     * @param byteBuffer the ByteBuffer to convert to an uppercase hex string.
     * @return the converted uppercase hexadecimal string.
     */
    public static String createDebugMessageByTryingToConvertToHexString(final ByteBuffer byteBuffer) {
        // to avoid modifications especially to the position of the buffer
        final ByteBuffer readOnlyByteBuffer = byteBuffer.asReadOnlyBuffer();
        try {
            return BinaryToHexConverter.toHexString(readOnlyByteBuffer);
        } catch (final IOException e) {
            return "Could not convert ByteBuffer to String due to " + e.getClass().getSimpleName();
        }
    }

    /**
     * Converts the {@code array} to an uppercase hexadecimal string.
     *
     * @param array the byte array to convert.
     * @return the converted uppercase hexadecimal string.
     */
    public static String toHexString(final byte[] array) throws IOException {
        return toHexString(new ByteArrayInputStream(array));
    }

    /**
     * Converts the {@code byteBuffer} to an uppercase hexadecimal string.
     *
     * @param byteBuffer the byte buffer to convert.
     * @return the converted uppercase hexadecimal string.
     */
    public static String toHexString(final ByteBuffer byteBuffer) throws IOException {
        return toHexString(ByteBufferInputStream.of(byteBuffer));
    }

    /**
     * Converts the {@code inputStream} to an uppercase hexadecimal string.
     *
     * @param inputStream the input stream to convert.
     * @return the converted uppercase hexadecimal string.
     */
    public static String toHexString(final InputStream inputStream) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder(inputStream.available());
        int currentByte = inputStream.read();
        while (currentByte >= 0) {
            appendByte((byte) currentByte, stringBuilder);
            currentByte = inputStream.read();
        }
        return stringBuilder.toString();
    }

    private static void appendByte(final byte b, final StringBuilder result) {
        result.append(HEXCHARACTERS[(b & 0xF0) >> 4]);
        result.append(HEXCHARACTERS[b & 0x0F]);
    }
}
