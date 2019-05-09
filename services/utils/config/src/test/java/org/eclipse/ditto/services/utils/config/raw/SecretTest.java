/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.config.raw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.config.raw.Secret}.
 */
public final class SecretTest {

    private static final String KNOWN_NAME = "foo";
    private static final String KNOWN_VALUE = "bar";

    @Test
    public void assertImmutability() {
        assertInstancesOf(Secret.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(Secret.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToCreateNewInstanceWithNullName() {
        assertThatNullPointerException()
                .isThrownBy(() -> Secret.newInstance(null, KNOWN_VALUE))
                .withMessage("The Secret name must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToCreateNewInstanceWithNullValue() {
        assertThatNullPointerException()
                .isThrownBy(() -> Secret.newInstance(KNOWN_NAME, null))
                .withMessage("The Secret value must not be null!")
                .withNoCause();
    }

    @Test
    public void getNameReturnsExpected() {
        final Secret underTest = Secret.newInstance(KNOWN_NAME, KNOWN_VALUE);

        assertThat(underTest.getName()).isEqualTo(KNOWN_NAME);
    }

    @Test
    public void getValueReturnsExpected() {
        final Secret underTest = Secret.newInstance(KNOWN_NAME, KNOWN_VALUE);

        assertThat(underTest.getValue()).isEqualTo(KNOWN_VALUE);
    }

    @Test
    public void toStringContainsExpected() {
        final Secret underTest = Secret.newInstance(KNOWN_NAME, KNOWN_VALUE);

        assertThat(underTest.toString()).contains("name")
                .contains(KNOWN_NAME)
                .contains("value")
                .contains(KNOWN_VALUE);
    }

}