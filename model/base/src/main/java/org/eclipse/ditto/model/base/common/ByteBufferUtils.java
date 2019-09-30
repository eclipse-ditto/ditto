/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Utilities around {@link ByteBuffer}.
 */
@Immutable
public final class ByteBufferUtils {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private ByteBufferUtils() {
        throw new AssertionError();
    }

    /**
     * Deeply clones the passed in ByteBuffer.
     *
     * @param original the ByteBuffer to clone.
     * @return the cloned ByteBuffer.
     */
    public static ByteBuffer clone(final ByteBuffer original) {
        if (original.remaining() == 0) {
            return ByteBuffer.wrap(EMPTY_BYTE_ARRAY);
        }

        final ByteBuffer clone = ByteBuffer.allocate(original.remaining());
        if (original.hasArray()) {
            System.arraycopy(original.array(), original.arrayOffset() + original.position(), clone.array(), 0,
                    original.remaining());
        } else {
            clone.put(original.duplicate());
            clone.flip();
        }
        return clone;
    }

    /**
     * Creates an empty ByteBuffer of size 0.
     * @return an empty ByteBuffer.
     */
    public static ByteBuffer empty() {
        return ByteBuffer.allocate(0);
    }

    /**
     * Creates a string from the ByteBuffer.
     * @param byteBuffer the ByteBuffer to decode.
     * @return the ByteBuffer in UTF-8 representation or {@code null} if it was null.
     */
    @Nullable
    public static String toUtf8String(@Nullable final ByteBuffer byteBuffer) {
        if (null == byteBuffer) {
            return null;
        }
        return StandardCharsets.UTF_8.decode(byteBuffer.asReadOnlyBuffer()).toString();
    }

}
