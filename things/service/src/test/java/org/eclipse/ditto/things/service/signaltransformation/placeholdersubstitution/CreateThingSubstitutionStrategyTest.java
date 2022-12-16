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
package org.eclipse.ditto.things.service.signaltransformation.placeholdersubstitution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

/**
 * Tests {@link CreateThingSubstitutionStrategy} in context of
 * {@link org.eclipse.ditto.base.service.signaltransformer.placeholdersubstitution.AbstractPlaceholderSubstitution}.
 */
public class CreateThingSubstitutionStrategyTest {

    private static final String SUBJECT_ID_PLACEHOLDER = "{{ request:subjectId }}";

    private static final String NAMESPACE = "org.eclipse.ditto";
    private static final PolicyId POLICY_ID = PolicyId.of(NAMESPACE, "my-policy");
    private static final String LABEL = "my-label";
    private static final String SUBJECT_ID = "nginx:ditto";

    private static final ThingId THING_ID = ThingId.of(NAMESPACE, "my-thing");
    private static final Thing THING = Thing.newBuilder().setId(THING_ID)
            .setAttributes(JsonObject.newBuilder().set("key", "val").build())
            .build();
    private static final Iterable<Resource> RESOURCES = Collections.singleton(
            Resource.newInstance("resourceKey", "resourcePath",
                    EffectedPermissions.newInstance(Collections.singleton("READ"), Collections.emptySet())));

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance(SUBJECT_ID)))
            .build();

    protected ThingsPlaceholderSubstitution substitution;

    @Before
    public void init() {
        substitution =
                new ThingsPlaceholderSubstitution(Mockito.mock(ActorSystem.class), ConfigFactory.empty());
    }

    @Test
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
    public void applyReturnsTheSameCommandInstanceWhenEmptyInlinePolicyIsSpecified() {
        final CreateThing commandWithoutInlinePolicy =
                CreateThing.of(THING, JsonObject.newBuilder()
                                .set("imports", JsonObject.empty())
                                .set("entries", JsonObject.empty())
                                .build(),
                        DITTO_HEADERS);

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

    private final Signal<?> applyBlocking(final Signal<?> input) {
        final CompletionStage<Signal<?>> responseFuture = substitution.apply(input);
        try {
            return responseFuture.toCompletableFuture().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

}
