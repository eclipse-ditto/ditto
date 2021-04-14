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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.policies.persistence.TestConstants;
import org.eclipse.ditto.signals.events.policies.PolicyEntryCreated;

/**
 * Tests {@link PolicyEntryCreatedStrategy}.
 */
public class PolicyEntryCreatedStrategyTest extends AbstractPolicyEventStrategyTest<PolicyEntryCreated> {

    private static final PolicyEntry CREATED = TestConstants.Policy.policyEntryWithLabel("created");

    @Override
    PolicyEntryCreatedStrategy getStrategyUnderTest() {
        return new PolicyEntryCreatedStrategy();
    }

    @Override
    PolicyEntryCreated getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        return PolicyEntryCreated.of(policyId, CREATED, 10L, instant, DittoHeaders.empty(), METADATA);
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        assertThat(policyWithEventApplied.getEntryFor(CREATED.getLabel())).contains(CREATED);
    }
}