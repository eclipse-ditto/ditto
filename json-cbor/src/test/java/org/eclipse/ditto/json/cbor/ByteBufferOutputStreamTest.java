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

import org.assertj.core.api.Assertions;
import org.junit.Test;

public final class ByteBufferOutputStreamTest {

    @Test
    public void writesThroughToBuffer() throws IOException {
        final int expectedCount = 11;
        final ByteBuffer buffer = ByteBuffer.allocate(expectedCount);
        final ByteBufferOutputStream outputStream = new ByteBufferOutputStream(buffer);


        for (int i = 0; i < 5; i++) {
            outputStream.write(i);
        }
        outputStream.write(new byte[]{7,8,9});
        outputStream.write(new byte[]{10,11,12,13,14}, 1, 3);

        Assertions.assertThat(buffer.position()).isEqualTo(expectedCount);
    }
}
