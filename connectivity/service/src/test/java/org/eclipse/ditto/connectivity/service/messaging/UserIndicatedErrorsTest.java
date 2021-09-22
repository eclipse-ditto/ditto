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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link UserIndicatedErrors}.
 */
public final class UserIndicatedErrorsTest {

    private static final IllegalArgumentException MATCHING_EXCEPTION =
            new IllegalArgumentException("This exception should match.");
    private static final IllegalArgumentException NON_MATCHING_EXCEPTION =
            new IllegalArgumentException("This exception should not match.");
    private static final IllegalArgumentException NON_MATCHING_EXCEPTION_WITH_MATCHING_CAUSE =
            new IllegalArgumentException("This exception should not match.", MATCHING_EXCEPTION);
    private static final IllegalArgumentException NON_MATCHING_EXCEPTION_WITH_NESTED_MATCHING_CAUSE =
            new IllegalArgumentException("This is not a problem at all.", NON_MATCHING_EXCEPTION_WITH_MATCHING_CAUSE);

    private UserIndicatedErrors underTest;

    @Before
    public void setup() {
        final Config config = ConfigFactory.load("user-indicated-errors");
        underTest = UserIndicatedErrors.of(config);
    }

    @Test
    public void illegalStateExceptionMatches() {
        assertThat(underTest.matches(new IllegalStateException())).isTrue();
    }

    @Test
    public void illegalArgumentExceptionWithCorrectMessageMatches() {
        assertThat(underTest.matches(MATCHING_EXCEPTION)).isTrue();
    }

    @Test
    public void illegalArgumentExceptionWithWrongMessageDoesNotMatch() {
        assertThat(underTest.matches(NON_MATCHING_EXCEPTION)).isFalse();
    }

    @Test
    public void illegalArgumentExceptionWithCauseCorrectMessageMatches() {
        assertThat(underTest.matches(NON_MATCHING_EXCEPTION_WITH_MATCHING_CAUSE)).isTrue();
    }

    @Test
    public void illegalArgumentExceptionWithNestedCauseCorrectMessageMatches() {
        assertThat(underTest.matches(NON_MATCHING_EXCEPTION_WITH_NESTED_MATCHING_CAUSE)).isTrue();
    }

    /**
     * This test should ensure that we can explicitly match exceptions without a message by using "null" in
     * messagePattern.
     */
    @Test
    public void illegalAccessExceptionWithoutMessageMatches() {
        assertThat(underTest.matches(new IllegalAccessException())).isTrue();
    }

    @Test
    public void configurationEnvironmentVariableParsesStringObjects() {
        // this test uses an environment variable injected via maven-surefire-plugin in the pom.xml

        final Config config = ConfigFactory.load("user-indicated-errors-via-env");
        final UserIndicatedErrors withEnvInjectedConfig = UserIndicatedErrors.of(config);
        assertThat(withEnvInjectedConfig.matches(MATCHING_EXCEPTION))
                .isTrue();
        assertThat(withEnvInjectedConfig.matches(NON_MATCHING_EXCEPTION))
                .isFalse();
        assertThat(withEnvInjectedConfig.matches(new ArithmeticException("Some hickup here, nevermind")))
                .isTrue();
        assertThat(withEnvInjectedConfig.matches(new ArithmeticException("Division by zero")))
                .isFalse();
        assertThat(withEnvInjectedConfig.matches(new UnsupportedOperationException()))
                .isTrue();

    }
}
