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

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Test;

public class BinaryToHexConverterTest {

    private static byte[] testVector = new byte[]{
            b(0x00), b(0x10), b(0x58), b(0xC0), b(0xFF), b(0xEE), b(0x00), b(0xBE), b(0xEF)
    };
    private static String expectedString = "001058C0FFEE00BEEF";

    private static byte b(int i){
        return (byte) i;
    }

    @Test
    public void HexStringFromByteArrayWorks() throws IOException {
        assertThat(BinaryToHexConverter.toHexString(testVector)).isEqualTo(expectedString);
    }

    @Test
    public void HexStringFromByteBufferWorks() throws IOException {
        assertThat(BinaryToHexConverter.toHexString(ByteBuffer.wrap(testVector))).isEqualTo(expectedString);
    }

    @Test
    public void HexStringFromInputStream() throws IOException {
        assertThat(BinaryToHexConverter.toHexString(new ByteArrayInputStream(testVector))).isEqualTo(expectedString);
    }
}
