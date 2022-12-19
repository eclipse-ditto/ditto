/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.metrics.instruments.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link Tag}.
 */
public final class TagTest {

    private static final String METRICS_TAG_KEY = "myKey";
    private static final String METRICS_TAG_VALUE = "myValue";

    @Test
    public void assertImmutability() {
        assertInstancesOf(Tag.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(Tag.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithNullKeyThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> Tag.of(null, METRICS_TAG_VALUE))
                .withMessage("The key must not be null!")
                .withNoCause();
    }

    @Test
    public void ofWithNullStringValueThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> Tag.of(METRICS_TAG_KEY, null))
                .withMessage("The value must not be null!")
                .withNoCause();
    }

    @Test
    public void ofWithBlankKeyThrowsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Tag.of("   ", "void"))
                .withMessage("The key must not be blank.")
                .withNoCause();
    }

    @Test
    public void ofWithBlankStringValueThrowsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Tag.of("myKey", " "))
                .withMessage("The value must not be blank.")
                .withNoCause();
    }

    @Test
    public void getKeyReturnsExpectedKey() {
        final var underTest = Tag.of(METRICS_TAG_KEY, METRICS_TAG_VALUE);

        assertThat(underTest.getKey()).isEqualTo(METRICS_TAG_KEY);
    }

    @Test
    public void getValueReturnsExpectedKey() {
        final var underTest = Tag.of(METRICS_TAG_KEY, METRICS_TAG_VALUE);

        assertThat(underTest.getValue()).isEqualTo(METRICS_TAG_VALUE);
    }

    @Test
    public void ofWithBooleanValueReturnsExpected() {
        final var booleanValue = true;
        final var underTest = Tag.of(METRICS_TAG_KEY, booleanValue);

        assertThat(underTest.getKey()).as("key").isEqualTo(METRICS_TAG_KEY);
        assertThat(underTest.getValue()).as("value").isEqualTo(Boolean.toString(booleanValue));
    }

    @Test
    public void ofWithLongValueReturnsExpected() {
        final var longValue = 42L;
        final var underTest = Tag.of(METRICS_TAG_KEY, longValue);

        assertThat(underTest.getKey()).as("key").isEqualTo(METRICS_TAG_KEY);
        assertThat(underTest.getValue()).as("value").isEqualTo(Long.toString(longValue));
    }

}
