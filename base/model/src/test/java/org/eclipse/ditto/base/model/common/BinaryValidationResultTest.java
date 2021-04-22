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
