/*
 *  Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 *  SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.ditto.services.models.connectivity.placeholder;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link ImmutableThingPlaceholder}.
 */
public class ImmutableThingPlaceholderTest {

    private static final String NAME = "ditto";
    private static final String NAMESPACE = "eclipse";
    private static final String THING_ID = NAMESPACE + ":" + NAME;
    private static final ImmutableThingPlaceholder UNDER_TEST = ImmutableThingPlaceholder.INSTANCE;

    /**
     * Assert immutability.
     */
    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutableThingPlaceholder.class, MutabilityMatchers.areImmutable());
    }

    /**
     * Test hash code and equals.
     */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableThingPlaceholder.class)
                .suppress(Warning.INHERITED_DIRECTLY_FROM_OBJECT)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testReplaceThingId() {
        assertThat(UNDER_TEST.apply(THING_ID, "id")).contains(THING_ID);
    }

    @Test
    public void testReplaceThingName() {
        assertThat(UNDER_TEST.apply(THING_ID, "name")).contains(NAME);
    }

    @Test
    public void testReplaceThingNamespace() {
        assertThat(UNDER_TEST.apply(THING_ID, "namespace")).contains(NAMESPACE);
    }

    @Test(expected = ThingIdInvalidException.class)
    public void testInvalidThingIdThrowsException() {
        UNDER_TEST.apply("ditto", "id");
    }

    @Test
    public void testUnknownPlaceholderReturnsEmpty() {
        assertThat(UNDER_TEST.apply(THING_ID, "thing_id")).isEmpty();
    }

}