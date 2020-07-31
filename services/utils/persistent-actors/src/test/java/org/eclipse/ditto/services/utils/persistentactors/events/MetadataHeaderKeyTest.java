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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link MetadataHeaderKey}.
 */
public final class MetadataHeaderKeyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(MetadataHeaderKey.class, areImmutable(), provided(JsonPointer.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MetadataHeaderKey.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToGetInstanceWithNullPath() {
        assertThatNullPointerException()
                .isThrownBy(() -> MetadataHeaderKey.of(null))
                .withMessage("The path must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithEmptyPath() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MetadataHeaderKey.of(JsonPointer.empty()))
                .withMessage("The path of a metadata key must not be empty!")
                .withNoCause();
    }

    @Test
    public void wildcardPathHasTooFewLevels() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MetadataHeaderKey.of(JsonPointer.of("/*")))
                .withMessage("A wildcard metadata key path must have exactly two levels but it had <1>!")
                .withNoCause();
    }

    @Test
    public void wildcardPathHasTooManyLevels() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MetadataHeaderKey.of(JsonPointer.of("/*/foo/meta")))
                .withMessage("A wildcard metadata key path must have exactly two levels but it had <3>!")
                .withNoCause();
    }

    @Test
    public void wildcardPathHasAsteriskLeaf() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MetadataHeaderKey.of(JsonPointer.of("/*/*")))
                .withMessage("A metadata key path must not contain <*> at level <1>!")
                .withNoCause();
    }

    @Test
    public void pathContainsAsteriskAtWrongLevel() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MetadataHeaderKey.of(JsonPointer.of("/foo/*/baz")))
                .withMessage("A metadata key path must not contain <*> at level <1>!")
                .withNoCause();
    }

    @Test
    public void getSpecificPath() {
        final JsonPointer path = JsonPointer.of("/foo/bar/baz");
        final MetadataHeaderKey underTest = MetadataHeaderKey.of(path);

        assertThat((CharSequence) underTest.getPath()).isEqualTo(path);
    }

    @Test
    public void getPathOfWildcardKey() {
        final JsonKey leaf = JsonKey.of("baz");
        final JsonPointer path = JsonFactory.newPointer(MetadataHeaderKey.HIERARCHY_WILDCARD, leaf);
        final MetadataHeaderKey underTest = MetadataHeaderKey.of(path);

        assertThat((CharSequence) underTest.getPath()).isEqualTo(leaf.asPointer());
    }

    @Test
    public void pathWithoutLeadingAsteriskDoesNotApplyToAllLeaves() {
        final JsonPointer path = JsonPointer.of("/foo/bar/baz");
        final MetadataHeaderKey underTest = MetadataHeaderKey.of(path);

        assertThat(underTest.appliesToAllLeaves()).isFalse();
    }

    @Test
    public void pathWithLeadingAsteriskAppliesToAllLeaves() {
        final JsonPointer path = JsonPointer.of("/*/baz");
        final MetadataHeaderKey underTest = MetadataHeaderKey.of(path);

        assertThat(underTest.appliesToAllLeaves()).isTrue();
    }

    @Test
    public void asStringReturnsExpected() {
        final JsonPointer path = JsonPointer.of("/foo/bar/baz");
        final MetadataHeaderKey underTest = MetadataHeaderKey.of(path);

        assertThat(underTest.asString()).isEqualTo(MetadataHeaderKey.PREFIX + path);
    }

    @Test
    public void compareToWorksAsExpected() {
        final MetadataHeaderKey keyWithSpecificPathB = MetadataHeaderKey.of(JsonPointer.of("/foo/bar/baz"));
        final MetadataHeaderKey keyWithWildcardPathB = MetadataHeaderKey.of(JsonPointer.of("/*/baz"));
        final MetadataHeaderKey keyWithSpecificPathC = MetadataHeaderKey.of(JsonPointer.of("/foo/bar/chumble"));
        final MetadataHeaderKey keyWithWildcardPathC = MetadataHeaderKey.of(JsonPointer.of("/*/chumble"));

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(keyWithSpecificPathB.compareTo(keyWithSpecificPathB))
                    .as("[specific path/specific path] key is equal to itself")
                    .isZero();
            softly.assertThat(keyWithSpecificPathB)
                    .as("[specific path/specific path] key is less than other key")
                    .isLessThan(keyWithSpecificPathC);
            softly.assertThat(keyWithSpecificPathC)
                    .as("[specific path/specific/path] key is greater than other key")
                    .isGreaterThan(keyWithSpecificPathB);
            softly.assertThat(keyWithWildcardPathB.compareTo(keyWithWildcardPathB))
                    .as("[wildcard path/wildcard path] key is equal to itself")
                    .isZero();
            softly.assertThat(keyWithWildcardPathB)
                    .as("[wildcard path/wildcard path] key is less than other key")
                    .isLessThan(keyWithWildcardPathC);
            softly.assertThat(keyWithWildcardPathB)
                    .as("[wildcard path/wildcard path] key is greater than other key")
                    .isLessThan(keyWithSpecificPathB);
            softly.assertThat(keyWithWildcardPathC)
                    .as("[wildcard path/specific path] key is less than other key")
                    .isLessThan(keyWithSpecificPathB);
            softly.assertThat(keyWithSpecificPathB)
                    .as("[specific path/wildcard path] key is greater than other key")
                    .isGreaterThan(keyWithWildcardPathC);
            softly.assertThat(keyWithSpecificPathC)
                    .as("[specific path/wildcard path] key is greater than other key")
                    .isGreaterThan(keyWithWildcardPathC);
        }
    }

}
