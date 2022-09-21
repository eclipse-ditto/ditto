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
import org.eclipse.ditto.policies.model.signals.events.PolicyImportDeleted;
import org.eclipse.ditto.policies.service.persistence.TestConstants;

/**
 * Tests {@link PolicyImportDeletedStrategy}.
 */
public class PolicyImportDeletedStrategyTest extends AbstractPolicyEventStrategyTest<PolicyImportDeleted> {

    @Override
    PolicyImportDeletedStrategy getStrategyUnderTest() {
        return new PolicyImportDeletedStrategy();
    }

    @Override
    PolicyImportDeleted getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        return PolicyImportDeleted.of(policyId, TestConstants.Policy.POLICY_IMPORT_ID, 10L, instant,
                DittoHeaders.empty(), null);
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        assertThat(policyWithEventApplied.getPolicyImports().getPolicyImport(
                TestConstants.Policy.POLICY_IMPORT_ID)).isEmpty();
    }
}