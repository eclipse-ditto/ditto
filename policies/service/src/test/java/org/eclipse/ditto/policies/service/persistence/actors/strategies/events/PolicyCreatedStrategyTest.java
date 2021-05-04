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
import org.eclipse.ditto.policies.model.PolicyLifecycle;
import org.eclipse.ditto.policies.model.signals.events.PolicyCreated;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.policies.service.persistence.actors.strategies.events.PolicyCreatedStrategy}.
 */
public class PolicyCreatedStrategyTest extends AbstractPolicyEventStrategyTest<PolicyCreated> {

    @Override
    PolicyCreatedStrategy getStrategyUnderTest() {
        return new PolicyCreatedStrategy();
    }

    @Override
    PolicyCreated getPolicyEvent(final Instant instant, final Policy policy) {
        return PolicyCreated.of(policy, 0, instant, DittoHeaders.empty(), METADATA);
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        assertThat(policyWithEventApplied.getLifecycle()).contains(PolicyLifecycle.ACTIVE);
        assertThat(policyWithEventApplied.getCreated()).contains(getInstant());
    }

    @Test
    @Override
    public void testHandleReturnsNullForNullEntity() {
        // makes no sense for PolicyCreatedStrategy
    }
}
