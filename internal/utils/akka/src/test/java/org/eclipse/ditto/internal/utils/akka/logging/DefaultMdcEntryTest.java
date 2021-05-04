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
package org.eclipse.ditto.internal.utils.akka.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultMdcEntry}.
 */
public final class DefaultMdcEntryTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultMdcEntry.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultMdcEntry.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getInstanceWithNullKey() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultMdcEntry.of(null, "bar"))
                .withMessage("The key must not be null!")
                .withNoCause();
    }

    @Test
    public void getInstanceWithEmptyKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DefaultMdcEntry.of("", "bar"))
                .withMessage("The argument 'key' must not be empty!")
                .withNoCause();
    }

    @Test
    public void getInstanceWithNullValue() {
        final DefaultMdcEntry underTest = DefaultMdcEntry.of("foo", null);

        assertThat(underTest.getValueOrNull()).isNull();
    }

    @Test
    public void getKey() {
        final String key = "foo";
        final DefaultMdcEntry underTest = DefaultMdcEntry.of(key, "bar");

        assertThat(underTest.getKey()).isEqualTo(key);
    }

    @Test
    public void getValue() {
        final String value = "bar";
        final DefaultMdcEntry underTest = DefaultMdcEntry.of("foo", value);

        assertThat(underTest.getValueOrNull()).isEqualTo(value);
    }

}
