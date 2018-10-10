/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.base;

import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Abstract base command size validator class responsible for checking whether size limitation of entities were
 * exceeded and throwing a DittoRuntimeException special to the implementing class responsible for an entity type.
 *
 * @param <T> the specific type of the {@link DittoRuntimeException} the validator produces.
 */
public abstract class AbstractCommandSizeValidator<T extends DittoRuntimeException>  {

    protected static final String DEFAULT_LIMIT = "-1";

    @Nullable private final Long maxSize;

    protected AbstractCommandSizeValidator(@Nullable Long maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * Returns the maximum allowed command size.
     *
     * @return the size
     */
    public Optional<Long> getMaxSize() {
        return Optional.ofNullable(maxSize);
    }

    /**
     * Guard function that throws when a size limit is specified and the given size supplier returns a size
     * greater than the limit.
     *
     * @param sizeSupplier the length calc function (only called when limit is present)
     * @param headersSupplier the headersSupplier for the exception
     * @throws T if size limit is set and exceeded
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
     * Builds a new exception that is used to flag an too large size.
     *
     * @param maxSize the max size
     * @param actualSize the actual size
     * @param headers the ditto headers relevant for exception construction
     * @return the exception of type T
     */
    protected abstract T newInvalidSizeException(long maxSize, long actualSize, DittoHeaders headers);
}
