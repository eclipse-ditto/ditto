/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.common.HttpStatus}.
 */
public final class HttpStatusTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(HttpStatus.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(HttpStatus.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getInstanceWithCodeOutOfRange() {
        assertThatExceptionOfType(HttpStatusCodeOutOfRangeException.class)
                .isThrownBy(() -> HttpStatus.getInstance(99));
    }

    @Test
    public void tryGetInstanceWithCodeOutOfRange() {
        assertThat(HttpStatus.tryGetInstance(600)).isEmpty();
    }

    @Test
    public void getInstanceWithValidCode() throws HttpStatusCodeOutOfRangeException {
        final HttpStatus expected = HttpStatus.IM_A_TEAPOT;

        final HttpStatus actual = HttpStatus.getInstance(expected.getCode());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void tryGetInstanceWithValidCode() {
        final HttpStatus expected = HttpStatus.ACCEPTED;

        final Optional<HttpStatus> httpStatusOptional = HttpStatus.tryGetInstance(expected.getCode());

        assertThat(httpStatusOptional).hasValue(expected);
    }

    @Test
    public void tryToGetInstanceWithCustomCode() throws HttpStatusCodeOutOfRangeException {
        HttpStatus.getInstance(219);
    }

    @Test
    public void informationalHttpStatusIsOnlyInformational() {
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            final HttpStatus underTest = HttpStatus.PROCESSING;
            softly.assertThat(underTest.isInformational()).as("is informational").isTrue();
            softly.assertThat(underTest.isSuccess()).as("is not success").isFalse();
            softly.assertThat(underTest.isRedirection()).as("is not redirection").isFalse();
            softly.assertThat(underTest.isClientError()).as("is not client error").isFalse();
            softly.assertThat(underTest.isServerError()).as("is not server error").isFalse();
        }
    }

    @Test
    public void successHttpStatusIsOnlySuccess() {
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            final HttpStatus underTest = HttpStatus.OK;
            softly.assertThat(underTest.isInformational()).as("is not informational").isFalse();
            softly.assertThat(underTest.isSuccess()).as("is success").isTrue();
            softly.assertThat(underTest.isRedirection()).as("is not redirection").isFalse();
            softly.assertThat(underTest.isClientError()).as("is not client error").isFalse();
            softly.assertThat(underTest.isServerError()).as("is not server error").isFalse();
        }
    }

    @Test
    public void redirectionHttpStatusIsOnlyRedirection() {
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            final HttpStatus underTest = HttpStatus.FOUND;
            softly.assertThat(underTest.isInformational()).as("is not informational").isFalse();
            softly.assertThat(underTest.isSuccess()).as("is not success").isFalse();
            softly.assertThat(underTest.isRedirection()).as("is redirection").isTrue();
            softly.assertThat(underTest.isClientError()).as("is not client error").isFalse();
            softly.assertThat(underTest.isServerError()).as("is not server error").isFalse();
        }
    }

    @Test
    public void clientErrorHttpStatusIsOnlyClientError() {
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            final HttpStatus underTest = HttpStatus.BAD_REQUEST;
            softly.assertThat(underTest.isInformational()).as("is not informational").isFalse();
            softly.assertThat(underTest.isSuccess()).as("is not success").isFalse();
            softly.assertThat(underTest.isRedirection()).as("is not redirection").isFalse();
            softly.assertThat(underTest.isClientError()).as("is client error").isTrue();
            softly.assertThat(underTest.isServerError()).as("is not server error").isFalse();
        }
    }

    @Test
    public void serverErrorHttpStatusIsOnlyServerError() {
        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            final HttpStatus underTest = HttpStatus.INTERNAL_SERVER_ERROR;
            softly.assertThat(underTest.isInformational()).as("is not informational").isFalse();
            softly.assertThat(underTest.isSuccess()).as("is not success").isFalse();
            softly.assertThat(underTest.isRedirection()).as("is not redirection").isFalse();
            softly.assertThat(underTest.isClientError()).as("is not client error").isFalse();
            softly.assertThat(underTest.isServerError()).as("is server error").isTrue();
        }
    }

}
