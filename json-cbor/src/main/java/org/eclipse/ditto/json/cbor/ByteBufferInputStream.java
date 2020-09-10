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
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Implementation of {@link InputStream} backed by a {@link ByteBuffer}.
 */
final class ByteBufferInputStream extends InputStream {

    private final ByteBuffer byteBuffer;

    private ByteBufferInputStream(final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    static InputStream of(final ByteBuffer byteBuffer) {
        if (byteBuffer.hasArray()) {
            return new ByteArrayInputStream(
                    byteBuffer.array(),
                    byteBuffer.arrayOffset() + byteBuffer.position(),
                    byteBuffer.limit());
        }
        return new ByteBufferInputStream(byteBuffer);
    }

    @Override
    public int read() {
        return byteBuffer.hasRemaining() ? byteBuffer.get() & 0xFF : -1;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) {
        // this implementation is optional but should increase speed.
        if (!byteBuffer.hasRemaining()) {
            return -1;
        }
        final int dataToRead = Math.min(byteBuffer.remaining(), len);
        byteBuffer.get(b, off, dataToRead);
        return dataToRead;
    }
}
