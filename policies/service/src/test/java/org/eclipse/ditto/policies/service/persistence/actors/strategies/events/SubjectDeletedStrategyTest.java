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
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.ADDITIONAL_SUPPORT_SUBJECT_ID;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.FEATURES_RESOURCE_KEY;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.SUPPORT_LABEL;

import java.time.Instant;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.events.SubjectDeleted;

/**
 * Tests {@link org.eclipse.ditto.policies.service.persistence.actors.strategies.events.SubjectDeletedStrategy}.
 */
public class SubjectDeletedStrategyTest extends AbstractPolicyEventStrategyTest<SubjectDeleted> {

    @Override
    SubjectDeletedStrategy getStrategyUnderTest() {
        return new SubjectDeletedStrategy();
    }

    @Override
    SubjectDeleted getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        return SubjectDeleted.of(policyId, SUPPORT_LABEL, ADDITIONAL_SUPPORT_SUBJECT_ID, 10L, instant,
                DittoHeaders.empty(), METADATA);
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        assertThat(policyWithEventApplied.getEffectedPermissionsFor(SUPPORT_LABEL, ADDITIONAL_SUPPORT_SUBJECT_ID,
                FEATURES_RESOURCE_KEY)).isEmpty();
    }
}
