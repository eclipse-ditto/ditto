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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.SUPPORT_LABEL;

import java.time.Instant;
import java.util.Arrays;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntriesModified;

/**
 * Tests {@link org.eclipse.ditto.policies.service.persistence.actors.strategies.events.PolicyEntriesModifiedStrategy}.
 */
public class PolicyEntriesModifiedStrategyTest extends AbstractPolicyEventStrategyTest<PolicyEntriesModified> {

    private static final PolicyEntry MODIFIED_1 = TestConstants.Policy.policyEntryWithLabel("modified1");
    private static final PolicyEntry MODIFIED_2 = TestConstants.Policy.policyEntryWithLabel("modified2");

    @Override
    PolicyEntriesModifiedStrategy getStrategyUnderTest() {
        return new PolicyEntriesModifiedStrategy();
    }

    @Override
    PolicyEntriesModified getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        return PolicyEntriesModified.of(policyId, Arrays.asList(MODIFIED_1, MODIFIED_2), 10L, instant,
                DittoHeaders.empty(), METADATA);
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        assertThat(policyWithEventApplied.getEntryFor(MODIFIED_1.getLabel())).contains(MODIFIED_1);
        assertThat(policyWithEventApplied.getEntryFor(MODIFIED_2.getLabel())).contains(MODIFIED_2);
        assertThat(policyWithEventApplied.getEntryFor(SUPPORT_LABEL)).isEmpty();
        assertThat(policyWithEventApplied).containsExactlyInAnyOrder(MODIFIED_1, MODIFIED_2);
    }
}
