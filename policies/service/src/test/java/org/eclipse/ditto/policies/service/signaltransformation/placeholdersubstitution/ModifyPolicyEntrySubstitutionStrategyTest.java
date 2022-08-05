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
package org.eclipse.ditto.policies.service.signaltransformation.placeholdersubstitution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntry;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.edge.service.dispatching.signaltransformer.placeholdersubstitution.policies.ModifyPolicyEntrySubstitutionStrategy} in context of
 * {@link org.eclipse.ditto.policies.enforcement.placeholders.AbstractPlaceholderSubstitutionPreEnforcer}.
 */
public class ModifyPolicyEntrySubstitutionStrategyTest extends AbstractPolicySubstitutionStrategyTestBase {

    @Override
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyEntrySubstitutionStrategy.class, areImmutable());
    }

    @Test
    public void applyReturnsTheSameCommandInstanceWhenNoPlaceholderIsSpecified() {
        final PolicyEntry policyEntry = PolicyEntry.newInstance(LABEL,
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED)), RESOURCES);
        final ModifyPolicyEntry commandWithoutPlaceholders = ModifyPolicyEntry.of(
                POLICY_ID,
                policyEntry,
                DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutPlaceholders);

        assertThat(response).isSameAs(commandWithoutPlaceholders);
    }

    @Test
    public void applyReturnsTheReplacedCommandInstanceWhenPlaceholderIsSpecified() {
        final PolicyEntry policyEntryWithPlaceholders = PolicyEntry.newInstance(
                LABEL,
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID_PLACEHOLDER, SubjectType.GENERATED)), RESOURCES);
        final ModifyPolicyEntry commandWithPlaceholders = ModifyPolicyEntry.of(
                POLICY_ID,
                policyEntryWithPlaceholders,
                DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithPlaceholders);

        final PolicyEntry expectedPolicyEntryReplaced = PolicyEntry.newInstance(
                LABEL,
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED)), RESOURCES);
        final ModifyPolicyEntry expectedCommandReplaced = ModifyPolicyEntry.of(commandWithPlaceholders.getEntityId(),
                expectedPolicyEntryReplaced, DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandReplaced);
    }

}
