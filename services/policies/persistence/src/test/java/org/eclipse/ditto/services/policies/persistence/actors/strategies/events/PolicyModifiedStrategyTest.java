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
import org.eclipse.ditto.services.policies.persistence.TestConstants;
import org.eclipse.ditto.signals.events.policies.PolicyModified;

/**
 * Tests {@link PolicyModifiedStrategy}.
 */
public class PolicyModifiedStrategyTest extends AbstractPolicyEventStrategyTest<PolicyModified> {

    private static final PolicyEntry MODIFIED = TestConstants.Policy.policyEntryWithLabel("modified");

    @Override
    PolicyModifiedStrategy getStrategyUnderTest() {
        return new PolicyModifiedStrategy();
    }

    @Override
    PolicyModified getPolicyEvent(final Instant instant, final Policy policy) {
        final Policy modified = policy.toBuilder().set(MODIFIED).build();
        return PolicyModified.of(modified, 10L, instant, DittoHeaders.empty(), METADATA);
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        assertThat(policyWithEventApplied.getEntryFor(MODIFIED.getLabel())).contains(MODIFIED);
    }
}