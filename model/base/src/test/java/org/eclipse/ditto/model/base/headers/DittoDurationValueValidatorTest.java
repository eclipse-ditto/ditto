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
package org.eclipse.ditto.model.base.headers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link DittoDurationValueValidator}.
 */
public final class DittoDurationValueValidatorTest {

    private static CharSequence validDittoDurationString;

    private DittoDurationValueValidator underTest;

    @BeforeClass
    public static void setUpClass() {
        final DittoDuration dittoDuration = DittoDuration.parseDuration("23s");
        validDittoDurationString = dittoDuration.toString();
    }

    @Before
    public void setUp() {
        underTest = DittoDurationValueValidator.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoDurationValueValidator.class, areImmutable());
    }

    @Test
    public void tryToAcceptNullDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.accept(null, validDittoDurationString))
                .withMessage("The definition must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAcceptNullValue() {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.TIMEOUT;

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(headerDefinition, null))
                .withMessageContaining("null")
                .withMessageContaining(headerDefinition.getKey())
                .withMessageEndingWith("is not a valid DittoDuration.")
                .withNoCause();
    }

    @Test
    public void canValidateDittoDuration() {
        assertThat(underTest.canValidate(DittoDuration.class)).isTrue();
    }

    @Test
    public void acceptValidCharSequence() {
        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.TIMEOUT, validDittoDurationString))
                .doesNotThrowAnyException();
    }

    @Test
    public void doesNotValidateAsDefinedJavaTypeIsNotDittoDuration() {
        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.RESPONSE_REQUIRED, validDittoDurationString))
                .doesNotThrowAnyException();
    }

    @Test
    public void acceptNonDittoDurationString() {
        final String invalidDittoDurationString = "true";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.TIMEOUT, invalidDittoDurationString))
                .withMessageContaining(invalidDittoDurationString)
                .withMessageEndingWith("is not a valid duration.")
                .withNoCause();
    }

    @Test
    public void acceptDittoDurationStringWithNegativeAmount() {
        final String invalidDittoDurationString = "-15s";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.TIMEOUT, invalidDittoDurationString))
                .withMessageContaining(invalidDittoDurationString)
                .withMessageEndingWith("is not a valid duration.")
                .withNoCause();
    }

    @Test
    public void acceptDittoDurationStringWithInvalidTimeUnit() {
        final String invalidDittoDurationString = "1h";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.TIMEOUT, invalidDittoDurationString))
                .withMessageContaining(invalidDittoDurationString)
                .withMessageEndingWith("is not a valid duration.")
                .withNoCause();
    }

}