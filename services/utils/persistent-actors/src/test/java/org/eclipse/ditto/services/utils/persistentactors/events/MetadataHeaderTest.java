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
package org.eclipse.ditto.services.utils.persistentactors.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MetadataHeaderTest}.
 */
@RunWith(Enclosed.class)
public final class MetadataHeaderTest {

    public static final class GeneralFunctionalityTest {

        private static final MetadataHeaderKey KNOWN_KEY = MetadataHeaderKey.of(JsonPointer.of("foo/bar/baz"));
        private static final JsonValue KNOWN_VALUE = JsonValue.of(1);

        @Test
        public void assertImmutability() {
            assertInstancesOf(MetadataHeader.class,
                    areImmutable(),
                    provided(MetadataHeaderKey.class, JsonValue.class).areAlsoImmutable());
        }

        @Test
        public void testHashCodeAndEquals() {
            EqualsVerifier.forClass(MetadataHeader.class)
                    .usingGetClass()
                    .verify();
        }

        @Test
        public void tryToGetInstanceWithNullKey() {
            assertThatNullPointerException()
                    .isThrownBy(() -> MetadataHeader.of(null, KNOWN_VALUE))
                    .withMessage("The key must not be null!")
                    .withNoCause();
        }

        @Test
        public void tryToGetInstanceWithNullValue() {
            assertThatNullPointerException()
                    .isThrownBy(() -> MetadataHeader.of(KNOWN_KEY, null))
                    .withMessage("The value must not be null!")
                    .withNoCause();
        }

        @Test
        public void getKeyReturnsExpected() {
            final MetadataHeader underTest = MetadataHeader.of(KNOWN_KEY, KNOWN_VALUE);

            assertThat(underTest.getKey()).isEqualTo(KNOWN_KEY);
        }

        @Test
        public void getValueReturnsExpected() {
            final MetadataHeader underTest = MetadataHeader.of(KNOWN_KEY, KNOWN_VALUE);

            assertThat(underTest.getValue()).isEqualTo(KNOWN_VALUE);
        }

    }

    @RunWith(Parameterized.class)
    public static final class CompareToTest {

        @Parameterized.Parameters(name = "{index}: Compare {0} to {1}, expecting {2}")
        public static Collection<Object[]> input() {
            final MetadataHeaderKey keySpecificB = MetadataHeaderKey.of(JsonPointer.of("/foo/bar/baz"));
            final MetadataHeaderKey keyWildcardB = MetadataHeaderKey.of(JsonPointer.of("/*/baz"));
            final MetadataHeaderKey keySpecificC = MetadataHeaderKey.of(JsonPointer.of("/foo/bar/chumble"));
            final MetadataHeaderKey keyWildcardC = MetadataHeaderKey.of(JsonPointer.of("/*/chumble"));
            final JsonValue value0 = JsonValue.of(0);
            final JsonValue valueRed = JsonValue.of("red");

            return Arrays.asList(new Object[][] {
                    {MetadataHeader.of(keySpecificB, value0), MetadataHeader.of(keySpecificB, value0), 0},
                    {MetadataHeader.of(keySpecificB, value0), MetadataHeader.of(keySpecificB, valueRed), -1},
                    {MetadataHeader.of(keyWildcardB, value0), MetadataHeader.of(keyWildcardB, value0), 0},
                    {MetadataHeader.of(keyWildcardB, valueRed), MetadataHeader.of(keyWildcardB, value0), 1},
                    {MetadataHeader.of(keyWildcardC, value0), MetadataHeader.of(keySpecificB, value0), -1},
                    {MetadataHeader.of(keySpecificB, valueRed), MetadataHeader.of(keySpecificC, value0), -1},
            });
        }

        @Parameterized.Parameter(0)
        public MetadataHeader left;

        @Parameterized.Parameter(1)
        public MetadataHeader right;

        @Parameterized.Parameter(2)
        public int expected;

        @Test
        public void compareToWorksAsExpected() {
            assertThat(left.compareTo(right))
                    .as("comparing %s with %s", left, right)
                    .isEqualTo(expected);
        }

    }

}