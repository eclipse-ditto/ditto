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
import static org.eclipse.ditto.services.policies.persistence.TestConstants.Policy.FEATURES_RESOURCE_KEY;
import static org.eclipse.ditto.services.policies.persistence.TestConstants.Policy.NEW_ATTRIBUTE_RESOURCE;
import static org.eclipse.ditto.services.policies.persistence.TestConstants.Policy.NEW_ATTRIBUTE_RESOURCE_KEY;
import static org.eclipse.ditto.services.policies.persistence.TestConstants.Policy.READ_GRANTED;
import static org.eclipse.ditto.services.policies.persistence.TestConstants.Policy.SUPPORT_LABEL;
import static org.eclipse.ditto.services.policies.persistence.TestConstants.Policy.SUPPORT_SUBJECT_ID;

import java.time.Instant;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.signals.events.policies.ResourcesModified;

/**
 * Tests {@link ResourcesModifiedStrategy} with modified Label.
 */
public class ResourcesModifiedStrategyUpdateTest extends AbstractPolicyEventStrategyTest<ResourcesModified> {

    @Override
    ResourcesModifiedStrategy getStrategyUnderTest() {
        return new ResourcesModifiedStrategy();
    }

    @Override
    ResourcesModified getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        final Resources resources = Resources.newInstance(NEW_ATTRIBUTE_RESOURCE);
        return ResourcesModified.of(policyId, SUPPORT_LABEL, resources, 10L, instant, DittoHeaders.empty());
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        // new resource added
        assertThat(policyWithEventApplied.getEffectedPermissionsFor(SUPPORT_LABEL, SUPPORT_SUBJECT_ID,
                NEW_ATTRIBUTE_RESOURCE_KEY)).contains(READ_GRANTED);

        // existing resources removed
        assertThat(policyWithEventApplied.getEffectedPermissionsFor(SUPPORT_LABEL, SUPPORT_SUBJECT_ID,
                FEATURES_RESOURCE_KEY)).isEmpty();

        // subjects were not changed
        assertThat(policyWithEventApplied.getEntryFor(SUPPORT_LABEL)
                .map(pe -> pe.getSubjects()))
                .contains(Subjects.newInstance(Subject.newInstance(SUPPORT_SUBJECT_ID)));
    }
}