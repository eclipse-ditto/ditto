/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.base.common;

import java.nio.ByteBuffer;

import javax.annotation.concurrent.Immutable;

/**
 * Utilities around {@link ByteBuffer}.
 */
@Immutable
public final class ByteBufferUtils {

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
            return ByteBuffer.wrap(new byte[0]);
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

}
