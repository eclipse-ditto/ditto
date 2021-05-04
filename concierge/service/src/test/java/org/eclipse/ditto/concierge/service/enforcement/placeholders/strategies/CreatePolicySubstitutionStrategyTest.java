/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.concierge.service.enforcement.placeholders.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.junit.Test;

/**
 * Tests {@link CreatePolicySubstitutionStrategy} in context of
 * {@link org.eclipse.ditto.concierge.service.enforcement.placeholders.PlaceholderSubstitution}.
 */
public class CreatePolicySubstitutionStrategyTest extends AbstractSubstitutionStrategyTestBase {

    @Override
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicySubstitutionStrategy.class, areImmutable());
    }

    @Test
    public void applyReturnsTheSameCommandInstanceWhenNoPlaceholderIsSpecified() {
        final PolicyEntry policyEntry = PolicyEntry.newInstance(LABEL,
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED)), RESOURCES);
        final Policy policy = PoliciesModelFactory.newPolicy(POLICY_ID, Collections.singletonList(policyEntry));
        final CreatePolicy commandWithoutPlaceholders = CreatePolicy.of(policy, DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutPlaceholders);

        assertThat(response).isSameAs(commandWithoutPlaceholders);
    }

    @Test
    public void applyReturnsTheReplacedCommandInstanceWhenPlaceholderIsSpecified() {
        final PolicyEntry policyEntryWithPlaceholders = PolicyEntry.newInstance(LABEL,
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID_PLACEHOLDER, SubjectType.GENERATED)), RESOURCES);
        final Policy policyWithPlaceholders =
                PoliciesModelFactory.newPolicy(POLICY_ID, Collections.singletonList(policyEntryWithPlaceholders));
        final CreatePolicy commandWithPlaceholders = CreatePolicy.of(policyWithPlaceholders, DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithPlaceholders);

        final PolicyEntry expectedPolicyEntryReplaced = PolicyEntry.newInstance(LABEL,
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED)), RESOURCES);
        final Policy expectedPolicyReplaced =
                PoliciesModelFactory.newPolicy(POLICY_ID, Collections.singletonList(expectedPolicyEntryReplaced));
        final CreatePolicy expectedCommandReplaced = CreatePolicy.of(expectedPolicyReplaced, DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandReplaced);
    }

}
