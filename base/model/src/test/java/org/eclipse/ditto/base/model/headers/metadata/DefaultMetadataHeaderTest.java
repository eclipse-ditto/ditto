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
package org.eclipse.ditto.base.model.headers.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.metadata.DefaultMetadataHeader}.
 */
@RunWith(Enclosed.class)
public final class DefaultMetadataHeaderTest {

    public static final class GeneralFunctionalityTest {

        private static final MetadataHeaderKey KNOWN_KEY = MetadataHeaderKey.of(JsonPointer.of("foo/bar/baz"));
        private static final JsonValue KNOWN_VALUE = JsonValue.of(1);

        @Test
        public void assertImmutability() {
            assertInstancesOf(DefaultMetadataHeader.class,
                    areImmutable(),
                    provided(MetadataHeaderKey.class, JsonValue.class).areAlsoImmutable());
        }

        @Test
        public void testHashCodeAndEquals() {
            EqualsVerifier.forClass(DefaultMetadataHeader.class)
                    .usingGetClass()
                    .verify();
        }

        @Test
        public void tryToGetInstanceWithNullKey() {
            assertThatNullPointerException()
                    .isThrownBy(() -> DefaultMetadataHeader.of(null, KNOWN_VALUE))
                    .withMessage("The key must not be null!")
                    .withNoCause();
        }

        @Test
        public void tryToGetInstanceWithNullValue() {
            assertThatNullPointerException()
                    .isThrownBy(() -> DefaultMetadataHeader.of(KNOWN_KEY, null))
                    .withMessage("The value must not be null!")
                    .withNoCause();
        }

        @Test
        public void getKeyReturnsExpected() {
            final DefaultMetadataHeader underTest = DefaultMetadataHeader.of(KNOWN_KEY, KNOWN_VALUE);

            assertThat(underTest.getKey()).isEqualTo(KNOWN_KEY);
        }

        @Test
        public void getValueReturnsExpected() {
            final DefaultMetadataHeader underTest = DefaultMetadataHeader.of(KNOWN_KEY, KNOWN_VALUE);

            assertThat(underTest.getValue()).isEqualTo(KNOWN_VALUE);
        }

        @Test
        public void toJsonReturnsExpected() {
            final JsonObject expected = JsonObject.newBuilder()
                    .set(MetadataHeader.JsonFields.METADATA_KEY, KNOWN_KEY.toString())
                    .set(MetadataHeader.JsonFields.METADATA_VALUE, KNOWN_VALUE)
                    .build();

            final DefaultMetadataHeader underTest = DefaultMetadataHeader.of(KNOWN_KEY, KNOWN_VALUE);

            assertThat(underTest.toJson()).isEqualTo(expected);
        }

        @Test
        public void getInstanceFromNullJsonObject() {
            assertThatNullPointerException()
                    .isThrownBy(() -> DefaultMetadataHeader.fromJson(null))
                    .withMessage("The jsonObject must not be null!")
                    .withNoCause();
        }

        @Test
        public void getInstanceFromNullLiteral() {
            assertThatExceptionOfType(JsonMissingFieldException.class)
                    .isThrownBy(() -> DefaultMetadataHeader.fromJson(JsonObject.of("null")))
                    .withMessage("Metadata header entry JSON object did not include required <%s> field!",
                            MetadataHeader.JsonFields.METADATA_KEY.getPointer())
                    .withNoCause();
        }

        @Test
        public void getInstanceFromJsonObjectWithoutMetadataValue() {
            final JsonObject jsonObject = JsonObject.newBuilder()
                    .set(MetadataHeader.JsonFields.METADATA_KEY, KNOWN_KEY.toString())
                    .build();

            assertThatExceptionOfType(JsonMissingFieldException.class)
                    .isThrownBy(() -> DefaultMetadataHeader.fromJson(jsonObject))
                    .withMessage("Metadata header entry JSON object did not include required <%s> field!",
                            MetadataHeader.JsonFields.METADATA_VALUE.getPointer())
                    .withNoCause();
        }

    }

    @RunWith(Parameterized.class)
    public static final class CompareToTest {

        @Parameterized.Parameters(name = "{index}: Compare {0} to {1}, expecting {2}")
        public static Collection<Object[]> input() {
            final MetadataHeaderKey keySpecificB = DefaultMetadataHeaderKey.of(JsonPointer.of("/foo/bar/baz"));
            final MetadataHeaderKey keyWildcardB = DefaultMetadataHeaderKey.of(JsonPointer.of("/*/baz"));
            final MetadataHeaderKey keySpecificC = DefaultMetadataHeaderKey.of(JsonPointer.of("/foo/bar/chumble"));
            final MetadataHeaderKey keyWildcardC = DefaultMetadataHeaderKey.of(JsonPointer.of("/*/chumble"));
            final JsonValue value0 = JsonValue.of(0);
            final JsonValue valueRed = JsonValue.of("red");

            return Arrays.asList(new Object[][] {
                    {DefaultMetadataHeader.of(keySpecificB, value0), DefaultMetadataHeader.of(keySpecificB, value0), 0},
                    {DefaultMetadataHeader.of(keySpecificB, value0), DefaultMetadataHeader.of(keySpecificB, valueRed), -1},
                    {DefaultMetadataHeader.of(keyWildcardB, value0), DefaultMetadataHeader.of(keyWildcardB, value0), 0},
                    {DefaultMetadataHeader.of(keyWildcardB, valueRed), DefaultMetadataHeader.of(keyWildcardB, value0), 1},
                    {DefaultMetadataHeader.of(keyWildcardC, value0), DefaultMetadataHeader.of(keySpecificB, value0), -1},
                    {DefaultMetadataHeader.of(keySpecificB, valueRed), DefaultMetadataHeader.of(keySpecificC, value0), -1},
            });
        }

        @Parameterized.Parameter(0)
        public DefaultMetadataHeader left;

        @Parameterized.Parameter(1)
        public DefaultMetadataHeader right;

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
