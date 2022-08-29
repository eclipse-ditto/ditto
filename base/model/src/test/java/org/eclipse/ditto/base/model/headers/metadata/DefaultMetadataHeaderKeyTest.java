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
 * Unit tests for {@link org.eclipse.ditto.base.model.headers.metadata.DefaultMetadataHeaderKey}.
 */
public final class DefaultMetadataHeaderKeyTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultMetadataHeaderKey.class,
                areImmutable(),
                provided(JsonPointer.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultMetadataHeaderKey.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToGetInstanceWithNullPath() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultMetadataHeaderKey.of(null))
                .withMessage("The path must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithEmptyPath() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DefaultMetadataHeaderKey.of(JsonPointer.empty()))
                .withMessage("The path of a metadata header key must not be empty!")
                .withNoCause();
    }

    @Test
    public void tryToParseNullString() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultMetadataHeaderKey.parse(null))
                .withMessage("The key must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToParseEmptyString() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DefaultMetadataHeaderKey.parse(""))
                .withMessage("The argument 'key' must not be empty!")
                .withNoCause();
    }

    @Test
    public void parseValidString() {
        final JsonPointer path = JsonPointer.of("/foo/bar");

        final DefaultMetadataHeaderKey parsed = DefaultMetadataHeaderKey.parse(path);

        assertThat(parsed).isEqualTo(DefaultMetadataHeaderKey.of(path));
    }

    @Test
    public void getSpecificPath() {
        final JsonPointer path = JsonPointer.of("/foo/bar/baz");
        final MetadataHeaderKey underTest = DefaultMetadataHeaderKey.of(path);

        assertThat((CharSequence) underTest.getPath()).isEqualTo(path);
    }

    @Test
    public void getPathOfWildcardKey() {
        final JsonKey leaf = JsonKey.of("baz");
        final JsonPointer path = JsonFactory.newPointer(DefaultMetadataHeaderKey.HIERARCHY_WILDCARD, leaf);
        final MetadataHeaderKey underTest = DefaultMetadataHeaderKey.of(path);

        assertThat((CharSequence) underTest.getPath()).isEqualTo(path);
    }

    @Test
    public void pathWithoutLeadingAsteriskDoesNotApplyToAllLeaves() {
        final JsonPointer path = JsonPointer.of("/foo/bar/baz");
        final MetadataHeaderKey underTest = DefaultMetadataHeaderKey.of(path);

        assertThat(underTest.appliesToAllLeaves()).isFalse();
    }

    @Test
    public void pathWithLeadingAsteriskAppliesToAllLeaves() {
        final JsonPointer path = JsonPointer.of("/*/baz");
        final MetadataHeaderKey underTest = DefaultMetadataHeaderKey.of(path);

        assertThat(underTest.appliesToAllLeaves()).isTrue();
    }

    @Test
    public void toStringReturnsExpected() {
        final JsonPointer path = JsonPointer.of("/foo/bar/baz");
        final DefaultMetadataHeaderKey underTest = DefaultMetadataHeaderKey.of(path);

        assertThat(underTest).hasToString(path.toString());
    }

    @Test
    public void compareToWorksAsExpected() {
        final DefaultMetadataHeaderKey keyWithSpecificPathB = DefaultMetadataHeaderKey.of(JsonPointer.of("/foo/bar/baz"));
        final DefaultMetadataHeaderKey keyWithWildcardPathB = DefaultMetadataHeaderKey.of(JsonPointer.of("/*/baz"));
        final DefaultMetadataHeaderKey keyWithSpecificPathC = DefaultMetadataHeaderKey.of(JsonPointer.of("/foo/bar/chumble"));
        final DefaultMetadataHeaderKey keyWithWildcardPathC = DefaultMetadataHeaderKey.of(JsonPointer.of("/*/chumble"));

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
