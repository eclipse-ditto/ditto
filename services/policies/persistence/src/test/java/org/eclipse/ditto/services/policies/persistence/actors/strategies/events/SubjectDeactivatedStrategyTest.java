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
import static org.eclipse.ditto.services.policies.persistence.TestConstants.Policy.SUPPORT_LABEL;
import static org.eclipse.ditto.services.policies.persistence.TestConstants.Policy.SUPPORT_SUBJECT_ID;

import java.time.Instant;
import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.signals.events.policies.SubjectDeactivated;

/**
 * Tests {@link SubjectDeactivatedStrategy}.
 */
public class SubjectDeactivatedStrategyTest extends AbstractPolicyEventStrategyTest<SubjectDeactivated> {

    @Override
    SubjectDeactivatedStrategy getStrategyUnderTest() {
        return new SubjectDeactivatedStrategy();
    }

    @Override
    SubjectDeactivated getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        return SubjectDeactivated.of(policyId, SUPPORT_LABEL, SUPPORT_SUBJECT_ID, 10L, instant,
                DittoHeaders.empty());
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        final Optional<Subject> deactivatedSubject = policyWithEventApplied.getEntryFor(SUPPORT_LABEL)
                .map(PolicyEntry::getSubjects)
                .flatMap(subjects -> subjects.getSubject(SUPPORT_SUBJECT_ID));

        assertThat(deactivatedSubject).isEmpty();
    }
}
