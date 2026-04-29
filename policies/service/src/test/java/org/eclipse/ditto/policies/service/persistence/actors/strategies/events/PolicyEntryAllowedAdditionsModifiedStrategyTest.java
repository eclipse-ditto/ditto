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
import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.AllowedAddition;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryAllowedAdditionsModified;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Test;

/**
 * Tests {@link PolicyEntryAllowedAdditionsModifiedStrategy}.
 */
public class PolicyEntryAllowedAdditionsModifiedStrategyTest
        extends AbstractPolicyEventStrategyTest<PolicyEntryAllowedAdditionsModified> {

    private static final Label LABEL = TestConstants.Policy.SUPPORT_LABEL;
    private static final Set<AllowedAddition> ALLOWED_ADDITIONS =
            EnumSet.of(AllowedAddition.SUBJECTS, AllowedAddition.NAMESPACES);

    @Override
    PolicyEntryAllowedAdditionsModifiedStrategy getStrategyUnderTest() {
        return new PolicyEntryAllowedAdditionsModifiedStrategy();
    }

    @Override
    PolicyEntryAllowedAdditionsModified getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        return PolicyEntryAllowedAdditionsModified.of(policyId, LABEL, ALLOWED_ADDITIONS, 10L, instant,
                DittoHeaders.empty(), null);
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        assertThat(policyWithEventApplied.getEntryFor(LABEL))
                .hasValueSatisfying(entry -> assertThat(entry.getAllowedAdditions())
                        .contains(ALLOWED_ADDITIONS));
    }

    @Test
    public void overwritesExistingAllowedAdditionsOnEntry() {
        final Policy policyWithExistingAdditions = TestConstants.Policy.policyWithRandomName()
                .toBuilder()
                .setAllowedAdditionsFor(LABEL, EnumSet.allOf(AllowedAddition.class))
                .build();
        final PolicyId policyId = policyWithExistingAdditions.getEntityId().orElseThrow();
        final Set<AllowedAddition> replacement = EnumSet.of(AllowedAddition.RESOURCES);
        final PolicyEntryAllowedAdditionsModified event = PolicyEntryAllowedAdditionsModified.of(policyId,
                LABEL, replacement, 11L, getInstant(), DittoHeaders.empty(), null);

        final Policy result = getStrategyUnderTest().handle(event, policyWithExistingAdditions, 11L);

        assertThat(result).isNotNull();
        assertThat(result.getEntryFor(LABEL))
                .hasValueSatisfying(entry -> assertThat(entry.getAllowedAdditions()).contains(replacement));
    }

    @Test
    public void emptySetIsAppliedAsExplicitDenyAll() {
        final Policy basePolicy = TestConstants.Policy.policyWithRandomName();
        final PolicyId policyId = basePolicy.getEntityId().orElseThrow();
        final Set<AllowedAddition> empty = EnumSet.noneOf(AllowedAddition.class);
        final PolicyEntryAllowedAdditionsModified event = PolicyEntryAllowedAdditionsModified.of(policyId,
                LABEL, empty, 11L, getInstant(), DittoHeaders.empty(), null);

        final Policy result = getStrategyUnderTest().handle(event, basePolicy, 11L);

        assertThat(result).isNotNull();
        assertThat(result.getEntryFor(LABEL))
                .hasValueSatisfying(entry -> assertThat(entry.getAllowedAdditions()).contains(empty));
    }
}
