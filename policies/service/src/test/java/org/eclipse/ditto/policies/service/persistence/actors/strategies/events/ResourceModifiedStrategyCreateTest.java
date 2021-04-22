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
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.FEATURES_RESOURCE_KEY;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.MODIFIED_FEATURES_RESOURCE;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.READ_WRITE_REVOKED;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.SUPPORT_LABEL;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.SUPPORT_SUBJECT_ID;

import java.time.Instant;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resources;
import org.eclipse.ditto.policies.model.signals.events.ResourceModified;

/**
 * Tests {@link org.eclipse.ditto.policies.service.persistence.actors.strategies.events.ResourceModifiedStrategy} with modified Label.
 */
public class ResourceModifiedStrategyCreateTest extends AbstractPolicyEventStrategyTest<ResourceModified> {

    private static final Label NEW_LABEL = Label.of("new");
    private static final Resources RESOURCES = Resources.newInstance(MODIFIED_FEATURES_RESOURCE);

    @Override
    ResourceModifiedStrategy getStrategyUnderTest() {
        return new ResourceModifiedStrategy();
    }

    @Override
    ResourceModified getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        return ResourceModified.of(policyId, NEW_LABEL, MODIFIED_FEATURES_RESOURCE,
                10L, instant, DittoHeaders.empty(), METADATA);
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {

        // new resource created under new label
        assertThat(policyWithEventApplied.getEntryFor(NEW_LABEL).map(PolicyEntry::getResources)).contains(RESOURCES);
        assertThat(policyWithEventApplied.getEntryFor(NEW_LABEL).map(PolicyEntry::getSubjects))
                .contains(PoliciesModelFactory.emptySubjects());

        // existing label not changed
        assertThat(policyWithEventApplied.getEffectedPermissionsFor(SUPPORT_LABEL, SUPPORT_SUBJECT_ID,
                FEATURES_RESOURCE_KEY)).contains(READ_WRITE_REVOKED);
    }
}
