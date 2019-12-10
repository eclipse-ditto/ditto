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
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.junit.Test;

public class ByteBufferInputStreamTest {

    private static byte TESTVALUE = 42;

    @Test public void readReadsFromBuffer() throws IOException {
        int length = 20;
        final ByteBuffer buffer = ByteBuffer.allocate(length);
        for (int i = 0; i < length; i++) {
            buffer.put((byte) i);
        }
        buffer.flip();
        final InputStream stream = ByteBufferInputStream.of(buffer);
        for (int i = 0; i < length; i++) {
            assertThat(stream.read()).isEqualTo(i);
        }
    }

    @Test public void readWithParametersWorks() throws IOException {
        int paddingFront = 7;
        int length = 21;
        int paddingBack = 13;
        int totalLength = paddingFront + length + paddingBack;

        final ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        for (int i = 0; i < totalLength; i++) {
            buffer.put(TESTVALUE);
        }
        buffer.flip();
        final InputStream stream = ByteBufferInputStream.of(buffer);

        byte[] bytes = new byte[totalLength];
        final int readCount = stream.read(bytes, paddingFront, length);

        assertThat(readCount).isEqualTo(length);

        for (int i = 0; i < paddingFront; i++) {
            assertThat(bytes[i]).isEqualTo((byte) 0);
        }
        for (int i = paddingFront; i < length; i++) {
            assertThat(bytes[i]).isEqualTo(TESTVALUE);
        }
        for (int i = paddingFront + length; i < totalLength; i++) {
            assertThat(bytes[i]).isEqualTo((byte) 0);
        }
    }

    @Test public void factoryMethodAccessesBackingArrayDirectlyIfAvailable(){
        final InputStream inputStream = ByteBufferInputStream.of(ByteBuffer.wrap(new byte[16]));
        assertThat(inputStream).isInstanceOf(ByteArrayInputStream.class);
    }

    @Test public void factoryMethodDoesNotDependOnBackingArrayBeingAvailable() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.put(TESTVALUE);
        buffer.flip();
        final InputStream stream = ByteBufferInputStream.of(buffer.asReadOnlyBuffer());
        assertThat(stream.read()).isEqualTo(TESTVALUE);
    }
}
