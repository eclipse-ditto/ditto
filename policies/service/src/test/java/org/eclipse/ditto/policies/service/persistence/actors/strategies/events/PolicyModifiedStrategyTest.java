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
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.eclipse.ditto.policies.model.signals.events.PolicyModified;

/**
 * Tests {@link org.eclipse.ditto.policies.service.persistence.actors.strategies.events.PolicyModifiedStrategy}.
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
