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

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyRevision;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.eclipse.ditto.internal.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractPolicyEventStrategyTest<T extends PolicyEvent<T>> {

    static final Instant TIMESTAMP = Instant.EPOCH;
    static final Metadata METADATA = Metadata.newBuilder()
            .set("creator", "The epic Ditto team")
            .build();

    private Instant instant;
    private Policy policy;
    private T policyEvent;
    private EventStrategy<T, Policy> strategy;

    @Before
    public void setUp() {
        policy = TestConstants.Policy.policyWithRandomName();
        instant = Instant.now();
        policyEvent = getPolicyEvent(instant, policy);
        strategy = getStrategyUnderTest();
    }

    @Test
    public void testHandleReturnsNullForNullEntity() {
        assertThat(getStrategyUnderTest().handle(getPolicyEvent(instant, policy), null, 0L)).isNull();
    }

    @Test
    public void testHandlePolicyEvent() {
        final Policy policyWithEventApplied = strategy.handle(policyEvent, policy, 10L);
        assertThat(policyWithEventApplied).isNotNull();
        assertThat(policyWithEventApplied.getModified()).contains(instant);
        assertThat(policyWithEventApplied.getRevision()).contains(PolicyRevision.newInstance(10L));
        additionalAssertions(policyWithEventApplied);
    }

    protected void additionalAssertions(final Policy policyWithEventApplied) {
        // override in subclass
    }

    protected Instant getInstant() {
        return instant;
    }

    abstract EventStrategy<T, Policy> getStrategyUnderTest();

    abstract T getPolicyEvent(final Instant instant, final Policy policy);

}
