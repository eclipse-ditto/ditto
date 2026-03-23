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
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.ADDITIONAL_SUPPORT_SUBJECT;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.ADDITIONAL_SUPPORT_SUBJECT_ID;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.FEATURES_RESOURCE;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.FEATURES_RESOURCE_KEY;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.READ_WRITE_REVOKED;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.SUPPORT_LABEL;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.SUPPORT_SUBJECT_ID;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resources;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.events.SubjectsModified;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.policies.service.persistence.actors.strategies.events.SubjectsModifiedStrategy} with modified Label.
 */
public class SubjectsModifiedStrategyUpdateTest extends AbstractPolicyEventStrategyTest<SubjectsModified> {

    private static final List<String> SCOPED_NAMESPACES = Arrays.asList("com.acme", "com.acme.*");
    private static final Subjects NEW_SUBJECTS = Subjects.newInstance(ADDITIONAL_SUPPORT_SUBJECT);

    @Override
    SubjectsModifiedStrategy getStrategyUnderTest() {
        return new SubjectsModifiedStrategy();
    }

    @Override
    SubjectsModified getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        return SubjectsModified.of(policyId, SUPPORT_LABEL, NEW_SUBJECTS, 10L, instant, DittoHeaders.empty(),
                METADATA);
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        // existing subjects removed
        assertThat(policyWithEventApplied.getEffectedPermissionsFor(SUPPORT_LABEL, SUPPORT_SUBJECT_ID,
                FEATURES_RESOURCE_KEY)).isEmpty();

        // new subjects replaced the existing ones
        assertThat(policyWithEventApplied.getEffectedPermissionsFor(SUPPORT_LABEL, ADDITIONAL_SUPPORT_SUBJECT_ID,
                FEATURES_RESOURCE_KEY)).contains(READ_WRITE_REVOKED);

        // resources were not changed
        assertThat(policyWithEventApplied.getEntryFor(SUPPORT_LABEL)
                .map(pe -> pe.getResources()))
                .contains(Resources.newInstance(FEATURES_RESOURCE));
    }

    @Test
    public void testHandlePreservesNamespaces() {
        final Policy policy = newScopedSupportPolicy();
        assertThat(policy.getEntryFor(SUPPORT_LABEL).orElseThrow().getNamespaces())
                .containsExactlyElementsOf(SCOPED_NAMESPACES);
        final SubjectsModified event = getPolicyEvent(getInstant(), policy);

        final Policy policyWithEventApplied = getStrategyUnderTest().handle(event, policy, 10L);

        assertThat(policyWithEventApplied.getEntryFor(SUPPORT_LABEL)
                .orElseThrow()
                .getNamespaces())
                        .containsExactlyElementsOf(SCOPED_NAMESPACES);
    }

    private static Policy newScopedSupportPolicy() {
        final Policy policy = TestConstants.Policy.policyWithRandomName();
        final PolicyEntry supportEntry = policy.getEntryFor(SUPPORT_LABEL).orElseThrow();
        final PolicyEntry scopedSupportEntry = PoliciesModelFactory.newPolicyEntry(supportEntry.getLabel(),
                supportEntry.getSubjects(), supportEntry.getResources(), SCOPED_NAMESPACES,
                supportEntry.getImportableType(), supportEntry.getAllowedImportAdditions());
        return PoliciesModelFactory.newPolicyBuilder(policy)
                .set(scopedSupportEntry)
                .build();
    }
}
