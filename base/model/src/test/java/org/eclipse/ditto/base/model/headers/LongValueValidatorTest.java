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
package org.eclipse.ditto.base.model.headers;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.LongValueValidator}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class LongValueValidatorTest {

    private static final CharSequence LONG_CHAR_SEQUENCE = Long.toString((long) Integer.MAX_VALUE + 1);

    @Mock private HeaderDefinition longHeaderDefinition;
    private LongValueValidator underTest;

    @Before
    public void setUp() {
        Mockito.when(longHeaderDefinition.getJavaType()).thenReturn(Long.class);
        Mockito.when(longHeaderDefinition.getKey()).thenReturn("long-value");
        underTest = LongValueValidator.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(LongValueValidator.class, areImmutable());
    }

    @Test
    public void tryToAcceptNullDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.accept(null, LONG_CHAR_SEQUENCE))
                .withMessage("The definition must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAcceptNullValue() {
        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(longHeaderDefinition, null))
                .withMessageContaining("null")
                .withMessageContaining(longHeaderDefinition.getKey())
                .withMessageEndingWith("is not a valid Long.")
                .withNoCause();
    }

    @Test
    public void canValidateLong() {
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest.canValidate(long.class)).as("long").isTrue();
            softly.assertThat(underTest.canValidate(Long.class)).as("Long").isTrue();
        }
    }

    @Test
    public void acceptValidCharSequence() {
        assertThatCode(() -> underTest.accept(longHeaderDefinition, LONG_CHAR_SEQUENCE))
                .doesNotThrowAnyException();
    }

    @Test
    public void doesNotValidateAsDefinedJavaTypeIsNotLong() {
        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.RESPONSE_REQUIRED, LONG_CHAR_SEQUENCE))
                .doesNotThrowAnyException();
    }

    @Test
    public void acceptInvalidCharSequence() {
        final String invalidLong = "true";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(longHeaderDefinition, invalidLong))
                .withMessageContaining(invalidLong)
                .withMessageEndingWith("is not a valid long.")
                .withNoCause();
    }

}
