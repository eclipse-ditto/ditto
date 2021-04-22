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
 * Unit test for {@link org.eclipse.ditto.base.model.headers.IntValueValidator}.
 */
public final class IntValueValidatorTest {

    private IntValueValidator underTest;

    @Before
    public void setUp() {
        underTest = IntValueValidator.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(IntValueValidator.class, areImmutable());
    }

    @Test
    public void tryToAcceptNullDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.accept(null, "5"))
                .withMessage("The definition must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAcceptNullValue() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.SCHEMA_VERSION;

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(headerDefinition, null))
                .withMessageContaining("null")
                .withMessageContaining(headerDefinition.getKey())
                .withMessageEndingWith("is not a valid int.")
                .withNoCause();
    }

    @Test
    public void canValidateInteger() {
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest.canValidate(int.class)).as("int").isTrue();
            softly.assertThat(underTest.canValidate(Integer.class)).as("Integer").isTrue();
        }
    }

    @Test
    public void acceptValidCharSequence() {
        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.SCHEMA_VERSION, "2"))
                .doesNotThrowAnyException();
    }

    @Test
    public void doesNotValidateAsDefinedJavaTypeIsNotInt() {
        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.RESPONSE_REQUIRED, "2"))
                .doesNotThrowAnyException();
    }

    @Test
    public void acceptInvalidCharSequence() {
        final String invalidInt = "true";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.SCHEMA_VERSION, invalidInt))
                .withMessageContaining(invalidInt)
                .withMessageEndingWith("is not a valid int.")
                .withNoCause();
    }

}
