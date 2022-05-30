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
package org.eclipse.ditto.edge.service.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.things.model.ThingId;
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
    private static final ThingId THING_ID = ThingId.of(NAMESPACE, NAME);
    private static final ThingPlaceholder UNDER_TEST = ImmutableThingPlaceholder.INSTANCE;

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
        assertThat(UNDER_TEST.resolveValues(THING_ID, "id")).contains(THING_ID.toString());
    }

    @Test
    public void testReplaceThingName() {
        assertThat(UNDER_TEST.resolveValues(THING_ID, "name")).contains(NAME);
    }

    @Test
    public void testReplaceThingNamespace() {
        assertThat(UNDER_TEST.resolveValues(THING_ID, "namespace")).contains(NAMESPACE);
    }

    @Test
    public void testUnknownPlaceholderReturnsEmpty() {
        assertThat(UNDER_TEST.resolveValues(THING_ID, "thing_id")).isEmpty();
    }

    @Test
    public void testResolvingWithNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> UNDER_TEST.resolveValues(THING_ID, null));
    }

    @Test
    public void testResolvingWithEmptyString() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> UNDER_TEST.resolveValues(THING_ID, ""));
    }

}
