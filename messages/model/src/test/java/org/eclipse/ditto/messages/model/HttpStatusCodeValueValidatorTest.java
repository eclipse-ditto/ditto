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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.common.HttpStatusCodeOutOfRangeException;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.eclipse.ditto.base.model.headers.ValueValidator;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link HttpStatusCodeValueValidator}.
 */
public final class HttpStatusCodeValueValidatorTest {

    private static final CharSequence VALID_HTTP_STATUS_CODE_STRING = "201";

    private ValueValidator underTest;

    @Before
    public void setUp() {
        underTest = HttpStatusCodeValueValidator.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(HttpStatusCodeValueValidator.class, areImmutable());
    }

    @Test
    public void tryToAcceptNullDefinition() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.accept(null, VALID_HTTP_STATUS_CODE_STRING))
                .withMessage("The definition must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToAcceptNullValue() {
        final HeaderDefinition headerDefinition = MessageHeaderDefinition.STATUS_CODE;

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
        assertThatCode(() -> underTest.accept(MessageHeaderDefinition.STATUS_CODE, VALID_HTTP_STATUS_CODE_STRING))
                .doesNotThrowAnyException();
    }

    @Test
    public void doesNotValidateAsDefinedJavaTypeIsNotInt() {
        assertThatCode(() -> underTest.accept(MessageHeaderDefinition.STATUS_CODE, VALID_HTTP_STATUS_CODE_STRING))
                .doesNotThrowAnyException();
    }

    @Test
    public void acceptNonIntCharSequence() {
        final String invalidHttpStatusCodeString = "true";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(MessageHeaderDefinition.STATUS_CODE, invalidHttpStatusCodeString))
                .withMessageContaining(invalidHttpStatusCodeString)
                .withMessageEndingWith("is not a valid int.")
                .withNoCause();
    }

    @Test
    public void acceptNonHttpStatusCodeIntCharSequence() {
        final String invalidHttpStatusCodeString = "4711";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(MessageHeaderDefinition.STATUS_CODE, invalidHttpStatusCodeString))
                .withMessageContaining(invalidHttpStatusCodeString)
                .withMessageEndingWith("is not a valid HTTP status code.")
                .withCauseInstanceOf(HttpStatusCodeOutOfRangeException.class);
    }

}
