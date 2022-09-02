/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.config.raw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link Secret}.
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
