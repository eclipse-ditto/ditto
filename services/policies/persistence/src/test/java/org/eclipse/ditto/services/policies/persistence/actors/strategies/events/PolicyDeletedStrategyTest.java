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
import org.eclipse.ditto.model.policies.PolicyLifecycle;
import org.eclipse.ditto.signals.events.policies.PolicyDeleted;

/**
 * Tests {@link PolicyDeletedStrategy}.
 */
public class PolicyDeletedStrategyTest extends AbstractPolicyEventStrategyTest<PolicyDeleted> {

    @Override
    PolicyDeletedStrategy getStrategyUnderTest() {
        return new PolicyDeletedStrategy();
    }

    @Override
    PolicyDeleted getPolicyEvent(final Instant instant, final Policy policy) {
        return PolicyDeleted.of(policy.getEntityId().orElseThrow(), 0, instant, DittoHeaders.empty());
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        assertThat(policyWithEventApplied.getLifecycle()).contains(PolicyLifecycle.DELETED);
    }
}