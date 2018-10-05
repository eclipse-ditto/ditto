/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.junit.Test;

/**
 * Tests {@link ModifyPolicySubstitutionStrategy} in context of
 * {@link org.eclipse.ditto.services.concierge.enforcement.placeholders.PlaceholderSubstitution}.
 */
public class CreateThingSubstitutionStrategyTest extends AbstractSubstitutionStrategyTestBase {


    @Override
    public void assertImmutability() {
        assertInstancesOf(CreateThingSubstitutionStrategy.class, areImmutable());
    }

    @Test
    public void applyReturnsTheSameCommandInstanceWhenNoPlaceholderForPolicySubjectIdIsSpecified() {
        final PolicyEntry policyEntry = PolicyEntry.newInstance(LABEL,
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED)), RESOURCES);
        final Policy policy = PoliciesModelFactory.newPolicy(POLICY_ID, Collections.singletonList(policyEntry));

        final CreateThing commandWithoutPlaceholders = CreateThing.of(THING, policy.toJson(), DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutPlaceholders);

        assertThat(response).isSameAs(commandWithoutPlaceholders);
    }

    @Test
    public void applyReturnsTheSameCommandInstanceWhenNoInlinePolicyIsSpecified() {
        final CreateThing commandWithoutInlinePolicy = CreateThing.of(THING, null, DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutInlinePolicy);

        assertThat(response).isSameAs(commandWithoutInlinePolicy);
    }

    @Test
    public void applyReturnsTheSameCommandInstanceWhenInvalidInlinePolicyIsSpecified() {
        final CreateThing commandWithoutInlinePolicy =
                CreateThing.of(THING, JsonObject.newBuilder().set("foo", "bar").build(), DITTO_HEADERS);

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
        final CreateThing commandWithPlaceholders =
                CreateThing.of(THING, policyWithPlaceholders.toJson(), DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithPlaceholders);

        final PolicyEntry expectedPolicyEntryReplaced = PolicyEntry.newInstance(LABEL,
                Subjects.newInstance(Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED)),
                RESOURCES);
        final Policy expectedPolicyReplaced =
                PoliciesModelFactory.newPolicy(POLICY_ID, Collections.singletonList(expectedPolicyEntryReplaced));
        final CreateThing expectedCommandReplaced =
                CreateThing.of(THING, expectedPolicyReplaced.toJson(), DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandReplaced);
    }

    @Test
    public void applyReturnsTheReplacedCommandInstanceWhenPlaceholderForAclSubjectIdIsSpecified() {
        final AccessControlList aclWithPlaceholders = AccessControlList.newBuilder()
                .set(AclEntry.newInstance(AuthorizationSubject.newInstance(SUBJECT_ID_PLACEHOLDER), ACL_PERMISSIONS))
                .build();
        final Thing thingWithAclPlaceholders = THING.setAccessControlList(aclWithPlaceholders);
        final CreateThing commandWithPlaceholders =
                CreateThing.of(thingWithAclPlaceholders, null, DITTO_HEADERS_V1);

        final WithDittoHeaders response = applyBlocking(commandWithPlaceholders);

        final AccessControlList expectedAclReplaced = AccessControlList.newBuilder()
                .set(AclEntry.newInstance(AuthorizationSubject.newInstance(SUBJECT_ID), ACL_PERMISSIONS))
                .build();
        final Thing expectedThingReplaced = THING.setAccessControlList(expectedAclReplaced);
        final CreateThing expectedCommandReplaced =
                CreateThing.of(expectedThingReplaced, null, DITTO_HEADERS_V1);
        assertThat(response).isEqualTo(expectedCommandReplaced);
    }

}
