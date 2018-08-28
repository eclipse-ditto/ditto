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

package org.eclipse.ditto.signals.commands.base;

import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

public abstract class AbstractCommandSizeValidator {

    @Nullable private final Long maxSize;

    protected AbstractCommandSizeValidator(@Nullable Long maxSize) {
        this.maxSize = maxSize;
    }

    public Optional<Long> getMaxSize() {
        return Optional.ofNullable(maxSize);
    }

    /**
     * Guard function that throws when a policy size limit is specified and the given size supplier returns a size
     * less than the limit.
     * @param sizeSupplier the length calc function (only called when limit is present)
     * @param headersSupplier the headersSupplier for the exception
     * @throws DittoRuntimeException if size limit is set and exceeded
     */
    public void ensureValidSize(final LongSupplier sizeSupplier, final Supplier<DittoHeaders> headersSupplier) {
        if (null != maxSize) {
            long actualSize = sizeSupplier.getAsLong();
            if (maxSize < actualSize) {
                throw newInvalidSizeException(maxSize, actualSize, headersSupplier.get());
            }

        }
    }

    /**
     * Builds a new exception that is used to flag an invalid side
     * @param maxSize the max size
     * @param actualSize the actual size
     * @param headers the ditto headers relevant for exception construction
     * @return the exception
     */
    protected abstract DittoRuntimeException newInvalidSizeException(long maxSize, long actualSize, final DittoHeaders
            headers);
}
