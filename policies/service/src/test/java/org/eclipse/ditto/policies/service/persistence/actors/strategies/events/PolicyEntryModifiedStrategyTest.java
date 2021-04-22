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

import java.time.Instant;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.eclipse.ditto.policies.model.signals.events.PolicyEntryModified;

/**
 * Tests {@link org.eclipse.ditto.policies.service.persistence.actors.strategies.events.PolicyEntryModifiedStrategy}.
 */
public class PolicyEntryModifiedStrategyTest extends AbstractPolicyEventStrategyTest<PolicyEntryModified> {

    private static final PolicyEntry MODIFIED = TestConstants.Policy.policyEntryWithLabel("Support");

    @Override
    PolicyEntryModifiedStrategy getStrategyUnderTest() {
        return new PolicyEntryModifiedStrategy();
    }

    @Override
    PolicyEntryModified getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        return PolicyEntryModified.of(policyId, MODIFIED, 10L, instant, DittoHeaders.empty(), METADATA);
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        assertThat(policyWithEventApplied.getEntryFor(MODIFIED.getLabel())).contains(MODIFIED);
    }
}
