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
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.signals.events.PolicyImportsModified;
import org.eclipse.ditto.policies.service.persistence.TestConstants;

/**
 * Tests {@link PolicyImportsModifiedStrategy}.
 */
public class PolicyImportsModifiedStrategyTest extends AbstractPolicyEventStrategyTest<PolicyImportsModified> {

    private static final PolicyImport MODIFIED_1 = TestConstants.Policy.POLICY_IMPORT_WITH_ENTRIES;
    private static final PolicyImport MODIFIED_2 = TestConstants.Policy.ADDITIONAL_POLICY_IMPORT_WITH_ENTRIES;

    @Override
    PolicyImportsModifiedStrategy getStrategyUnderTest() {
        return new PolicyImportsModifiedStrategy();
    }

    @Override
    PolicyImportsModified getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        return PolicyImportsModified.of(policyId, PolicyImports.newInstance(MODIFIED_1, MODIFIED_2), 10L, instant,
                DittoHeaders.empty(), null);
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        assertThat(policyWithEventApplied.getPolicyImports().getPolicyImport(
                MODIFIED_1.getImportedPolicyId()))
                .contains(MODIFIED_1);

        assertThat(policyWithEventApplied.getPolicyImports().getPolicyImport(
                MODIFIED_2.getImportedPolicyId()))
                .contains(MODIFIED_2);

        assertThat(policyWithEventApplied.getPolicyImports()).containsExactlyInAnyOrder(MODIFIED_1, MODIFIED_2);
    }
}