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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.Test;

/**
 * Tests {@link BinaryValidationResult}.
 */
public final class BinaryValidationResultTest {

    @Test
    public void isValid() {
        final BinaryValidationResult underTest = BinaryValidationResult.valid();

        assertThat(underTest.isValid()).isTrue();
    }

    @Test
    public void getReasonForInvalidity() {
        final IllegalStateException expectedReasonForInvalidity = new IllegalStateException("foo");
        final BinaryValidationResult underTest = BinaryValidationResult.invalid(expectedReasonForInvalidity);

        assertThat(underTest.getReasonForInvalidity()).isEqualTo(expectedReasonForInvalidity);
    }

    @Test
    public void valid() {
        final BinaryValidationResult underTest = BinaryValidationResult.valid();

        assertThat(underTest.isValid()).isTrue();
    }

    @Test
    public void invalid() {
        final BinaryValidationResult underTest = BinaryValidationResult.invalid(new IllegalStateException("foo"));

        assertThat(underTest.isValid()).isFalse();
    }

    @Test
    public void invalidThrowsNPEIfCalledWithNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> BinaryValidationResult.invalid(null))
                .withMessage("The reasonForInvalidity must not be null!");
    }

}