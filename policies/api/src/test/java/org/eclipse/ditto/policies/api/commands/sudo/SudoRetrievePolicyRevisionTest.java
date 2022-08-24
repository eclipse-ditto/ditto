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
package org.eclipse.ditto.policies.api.commands.sudo;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.policies.api.TestConstants;
import org.eclipse.ditto.policies.model.PolicyId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link SudoRetrievePolicyRevision}.
 */
public final class SudoRetrievePolicyRevisionTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrievePolicyRevision.class, areImmutable(),
                provided(JsonFieldSelector.class, PolicyId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoRetrievePolicyRevision.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void testSerialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();

        final SudoRetrievePolicyRevision underTest =
                SudoRetrievePolicyRevision.of(TestConstants.Policy.POLICY_ID, dittoHeaders);

        final SudoRetrievePolicyRevision deserialized =
                SudoRetrievePolicyRevision.fromJson(underTest.toJsonString(), dittoHeaders);

        assertThat(deserialized).isEqualTo(underTest);
    }

}
