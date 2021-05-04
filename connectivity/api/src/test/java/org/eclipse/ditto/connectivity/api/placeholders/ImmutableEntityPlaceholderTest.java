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
package org.eclipse.ditto.connectivity.api.placeholders;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link ImmutableEntityIdPlaceholder}.
 */
public class ImmutableEntityPlaceholderTest {

    private static final String NAME = "ditto";
    private static final String NAMESPACE = "eclipse";
    private static final PolicyId POLICY_ID = PolicyId.of(NAMESPACE, NAME);
    private static final ThingId THING_ID = ThingId.of(NAMESPACE, NAME);
    private static final EntityIdPlaceholder UNDER_TEST = ImmutableEntityIdPlaceholder.INSTANCE;

    /**
     * Assert immutability.
     */
    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ImmutablePolicyPlaceholder.class, MutabilityMatchers.areImmutable());
    }

    /**
     * Test hash code and equals.
     */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePolicyPlaceholder.class)
                .suppress(Warning.INHERITED_DIRECTLY_FROM_OBJECT)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testReplacePolicyId() {
        assertThat(UNDER_TEST.resolve(POLICY_ID, "id")).contains(POLICY_ID.toString());
    }

    @Test
    public void testReplaceThingId() {
        assertThat(UNDER_TEST.resolve(THING_ID, "id")).contains(THING_ID.toString());
    }

    @Test
    public void testReplacePolicyName() {
        assertThat(UNDER_TEST.resolve(POLICY_ID, "name")).contains(NAME);
    }

    @Test
    public void testReplaceThingName() {
        assertThat(UNDER_TEST.resolve(THING_ID, "name")).contains(NAME);
    }

    @Test
    public void testReplacePolicyNamespace() {
        assertThat(UNDER_TEST.resolve(POLICY_ID, "namespace")).contains(NAMESPACE);
    }

    @Test
    public void testReplaceThingNamespace() {
        assertThat(UNDER_TEST.resolve(THING_ID, "namespace")).contains(NAMESPACE);
    }

    @Test
    public void testUnknownPlaceholderReturnsEmpty() {
        assertThat(UNDER_TEST.resolve(POLICY_ID, "policy_id")).isEmpty();
    }

}
