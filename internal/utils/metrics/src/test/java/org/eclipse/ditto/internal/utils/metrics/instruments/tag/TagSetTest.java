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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link TagSet}.
 */
public final class TagSetTest {

    private static final Tag KNOWN_TAG_1 = Tag.of("marco", "polo2");
    private static final Tag KNOWN_TAG_2 = Tag.of("foo", "bar");
    private static final Tag KNOWN_TAG_3 = Tag.of("bar", "baz");

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(
                TagSet.class,
                areImmutable(),
                assumingFields("tagMap").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TagSet.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void emptyReturnsEmptyInstance() {
        assertThat(TagSet.empty()).isEmpty();
    }

    @Test
    public void ofTagWithNullTagThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> TagSet.ofTag(null))
                .withMessage("The tag must not be null!")
                .withNoCause();
    }

    @Test
    public void ofTagReturnsExpected() {
        final var underTest = TagSet.ofTag(KNOWN_TAG_1);

        assertThat(underTest).containsOnly(KNOWN_TAG_1);
    }

    @Test
    public void ofTagCollectionWithNullCollectionThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> TagSet.ofTagCollection(null))
                .withMessage("The tagCollection must not be null!")
                .withNoCause();
    }

    @Test
    public void ofTagCollectionReturnsExpected() {
        final var tagList = List.of(KNOWN_TAG_1, KNOWN_TAG_2, KNOWN_TAG_3);
        final var underTest = TagSet.ofTagCollection(tagList);

        assertThat(underTest).hasSameElementsAs(tagList);
    }

    @Test
    public void putTagWithNullTagThrowsNullPointerException() {
        final var underTest = TagSet.empty();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.putTag(null))
                .withMessage("The tag must not be null!")
                .withNoCause();
    }

    @Test
    public void putTagWorksAsExpected() {
        final var underTest = TagSet.ofTag(Tag.of(KNOWN_TAG_1.getKey(), "myValue"));

        final var actual = underTest.putTag(KNOWN_TAG_1);

        softly.assertThat(actual).isNotSameAs(underTest);
        softly.assertThat(actual).containsOnly(KNOWN_TAG_1);
    }

    @Test
    public void putAllTagsWithNullTagSetThrowsNullPointerException() {
        final var underTest = TagSet.empty();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.putAllTags(null))
                .withMessage("The tagSet must not be null!")
                .withNoCause();
    }

    @Test
    public void putAllTagsWorksAsExpected() {
        final var otherTagSet = TagSet.ofTagCollection(List.of(KNOWN_TAG_1, KNOWN_TAG_2, KNOWN_TAG_3));
        final var underTest = TagSet.ofTag(Tag.of(KNOWN_TAG_1.getKey(), "myValue"));

        final var actual = underTest.putAllTags(otherTagSet);

        softly.assertThat(actual).isNotSameAs(underTest);
        softly.assertThat(actual).isNotSameAs(otherTagSet);
        softly.assertThat(actual).isEqualTo(otherTagSet);
        softly.assertThat(underTest.putAllTags(TagSet.empty())).isSameAs(underTest);
    }

    @Test
    public void containsKeyReturnsExpected() {
        final var underTest = TagSet.ofTag(KNOWN_TAG_1);

        softly.assertThat(underTest.containsKey(KNOWN_TAG_1.getKey())).as("key is contained").isTrue();
        softly.assertThat(underTest.containsKey(KNOWN_TAG_2.getKey())).as("key is absent").isFalse();
    }

    @Test
    public void getTagValueReturnsExpected() {
        final var underTest = TagSet.ofTag(KNOWN_TAG_1);

        softly.assertThat(underTest.getTagValue(KNOWN_TAG_1.getKey()))
                .as("present value")
                .hasValue(KNOWN_TAG_1.getValue());
        softly.assertThat(underTest.getTagValue(KNOWN_TAG_2.getKey()))
                .as("absent value")
                .isEmpty();
    }

}