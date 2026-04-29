/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryReferencesDeleted;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Test;

/**
 * Tests {@link PolicyEntryReferencesDeletedStrategy}.
 */
public class PolicyEntryReferencesDeletedStrategyTest
        extends AbstractPolicyEventStrategyTest<PolicyEntryReferencesDeleted> {

    private static final Label LABEL = TestConstants.Policy.SUPPORT_LABEL;

    @Override
    PolicyEntryReferencesDeletedStrategy getStrategyUnderTest() {
        return new PolicyEntryReferencesDeletedStrategy();
    }

    @Override
    PolicyEntryReferencesDeleted getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        return PolicyEntryReferencesDeleted.of(policyId, LABEL, 10L, instant,
                DittoHeaders.empty(), null);
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        assertThat(policyWithEventApplied.getEntryFor(LABEL))
                .hasValueSatisfying(entry -> assertThat(entry.getReferences()).isEmpty());
    }

    @Test
    public void clearsExistingReferencesOnEntry() {
        final Policy policyWithReferences = TestConstants.Policy.policyWithRandomName()
                .toBuilder()
                .setReferencesFor(LABEL, List.of(
                        PoliciesModelFactory.newEntryReference(
                                TestConstants.Policy.POLICY_IMPORT_ID, Label.of("IncludedLabel")),
                        PoliciesModelFactory.newLocalEntryReference(Label.of("EndUser"))))
                .build();
        final PolicyId policyId = policyWithReferences.getEntityId().orElseThrow();
        final PolicyEntryReferencesDeleted event = PolicyEntryReferencesDeleted.of(policyId, LABEL, 11L,
                getInstant(), DittoHeaders.empty(), null);

        final Policy result = getStrategyUnderTest().handle(event, policyWithReferences, 11L);

        assertThat(result).isNotNull();
        assertThat(result.getEntryFor(LABEL))
                .hasValueSatisfying(entry -> assertThat(entry.getReferences()).isEmpty());
    }
}
