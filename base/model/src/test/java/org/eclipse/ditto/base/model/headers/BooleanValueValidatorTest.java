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

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.BooleanValueValidator}.
 */
public final class BooleanValueValidatorTest {

    private static final CharSequence BOOLEAN_CHAR_SEQUENCE = Boolean.TRUE.toString();

    private BooleanValueValidator underTest;

    @Before
    public void setUp() {
        underTest = BooleanValueValidator.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(BooleanValueValidator.class, areImmutable());
    }

    @Test
    public void tryToAcceptNullDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.accept(null, BOOLEAN_CHAR_SEQUENCE))
                .withMessage("The definition must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAcceptNullValue() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.RESPONSE_REQUIRED;

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(headerDefinition, null))
                .withMessageContaining("null")
                .withMessageContaining(headerDefinition.getKey())
                .withMessageEndingWith("is not a valid boolean.")
                .withNoCause();
    }

    @Test
    public void canValidateBoolean() {
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest.canValidate(boolean.class)).as("boolean").isTrue();
            softly.assertThat(underTest.canValidate(Boolean.class)).as("Boolean").isTrue();
        }
    }

    @Test
    public void acceptTrueCharSequence() {
        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.RESPONSE_REQUIRED, BOOLEAN_CHAR_SEQUENCE))
                .doesNotThrowAnyException();
    }

    @Test
    public void acceptFalseCharSequence() {
        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.RESPONSE_REQUIRED, Boolean.FALSE.toString()))
                .doesNotThrowAnyException();
    }

    @Test
    public void doesNotValidateAsDefinedJavaTypeIsNotBoolean() {
        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.SCHEMA_VERSION, BOOLEAN_CHAR_SEQUENCE))
                .doesNotThrowAnyException();
    }

    @Test
    public void acceptInvalidCharSequence() {
        final String invalidBoolean = "5";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.RESPONSE_REQUIRED, invalidBoolean))
                .withMessageContaining(invalidBoolean)
                .withMessageEndingWith("is not a valid boolean.")
                .withNoCause();
    }

}
