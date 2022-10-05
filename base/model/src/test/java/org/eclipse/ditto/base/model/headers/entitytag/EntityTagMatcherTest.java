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

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher}
 */
public final class EntityTagMatcherTest {

    private static final EntityTagMatcher ASTERISK = EntityTagMatcher.asterisk();
    private static final EntityTag WEAK_1 = EntityTag.fromString("W/\"1\"");
    private static final EntityTag WEAK_2 = EntityTag.fromString("W/\"2\"");
    private static final EntityTag STRONG_1 = EntityTag.fromString("\"1\"");
    private static final EntityTag STRONG_2 = EntityTag.fromString("\"2\"");

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(EntityTagMatcher.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(EntityTagMatcher.class, areImmutable());
    }

    @Test
    public void asteriskFromString() {
        final String asteriskStr = "*";
        softly.assertThat(EntityTagMatcher.isValid(asteriskStr)).isTrue();

        final EntityTagMatcher entityTagMatcherFromString = EntityTagMatcher.fromString(asteriskStr);
        softly.assertThat(entityTagMatcherFromString.isAsterisk()).isTrue();
        softly.assertThat(entityTagMatcherFromString).isEqualTo(ASTERISK);
        // there should be only one instance of ASTERISK
        softly.assertThat(entityTagMatcherFromString).isSameAs(ASTERISK);
    }

    @Test
    public void weakMatchEvaluatesAlwaysToTrueIfMatcherIsAsterisk() {
        softly.assertThat(ASTERISK.weakMatch(STRONG_1)).isTrue();
        softly.assertThat(ASTERISK.weakMatch(WEAK_1)).isTrue();
        softly.assertThat(ASTERISK.weakMatch(WEAK_2)).isTrue();
        softly.assertThat(ASTERISK.weakMatch(STRONG_2)).isTrue();
    }

    @Test
    public void strongMatchEvaluatesAlwaysToTrueIfMatcherIsAsterisk() {
        softly.assertThat(ASTERISK.strongMatch(STRONG_1)).isTrue();
        softly.assertThat(ASTERISK.strongMatch(WEAK_1)).isTrue();
        softly.assertThat(ASTERISK.strongMatch(WEAK_2)).isTrue();
        softly.assertThat(ASTERISK.strongMatch(STRONG_2)).isTrue();
    }

    @Test
    public void toStringTest() {
        softly.assertThat(ASTERISK.toString()).isEqualTo("*");
        softly.assertThat(EntityTagMatcher.fromString("W/\"1\"").toString()).isEqualTo("W/\"1\"");
    }

}
