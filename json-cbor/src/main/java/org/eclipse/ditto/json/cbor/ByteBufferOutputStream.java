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

import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Implementation of {@link OutputStream} backed by a {@link ByteBuffer}.
 */
final class ByteBufferOutputStream extends OutputStream {

    private final ByteBuffer destinationBuffer;

    ByteBufferOutputStream(final ByteBuffer destinationBuffer) {
        this.destinationBuffer = destinationBuffer;
    }

    @Override
    public void write(final int b) {
        // super specifies to ignore everything except the lower 8 bits.
        destinationBuffer.put((byte) b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) {
        destinationBuffer.put(b, off, len);
    }
}
