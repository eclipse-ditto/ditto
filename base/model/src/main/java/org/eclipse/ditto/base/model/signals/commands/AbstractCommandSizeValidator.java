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
package org.eclipse.ditto.base.model.signals.commands;

import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;

/**
 * Abstract base command size validator class responsible for checking whether size limitation of entities were
 * exceeded and throwing a DittoRuntimeException special to the implementing class responsible for an entity type.
 *
 * @param <T> the specific type of the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException} the validator produces.
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
     * Guard function that throws when a size limit is specified, and the given size supplier returns a size
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
     * Guard function that throws when a size limit is specified, and the given size supplier returns a size
     * greater than the limit.
     * Only calls the sizeSupplier, if the upper bound provided by the upperBoundSupplier exceeds the limit.
     *
     * @param upperBoundSupplier a calculation function that returns an upper bound for the size
     * (possibly {@link Long#MAX_VALUE})
     * @param sizeSupplier the length calc function (only called when limit is present)
     * @param headersSupplier the headersSupplier for the exception
     * @throws T if size limit is set and exceeded
     * @since 1.1.0
     */
    public void ensureValidSize(final LongSupplier upperBoundSupplier, final LongSupplier sizeSupplier,
            final Supplier<DittoHeaders> headersSupplier) {
        if (null != maxSize && upperBoundSupplier.getAsLong() >= maxSize) {
            ensureValidSize(sizeSupplier, headersSupplier);
        }
    }

    /**
     * Guard function that throws when a size limit is specified, and the given {@code jsonifiable} modified with the
     * given {@code jsonField} is greater than the limit.
     *
     * @param jsonifiable a json before applying the modification
     * @param jsonField the field to set on the given json
     * @param headersSupplier the headersSupplier for the exception
     * @throws T if size limit is set and exceeded
     */
    public void ensureValidSize(final Jsonifiable<JsonObject> jsonifiable, final JsonField jsonField, final Supplier<DittoHeaders> headersSupplier) {
        ensureValidSize(() -> {
            final JsonObject jsonWithField = jsonifiable.toJson().setValue(jsonField.getKey(), jsonField.getValue());
            return jsonWithField.toString().length();
        }, headersSupplier);
    }

    /**
     * Builds a new exception that is used to flag a too large size.
     *
     * @param maxSize the max size
     * @param actualSize the actual size
     * @param headers the ditto headers relevant for exception construction
     * @return the exception of type T
     */
    protected abstract T newInvalidSizeException(long maxSize, long actualSize, DittoHeaders headers);

}
