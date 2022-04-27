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
package org.eclipse.ditto.policies.enforcement.placeholders.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.policies.enforcement.placeholders.strategies.ModifyThingSubstitutionStrategy} in context of
 * {@link org.eclipse.ditto.policies.enforcement.placeholders.PlaceholderSubstitution}.
 */
public class ModifyThingSubstitutionStrategyTest extends AbstractSubstitutionStrategyTestBase {


    @Override
    public void assertImmutability() {
        assertInstancesOf(ModifyThingSubstitutionStrategy.class, areImmutable());
    }

    @Test
    public void applyReturnsTheSameCommandInstanceWhenNoPlaceholderForPolicySubjectIdIsSpecified() {
        final PolicyEntry policyEntry = PolicyEntry.newInstance(LABEL,
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED)), RESOURCES);
        final Policy policy = PoliciesModelFactory.newPolicy(POLICY_ID, Collections.singletonList(policyEntry));

        final ModifyThing commandWithoutPlaceholders = ModifyThing.of(THING_ID, THING, policy.toJson(), DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutPlaceholders);

        assertThat(response).isSameAs(commandWithoutPlaceholders);
    }

    @Test
    public void applyReturnsTheSameCommandInstanceWhenNoInlinePolicyIsSpecified() {
        final ModifyThing commandWithoutInlinePolicy = ModifyThing.of(THING_ID, THING, null, DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutInlinePolicy);

        assertThat(response).isSameAs(commandWithoutInlinePolicy);
    }

    @Test
    public void applyReturnsTheSameCommandInstanceWhenEmptyInlinePolicyIsSpecified() {
        final ModifyThing commandWithoutInlinePolicy =
                ModifyThing.of(THING_ID, THING, JsonObject.newBuilder().set("entries", JsonObject.empty()).build(), DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutInlinePolicy);

        assertThat(response).isSameAs(commandWithoutInlinePolicy);
    }

    @Test
    public void applyReturnsTheReplacedCommandInstanceWhenPlaceholderForPolicySubjectIdIsSpecified() {
        final PolicyEntry policyEntryWithPlaceholders = PolicyEntry.newInstance(LABEL,
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID_PLACEHOLDER, SubjectType.GENERATED)),
                RESOURCES);
        final Policy policyWithPlaceholders =
                PoliciesModelFactory.newPolicy(POLICY_ID, Collections.singletonList(policyEntryWithPlaceholders));
        final ModifyThing commandWithPlaceholders =
                ModifyThing.of(THING_ID, THING, policyWithPlaceholders.toJson(), DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithPlaceholders);

        final PolicyEntry expectedPolicyEntryReplaced = PolicyEntry.newInstance(LABEL,
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED)),
                RESOURCES);
        final Policy expectedPolicyReplaced =
                PoliciesModelFactory.newPolicy(POLICY_ID, Collections.singletonList(expectedPolicyEntryReplaced));
        final ModifyThing expectedCommandReplaced =
                ModifyThing.of(THING_ID, THING, expectedPolicyReplaced.toJson(), DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandReplaced);
    }

}
