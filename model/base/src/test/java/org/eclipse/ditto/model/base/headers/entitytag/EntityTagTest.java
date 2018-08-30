/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */

package org.eclipse.ditto.model.base.headers.entitytag;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.Test;

public class EntityTagTest {

    private static final EntityTag WEAK_1 = EntityTag.fromString("W/\"1\"");
    private static final EntityTag WEAK_2 = EntityTag.fromString("W/\"2\"");
    private static final EntityTag STRONG_1 = EntityTag.fromString("\"1\"");
    private static final EntityTag STRONG_2 = EntityTag.fromString("\"2\"");

    @Test
    public void weakEntityTagFromString() {
        final String validOpaqueTag = "\"hallo\"";
        final String validWeakEntityTag = "W/" + validOpaqueTag;

        assertThat(EntityTag.validate(validWeakEntityTag)).isTrue();

        final EntityTag entityTagFromString = EntityTag.fromString(validWeakEntityTag);
        assertThat(entityTagFromString.isWeak()).isTrue();
        assertThat(entityTagFromString.getOpaqueTag()).isEqualTo(validOpaqueTag);

        final EntityTag entityTagWeak = EntityTag.weak(validOpaqueTag);
        assertThat(entityTagWeak.isWeak()).isTrue();
        assertThat(entityTagWeak.getOpaqueTag()).isEqualTo(validOpaqueTag);
    }

    @Test
    public void strongEntityTagFromString() {
        final String validStrongEntityTag = "\"hallo\"";

        assertThat(EntityTag.validate(validStrongEntityTag)).isTrue();

        final EntityTag entityTag = EntityTag.fromString(validStrongEntityTag);
        assertThat(entityTag.isWeak()).isFalse();
        assertThat(entityTag.getOpaqueTag()).isEqualTo(validStrongEntityTag);

        final EntityTag entityTagWeak = EntityTag.strong(validStrongEntityTag);
        assertThat(entityTagWeak.isWeak()).isFalse();
        assertThat(entityTagWeak.getOpaqueTag()).isEqualTo(validStrongEntityTag);
    }

    @Test
    public void weakPrefixIsCaseSensitive() {
        final String invalidEntityTag = "w/\"hallo\"";

        assertThat(EntityTag.validate(invalidEntityTag)).isFalse();
        assertIllegalArgumentExceptionWhenCreatingFromString(invalidEntityTag);
    }

    @Test
    public void weakEntityTagMustNotContainAsteriskInOpaqueTag() {
        final String invalidEntityTag = "w/\"*\"";

        assertThat(EntityTag.validate(invalidEntityTag)).isFalse();
        assertIllegalArgumentExceptionWhenCreatingFromString(invalidEntityTag);
    }


    @Test
    public void strongEntityTagMustNotContainAsteriskInOpaqueTag() {
        final String invalidEntityTag = "\"*\"";

        assertThat(EntityTag.validate(invalidEntityTag)).isFalse();
        assertIllegalArgumentExceptionWhenCreatingFromString(invalidEntityTag);
    }

    @Test
    public void weakEntityTagOpaqueTagMustStartWithDoubleQuotes() {
        final String invalidEntityTag = "W/hallo\"";
        assertThat(EntityTag.validate(invalidEntityTag)).isFalse();
        assertIllegalArgumentExceptionWhenCreatingFromString(invalidEntityTag, "hallo\"");
    }

    @Test
    public void strongEntityTagOpaqueTagMustStartWithDoubleQuotes() {
        final String invalidEntityTag = "hallo\"";
        assertThat(EntityTag.validate(invalidEntityTag)).isFalse();
        assertIllegalArgumentExceptionWhenCreatingFromString(invalidEntityTag);
    }

    @Test
    public void weakEntityTagOpaqueTagMustEndWithDoubleQuotes() {
        final String invalidEntityTag = "W/\"hallo";
        assertThat(EntityTag.validate(invalidEntityTag)).isFalse();
        assertIllegalArgumentExceptionWhenCreatingFromString(invalidEntityTag, "\"hallo");
    }

    @Test
    public void strongEntityTagOpaqueTagMustEndWithDoubleQuotes() {
        final String invalidEntityTag = "\"hallo";
        assertThat(EntityTag.validate(invalidEntityTag)).isFalse();
        assertIllegalArgumentExceptionWhenCreatingFromString(invalidEntityTag);
    }

    @Test
    public void weakEntityTagOpaqueTagMustNotContainMoreThanTwoDoubleQuotes() {
        final String invalidEntityTag = "\"\"W/\\\"hal\\\"l\\\"o\\\"\"";
        assertThat(EntityTag.validate(invalidEntityTag)).isFalse();
        assertIllegalArgumentExceptionWhenCreatingFromString(invalidEntityTag);
    }

    @Test
    public void strongEntityTagOpaqueTagMustNotContainMoreThanTwoDoubleQuotes() {
        final String invalidEntityTag = "\"hal\"l\"o\"";
        assertThat(EntityTag.validate(invalidEntityTag)).isFalse();
        assertIllegalArgumentExceptionWhenCreatingFromString(invalidEntityTag);
    }

    @Test
    public void strongComparisonEvaluatesToFalseForEqualWeakTags() {
        assertThat(WEAK_1.strongCompareTo(WEAK_1)).isFalse();
    }

    @Test
    public void strongComparisonEvaluatesToFalseForDifferentWeakTags() {
        assertThat(WEAK_1.strongCompareTo(WEAK_2)).isFalse();
    }

    @Test
    public void strongComparisonEvaluatesToFalseForOneStrongTagWithSameValueThanWeakTag() {
        assertThat(WEAK_1.strongCompareTo(STRONG_1)).isFalse();
    }

    @Test
    public void strongComparisonEvaluatesToFalseForDifferentStrongTags() {
        assertThat(STRONG_1.strongCompareTo(STRONG_2)).isFalse();
    }

    @Test
    public void strongComparisonEvaluatesToTrueForEqualStrongTags() {
        assertThat(STRONG_1.strongCompareTo(STRONG_1)).isTrue();
    }

    @Test
    public void weakComparisonEvaluatesToTrueForEqualWeakTags() {
        assertThat(WEAK_1.weakCompareTo(WEAK_1)).isTrue();
    }

    @Test
    public void weakComparisonEvaluatesToFalseForDifferentWeakTags() {
        assertThat(WEAK_1.weakCompareTo(WEAK_2)).isFalse();
    }

    @Test
    public void weakComparisonEvaluatesToTrueForOneStrongTagWithSameValueThanWeakTag() {
        assertThat(WEAK_1.weakCompareTo(STRONG_1)).isTrue();
    }

    @Test
    public void weakComparisonEvaluatesToFalseForDifferentStrongTags() {
        assertThat(STRONG_1.weakCompareTo(STRONG_2)).isFalse();
    }

    @Test
    public void weakComparisonEvaluatesToTrueForEqualStrongTags() {
        assertThat(STRONG_1.weakCompareTo(STRONG_1)).isTrue();
    }

    private void assertIllegalArgumentExceptionWhenCreatingFromString(final String invalidEntityTagValue) {
        assertIllegalArgumentExceptionWhenCreatingFromString(invalidEntityTagValue, invalidEntityTagValue);
    }

    private void assertIllegalArgumentExceptionWhenCreatingFromString(final String invalidEntityTagValue,
            final String expectedOpaqueTagInMessage) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> EntityTag.fromString(invalidEntityTagValue))
                .withMessage("The opaque tag <%s> is not a valid entity-tag.", expectedOpaqueTagInMessage)
                .withNoCause();
    }
}