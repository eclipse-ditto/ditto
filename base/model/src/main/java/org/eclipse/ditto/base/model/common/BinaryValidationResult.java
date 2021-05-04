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
package org.eclipse.ditto.base.model.common;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Holds the result of a validation with binary outcome.
 * <p>
 * In general instances of this class can be regarded to be immutable.
 * Technically immutability cannot be granted because the possibly existing {@link Throwable} is mutable.
 * </p>
 */
@NotThreadSafe
public final class BinaryValidationResult {

    @Nullable
    private final Throwable reasonForInvalidity;

    private BinaryValidationResult(@Nullable final Throwable reasonForInvalidity) {
        this.reasonForInvalidity = reasonForInvalidity;
    }

    /**
     * Creates an instance of a valid validation result.
     *
     * @return the created instance.
     */
    public static BinaryValidationResult valid() {
        return new BinaryValidationResult(null);
    }

    /**
     * Creates an instance of an invalid validation result holding the given throwable as reason for invalidity.
     *
     * @param reasonForInvalidity the reason why the validation has failed.
     * @return the created instance.
     * @throws NullPointerException if reasonForInvalidity is {@code null}.
     */
    public static BinaryValidationResult invalid(final Throwable reasonForInvalidity) {
        return new BinaryValidationResult(checkNotNull(reasonForInvalidity, "reasonForInvalidity"));
    }

    /**
     * Indicates whether the validation was successful or not.
     *
     * @return {@code true} if validation was successful, {@code false} if not.
     */
    public boolean isValid() {
        return reasonForInvalidity == null;
    }

    /**
     * @return the reason why {@link #isValid()} is {@code false} or {@code null} if the result is valid.
     */
    @Nullable
    public Throwable getReasonForInvalidity() {
        return reasonForInvalidity;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BinaryValidationResult that = (BinaryValidationResult) o;
        return Objects.equals(reasonForInvalidity, that.reasonForInvalidity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reasonForInvalidity);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "reasonForInvalidity=" + reasonForInvalidity +
                "]";
    }

}
