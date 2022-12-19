/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.signals.events.assertions.EventAssertions.assertThat;

import java.time.Instant;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportCreated;
import org.eclipse.ditto.policies.service.persistence.TestConstants;

/**
 * Tests {@link PolicyImportCreatedStrategy}.
 */
public class PolicyImportCreatedStrategyTest extends AbstractPolicyEventStrategyTest<PolicyImportCreated> {

    private static final PolicyImport CREATED = TestConstants.Policy.policyImportWithId("created");

    @Override
    PolicyImportCreatedStrategy getStrategyUnderTest() {
        return new PolicyImportCreatedStrategy();
    }

    @Override
    PolicyImportCreated getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        return PolicyImportCreated.of(policyId, CREATED, 10L, instant, DittoHeaders.empty(), null);
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        assertThat(policyWithEventApplied.getPolicyImports()
                .getPolicyImport(CREATED.getImportedPolicyId()))
                .contains(CREATED);
    }
}