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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.exceptions.TimeoutInvalidException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.TimeoutValueValidator}.
 */
public final class TimeoutValueValidatorTest {

    private static CharSequence validDittoDurationString;

    private TimeoutValueValidator underTest;

    @BeforeClass
    public static void setUpClass() {
        final DittoDuration dittoDuration = DittoDuration.parseDuration("23s");
        validDittoDurationString = dittoDuration.toString();
    }

    @Before
    public void setUp() {
        underTest = TimeoutValueValidator.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(TimeoutValueValidator.class, areImmutable());
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

        assertThatExceptionOfType(TimeoutInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.TIMEOUT, invalidDittoDurationString))
                .withMessageContaining(invalidDittoDurationString)
                .withNoCause();
    }

    @Test
    public void acceptDittoDurationStringWithNegativeAmount() {
        final String invalidDittoDurationString = "-15s";

        assertThatExceptionOfType(TimeoutInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.TIMEOUT, invalidDittoDurationString))
                .withMessageContaining(invalidDittoDurationString)
                .withMessageStartingWith("The duration must not be negative")
                .withNoCause();
    }

    @Test
    public void acceptDittoDurationStringWithInvalidTimeUnit() {
        final String invalidDittoDurationString = "1d";

        assertThatExceptionOfType(TimeoutInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.TIMEOUT, invalidDittoDurationString))
                .withMessageContaining(invalidDittoDurationString)
                .withNoCause();
    }

}
