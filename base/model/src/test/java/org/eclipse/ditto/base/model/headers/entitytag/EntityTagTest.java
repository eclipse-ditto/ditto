/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers.entitytag;


import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag}.
 */
public final class EntityTagTest {

    private static final EntityTag WEAK_1 = EntityTag.fromString("W/\"1\"");
    private static final EntityTag WEAK_2 = EntityTag.fromString("W/\"2\"");
    private static final EntityTag STRONG_1 = EntityTag.fromString("\"1\"");
    private static final EntityTag STRONG_2 = EntityTag.fromString("\"2\"");

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(EntityTag.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(EntityTag.class, areImmutable());
    }

    @Test
    public void weakEntityTagFromString() {
        final String validOpaqueTag = "\"hallo\"";
        final String validWeakEntityTag = "W/" + validOpaqueTag;

        softly.assertThat(EntityTag.isValid(validWeakEntityTag)).isTrue();

        final EntityTag entityTagFromString = EntityTag.fromString(validWeakEntityTag);
        softly.assertThat(entityTagFromString.isWeak()).isTrue();
        softly.assertThat(entityTagFromString.getOpaqueTag()).isEqualTo(validOpaqueTag);

        final EntityTag entityTagWeak = EntityTag.weak(validOpaqueTag);
        softly.assertThat(entityTagWeak.isWeak()).isTrue();
        softly.assertThat(entityTagWeak.getOpaqueTag()).isEqualTo(validOpaqueTag);
    }

    @Test
    public void strongEntityTagFromString() {
        final String validStrongEntityTag = "\"hallo\"";

        softly.assertThat(EntityTag.isValid(validStrongEntityTag)).isTrue();

        final EntityTag entityTag = EntityTag.fromString(validStrongEntityTag);
        softly.assertThat(entityTag.isWeak()).isFalse();
        softly.assertThat(entityTag.getOpaqueTag()).isEqualTo(validStrongEntityTag);

        final EntityTag entityTagWeak = EntityTag.strong(validStrongEntityTag);
        softly.assertThat(entityTagWeak.isWeak()).isFalse();
        softly.assertThat(entityTagWeak.getOpaqueTag()).isEqualTo(validStrongEntityTag);
    }

    @Test
    public void weakPrefixIsCaseSensitive() {
        final String invalidEntityTag = "w/\"hallo\"";

        softly.assertThat(EntityTag.isValid(invalidEntityTag)).isFalse();
        assertExceptionWhenCreatingFromString(invalidEntityTag);
    }

    @Test
    public void weakEntityTagMustNotContainAsteriskInOpaqueTag() {
        final String invalidEntityTag = "w/\"*\"";

        softly.assertThat(EntityTag.isValid(invalidEntityTag)).isFalse();
        assertExceptionWhenCreatingFromString(invalidEntityTag);
    }

    @Test
    public void strongEntityTagMustNotContainAsteriskInOpaqueTag() {
        final String invalidEntityTag = "\"*\"";

        softly.assertThat(EntityTag.isValid(invalidEntityTag)).isFalse();
        assertExceptionWhenCreatingFromString(invalidEntityTag);
    }

    @Test
    public void weakEntityTagOpaqueTagMustStartWithDoubleQuotes() {
        final String invalidEntityTag = "W/hallo\"";
        softly.assertThat(EntityTag.isValid(invalidEntityTag)).isFalse();
        assertExceptionWhenCreatingFromString(invalidEntityTag, "hallo\"");
    }

    @Test
    public void strongEntityTagOpaqueTagMustStartWithDoubleQuotes() {
        final String invalidEntityTag = "hallo\"";
        softly.assertThat(EntityTag.isValid(invalidEntityTag)).isFalse();
        assertExceptionWhenCreatingFromString(invalidEntityTag);
    }

    @Test
    public void weakEntityTagOpaqueTagMustEndWithDoubleQuotes() {
        final String invalidEntityTag = "W/\"hallo";
        softly.assertThat(EntityTag.isValid(invalidEntityTag)).isFalse();
        assertExceptionWhenCreatingFromString(invalidEntityTag, "\"hallo");
    }

    @Test
    public void strongEntityTagOpaqueTagMustEndWithDoubleQuotes() {
        final String invalidEntityTag = "\"hallo";
        softly.assertThat(EntityTag.isValid(invalidEntityTag)).isFalse();
        assertExceptionWhenCreatingFromString(invalidEntityTag);
    }

    @Test
    public void weakEntityTagOpaqueTagMustNotContainMoreThanTwoDoubleQuotes() {
        final String invalidEntityTag = "\"\"W/\\\"hal\\\"l\\\"o\\\"\"";
        softly.assertThat(EntityTag.isValid(invalidEntityTag)).isFalse();
        assertExceptionWhenCreatingFromString(invalidEntityTag);
    }

    @Test
    public void strongEntityTagOpaqueTagMustNotContainMoreThanTwoDoubleQuotes() {
        final String invalidEntityTag = "\"hal\"l\"o\"";
        softly.assertThat(EntityTag.isValid(invalidEntityTag)).isFalse();
        assertExceptionWhenCreatingFromString(invalidEntityTag);
    }

    @Test
    public void strongComparisonEvaluatesToFalseForEqualWeakTags() {
        softly.assertThat(WEAK_1.strongCompareTo(WEAK_1)).isFalse();
    }

    @Test
    public void strongComparisonEvaluatesToFalseForDifferentWeakTags() {
        softly.assertThat(WEAK_1.strongCompareTo(WEAK_2)).isFalse();
    }

    @Test
    public void strongComparisonEvaluatesToFalseForOneStrongTagWithSameValueThanWeakTag() {
        softly.assertThat(WEAK_1.strongCompareTo(STRONG_1)).isFalse();
    }

    @Test
    public void strongComparisonEvaluatesToFalseForDifferentStrongTags() {
        softly.assertThat(STRONG_1.strongCompareTo(STRONG_2)).isFalse();
    }

    @Test
    public void strongComparisonEvaluatesToTrueForEqualStrongTags() {
        softly.assertThat(STRONG_1.strongCompareTo(STRONG_1)).isTrue();
    }

    @Test
    public void weakComparisonEvaluatesToTrueForEqualWeakTags() {
        softly.assertThat(WEAK_1.weakCompareTo(WEAK_1)).isTrue();
    }

    @Test
    public void weakComparisonEvaluatesToFalseForDifferentWeakTags() {
        softly.assertThat(WEAK_1.weakCompareTo(WEAK_2)).isFalse();
    }

    @Test
    public void weakComparisonEvaluatesToTrueForOneStrongTagWithSameValueThanWeakTag() {
        softly.assertThat(WEAK_1.weakCompareTo(STRONG_1)).isTrue();
    }

    @Test
    public void weakComparisonEvaluatesToFalseForDifferentStrongTags() {
        softly.assertThat(STRONG_1.weakCompareTo(STRONG_2)).isFalse();
    }

    @Test
    public void weakComparisonEvaluatesToTrueForEqualStrongTags() {
        softly.assertThat(STRONG_1.weakCompareTo(STRONG_1)).isTrue();
    }

    private static void assertExceptionWhenCreatingFromString(final String invalidEntityTagValue) {
        assertExceptionWhenCreatingFromString(invalidEntityTagValue, invalidEntityTagValue);
    }

    private static void assertExceptionWhenCreatingFromString(final String invalidEntityTagValue,
            final String expectedOpaqueTagInMessage) {

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> EntityTag.fromString(invalidEntityTagValue))
                .withMessage("The opaque tag <%s> is not a valid entity-tag.", expectedOpaqueTagInMessage);
    }

}
