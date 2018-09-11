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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link EntityTagMatcher}
 */
public class EntityTagMatcherTest {

    private static final EntityTagMatcher ASTERISK = EntityTagMatcher.asterisk();
    private static final EntityTag WEAK_1 = EntityTag.fromString("W/\"1\"");
    private static final EntityTag WEAK_2 = EntityTag.fromString("W/\"2\"");
    private static final EntityTag STRONG_1 = EntityTag.fromString("\"1\"");
    private static final EntityTag STRONG_2 = EntityTag.fromString("\"2\"");

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(EntityTagMatcher.class)
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(EntityTagMatcher.class, areImmutable());
    }

    @Test
    public void asteriskFromString() {
        final String asteriskStr = "*";
        assertThat(EntityTagMatcher.isValid(asteriskStr)).isTrue();

        final EntityTagMatcher entityTagMatcherFromString = EntityTagMatcher.fromString(asteriskStr);
        assertThat(entityTagMatcherFromString.isAsterisk()).isTrue();
        assertThat(entityTagMatcherFromString).isEqualTo(ASTERISK);
        // there should be only one instance of ASTERISK
        assertThat(entityTagMatcherFromString).isSameAs(ASTERISK);
    }

    @Test
    public void weakMatchEvaluatesAlwaysToTrueIfMatcherIsAsterisk() {
        assertThat(ASTERISK.weakMatch(STRONG_1)).isTrue();
        assertThat(ASTERISK.weakMatch(WEAK_1)).isTrue();
        assertThat(ASTERISK.weakMatch(WEAK_2)).isTrue();
        assertThat(ASTERISK.weakMatch(STRONG_2)).isTrue();
    }

    @Test
    public void strongMatchEvaluatesAlwaysToTrueIfMatcherIsAsterisk() {

        assertThat(ASTERISK.strongMatch(STRONG_1)).isTrue();
        assertThat(ASTERISK.strongMatch(WEAK_1)).isTrue();
        assertThat(ASTERISK.strongMatch(WEAK_2)).isTrue();
        assertThat(ASTERISK.strongMatch(STRONG_2)).isTrue();
    }

    @Test
    public void toStringTest() {
        assertThat(ASTERISK.toString()).isEqualTo("*");
        assertThat(EntityTagMatcher.fromString("W/\"1\"").toString()).isEqualTo("W/\"1\"");
    }
}
