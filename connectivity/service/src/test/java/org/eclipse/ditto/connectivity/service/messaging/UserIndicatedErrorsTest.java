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

public final class UserIndicatedErrorsTest {

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
        assertThat(underTest.matches(new IllegalArgumentException("This exception should match."))).isTrue();
    }

    @Test
    public void illegalArgumentExceptionWithWrongMessageDoesNotMatch() {
        assertThat(underTest.matches(new IllegalArgumentException("This exception should not match."))).isFalse();
    }

    /**
     * This test should ensure that we can explicitly match exceptions without a message by using "null" in
     * messagePattern.
     */
    @Test
    public void illegalAccessExceptionWithoutMessageMatches() {
        assertThat(underTest.matches(new IllegalAccessException())).isTrue();
    }

}
