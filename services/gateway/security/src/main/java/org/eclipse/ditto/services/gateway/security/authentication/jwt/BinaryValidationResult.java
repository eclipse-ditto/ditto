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
package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Holds the result of a validation with binary outcome.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class BinaryValidationResult {

    @Nullable
    private final Throwable reasonForInvalidity;

    private BinaryValidationResult(@Nullable final Throwable reasonForInvalidity) {
        this.reasonForInvalidity = reasonForInvalidity;
    }

    /**
     * Indicates whether the validation was successful or not.
     *
     * @return True if validation was successful. False if not.
     */
    public boolean isValid() {
        return reasonForInvalidity == null;
    }

    /**
     * @return the reason for an invalid {@link BinaryValidationResult}. {@code null} if {@link #isValid()} is true.
     */
    @Nullable
    public Throwable getReasonForInvalidity() {
        return reasonForInvalidity;
    }

    /**
     * Creates an instance of a {@link BinaryValidationResult valid result}.
     *
     * @return the created instance of {@link BinaryValidationResult}.
     */
    public static BinaryValidationResult valid() {
        return new BinaryValidationResult(null);
    }

    /**
     * Creates an instance of a {@link BinaryValidationResult invalid result} holding the given throwable as
     * reason for invalidity.
     *
     * @param reasonForInvalidity the reason why the validation has failed.
     * @return the created instance of {@link BinaryValidationResult}.
     * @throws java.lang.NullPointerException if reasonForInvalidity is null.
     */
    public static BinaryValidationResult invalid(final Throwable reasonForInvalidity) {
        return new BinaryValidationResult(checkNotNull(reasonForInvalidity, "reasonForInvalidity"));
    }
}
