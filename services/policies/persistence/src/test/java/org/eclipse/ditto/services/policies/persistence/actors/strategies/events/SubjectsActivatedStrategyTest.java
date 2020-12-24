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
import static org.eclipse.ditto.services.policies.persistence.TestConstants.Policy.SUPPORT_SUBJECT_WITH_EXPIRY;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.signals.events.policies.SubjectsActivated;

/**
 * Tests {@link SubjectsActivatedStrategy}.
 */
public class SubjectsActivatedStrategyTest extends AbstractPolicyEventStrategyTest<SubjectsActivated> {

    @Override
    SubjectsActivatedStrategy getStrategyUnderTest() {
        return new SubjectsActivatedStrategy();
    }

    @Override
    SubjectsActivated getPolicyEvent(final Instant instant, final Policy policy) {
        final PolicyId policyId = policy.getEntityId().orElseThrow();
        final Map<Label, Subject> activatedSubjects = Stream.of(SUPPORT_LABEL)
                .collect(Collectors.toMap(Function.identity(), label -> SUPPORT_SUBJECT_WITH_EXPIRY));
        return SubjectsActivated.of(policyId, activatedSubjects, 10L, instant, DittoHeaders.empty());
    }

    @Override
    protected void additionalAssertions(final Policy policyWithEventApplied) {
        final Subject activatedSubject = policyWithEventApplied.getEntryFor(SUPPORT_LABEL)
                .map(PolicyEntry::getSubjects)
                .flatMap(subjects -> subjects.getSubject(SUPPORT_SUBJECT_ID))
                .orElseThrow(() -> new AssertionError("Expected subject " + SUPPORT_SUBJECT_ID +
                        " not found in entry " + SUPPORT_LABEL + " in policy " + policyWithEventApplied));

        assertThat(activatedSubject.getExpiry()).isNotEmpty();
    }
}