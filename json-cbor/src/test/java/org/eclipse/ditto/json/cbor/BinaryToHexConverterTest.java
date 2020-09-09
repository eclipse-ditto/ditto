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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Test;

public final class BinaryToHexConverterTest {

    private static final byte[] TEST_VECTOR = new byte[]{
            b(0x00), b(0x10), b(0x58), b(0xC0), b(0xFF), b(0xEE), b(0x00), b(0xBE), b(0xEF)
    };
    private static final String EXPECTED_STRING = "001058C0FFEE00BEEF";

    private static byte b(final int i) {
        return (byte) i;
    }

    @Test
    public void hexStringFromByteArrayWorks() throws IOException {
        assertThat(BinaryToHexConverter.toHexString(TEST_VECTOR)).isEqualTo(EXPECTED_STRING);
    }

    @Test
    public void hexStringFromByteBufferWorks() throws IOException {
        assertThat(BinaryToHexConverter.toHexString(ByteBuffer.wrap(TEST_VECTOR))).isEqualTo(EXPECTED_STRING);
    }

    @Test
    public void hexStringFromInputStream() throws IOException {
        assertThat(BinaryToHexConverter.toHexString(new ByteArrayInputStream(TEST_VECTOR))).isEqualTo(EXPECTED_STRING);
    }
}
