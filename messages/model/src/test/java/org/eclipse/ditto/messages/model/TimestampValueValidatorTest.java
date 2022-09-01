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
package org.eclipse.ditto.messages.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.OffsetDateTime;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link TimestampValueValidator}.
 */
public final class TimestampValueValidatorTest {

    private static CharSequence validTimestampString;

    private TimestampValueValidator underTest;

    @BeforeClass
    public static void setUpClass() {
        final OffsetDateTime timestamp = OffsetDateTime.now();
        validTimestampString = timestamp.toString();
    }

    @Before
    public void setUp() {
        underTest = TimestampValueValidator.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(TimestampValueValidator.class, areImmutable());
    }

    @Test
    public void tryToAcceptNullDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.accept(null, validTimestampString))
                .withMessage("The definition must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAcceptNullValue() {
        final HeaderDefinition headerDefinition = MessageHeaderDefinition.TIMESTAMP;

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(headerDefinition, null))
                .withMessageContaining("null")
                .withMessageContaining(headerDefinition.getKey())
                .withMessageEndingWith("is not a valid String.")
                .withNoCause();
    }

    @Test
    public void canValidateTimestamp() {
        assertThat(underTest.canValidate(String.class)).isTrue();
    }

    @Test
    public void acceptValidCharSequence() {
        assertThatCode(() -> underTest.accept(MessageHeaderDefinition.TIMESTAMP, validTimestampString))
                .doesNotThrowAnyException();
    }

    @Test
    public void doesNotValidateAsDefinedJavaTypeIsNotTimestamp() {
        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.RESPONSE_REQUIRED, validTimestampString))
                .doesNotThrowAnyException();
    }

    @Test
    public void acceptNonTimestampString() {
        final String invalidTimestampString = "true";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(MessageHeaderDefinition.TIMESTAMP, invalidTimestampString))
                .withMessageContaining(invalidTimestampString)
                .withMessageEndingWith("is not a valid timestamp.");
    }

}
