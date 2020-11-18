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
package org.eclipse.ditto.model.messages;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;
import org.eclipse.ditto.model.base.headers.ValueValidator;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link MessageHeaderTimeoutValueValidator}.
 */
public final class MessageHeaderTimeoutValueValidatorTest {

    private static final CharSequence VALID_TIMEOUT_STRING = "42";

    private ValueValidator underTest;

    @Before
    public void setUp() {
        underTest = MessageHeaderTimeoutValueValidator.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(MessageHeaderTimeoutValueValidator.class, areImmutable());
    }

    @Test
    public void tryToAcceptNullDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.accept(null, VALID_TIMEOUT_STRING))
                .withMessage("The definition must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAcceptNullValue() {
        final HeaderDefinition headerDefinition = MessageHeaderDefinition.TIMEOUT;

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(headerDefinition, null))
                .withMessageContaining("null")
                .withMessageContaining(headerDefinition.getKey())
                .withMessageEndingWith("is not a valid long.")
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
        assertThatCode(() -> underTest.accept(MessageHeaderDefinition.TIMEOUT, VALID_TIMEOUT_STRING))
                .doesNotThrowAnyException();
    }

    @Test
    public void doesNotValidateAsDefinedJavaTypeIsNotLong() {
        assertThatCode(() -> underTest.accept(MessageHeaderDefinition.SUBJECT, VALID_TIMEOUT_STRING))
                .doesNotThrowAnyException();
    }

    @Test
    public void acceptNonLongCharSequence() {
        final String invalidTimeoutString = "true";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(MessageHeaderDefinition.TIMEOUT, invalidTimeoutString))
                .withMessageContaining(invalidTimeoutString)
                .withMessageEndingWith("is not a valid long.")
                .withNoCause();
    }

}