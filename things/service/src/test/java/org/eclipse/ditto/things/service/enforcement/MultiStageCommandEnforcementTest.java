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
package org.eclipse.ditto.things.service.enforcement;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyConflictException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingUnavailableException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.junit.ClassRule;
import org.junit.Test;

import akka.pattern.AskTimeoutException;
import akka.testkit.javadsl.TestKit;

/**
 * Tests commands that triggers different or multiple commands internally.
 */
public final class MultiStageCommandEnforcementTest extends AbstractThingEnforcementTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final Subject DEFAULT_SUBJECT =
            Subject.newInstance(SubjectIssuer.GOOGLE, "defaultSubject");

    private static final Subject NOT_DEFAULT_SUBJECT =
            Subject.newInstance(SubjectIssuer.GOOGLE, "notDefaultSubject");

    public static final AuthorizationSubject AUTHORIZATION_SUBJECT =
            AuthorizationSubject.newInstance(DEFAULT_SUBJECT.getId());
    private static final DittoHeaders DEFAULT_HEADERS = DittoHeaders.newBuilder()
            .authorizationContext(AuthorizationContext.newInstance(
                    DittoAuthorizationContextType.UNSPECIFIED,
                    AUTHORIZATION_SUBJECT))
            .putHeader(DittoHeaderDefinition.ORIGINATOR.getKey(), DEFAULT_SUBJECT.getId())
            .build();

    private static final String THING = ThingCommand.RESOURCE_TYPE;
    private static final String POLICY = PolicyCommand.RESOURCE_TYPE;

    @Test
    public void retrieveThingAndPolicy() {
        new TestKit(system) {{
            // GIVEN: Thing and its Policy exist
            final ThingId thingId = ThingId.of("thing", UUID.randomUUID().toString());
            final PolicyId policyId = PolicyId.of("policy", UUID.randomUUID().toString());
            final Thing thing = emptyThing(thingId, policyId);
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());
            final Policy policy = defaultPolicy(policyId);
            when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                    .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

            // WHEN: received RetrieveThing
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            final RetrieveThing expectedRetrieveThing = retrieveThing.setDittoHeaders(DEFAULT_HEADERS.toBuilder()
                    .readGrantedSubjects(List.of(AUTHORIZATION_SUBJECT)).build());
            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            thingPersistenceActorProbe.reply(
                    RetrieveThingResponse.of(thingId, thing, null, null, DEFAULT_HEADERS));

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            policiesShardRegionProbe.expectMsgClass(RetrievePolicy.class);
            policiesShardRegionProbe.reply(RetrievePolicyResponse.of(policyId, policy, DEFAULT_HEADERS));

            // THEN: Load enforcer to filter response
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            // THEN: initial requester receives Thing with inline policy
            final RetrieveThingResponse response = TestSetup.fishForMsgClass(this, RetrieveThingResponse.class);
            assertThat((CharSequence) response.getEntityId()).isEqualTo(thingId);
            assertThat(response.getEntity()).isObject();
            assertThat(response.getEntity().asObject().getValue("_policy/policyId"))
                    .contains(JsonFactory.newValue(policyId.toString()));
        }};
    }

    @Test
    public void retrieveThingWithoutPolicyWhenPermissionIsMissing() {
        new TestKit(system) {{
            // GIVEN: Thing and its Policy exist but default subject has no permission on the policy
            final ThingId thingId = ThingId.of("thing", UUID.randomUUID().toString());
            final PolicyId policyId = PolicyId.of("policy", UUID.randomUUID().toString());
            final Thing thing = emptyThing(thingId, policyId);
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());
            final Policy policy = thingOnlyPolicy(policyId);
            when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                    .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

            // WHEN: received RetrieveThing
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            supervisor.tell(retrieveThing, getRef());

            // THEN:: Retrieve enforcer for authorization of command
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            final DittoHeaders expectedHeaders = DEFAULT_HEADERS.toBuilder()
                    .readGrantedSubjects(List.of(AUTHORIZATION_SUBJECT)).build();
            final RetrieveThing expectedRetrieveThing = retrieveThing.setDittoHeaders(expectedHeaders);
            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            thingPersistenceActorProbe.reply(
                    RetrieveThingResponse.of(thingId, thing, null, null, expectedHeaders));

            // THEN:: Retrieve policy for enrichment
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            policiesShardRegionProbe.expectMsgClass(RetrievePolicy.class);
            policiesShardRegionProbe.reply(PolicyNotAccessibleException.newBuilder(policyId).build());

            // THEN:: Retrieve enforcer for filtering of response
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            // THEN: initial requester receives Thing with inline policy
            final RetrieveThingResponse response = expectMsgClass(RetrieveThingResponse.class);
            assertThat((CharSequence) response.getEntityId()).isEqualTo(thingId);
            assertThat(response.getEntity()).isObject();
            assertThat(response.getEntity().asObject()).doesNotContain(JsonKey.of("_policy"));
        }};
    }

    @Test
    public void retrieveThingErrorIfCacheIsOutOfDate() {
        new TestKit(system) {{
            // GIVEN: Thing and its Policy exist in cache but not actually
            final ThingId thingId = ThingId.of("thing", UUID.randomUUID().toString());
            final PolicyId policyId = PolicyId.of("policy", UUID.randomUUID().toString());
            final Thing thing = emptyThing(thingId, policyId);
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());
            final Policy policy = thingOnlyPolicy(policyId);
            when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                    .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

            // WHEN: received RetrieveThing
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            final RetrieveThing expectedRetrieveThing = retrieveThing.setDittoHeaders(DEFAULT_HEADERS.toBuilder()
                    .readGrantedSubjects(List.of(AUTHORIZATION_SUBJECT)).build());
            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            thingPersistenceActorProbe.reply(ThingNotAccessibleException.newBuilder(thingId).build());

            // THEN: initial requester receives error
            TestSetup.fishForMsgClass(this, ThingNotAccessibleException.class);

            // WHEN: Thing exists but Policy exists only in cache
            supervisor.tell(retrieveThing, getRef());

            // THEN: Load enforcer to authorize retrieve thing
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            thingPersistenceActorProbe.reply(RetrieveThingResponse.of(thingId, thing, null, null, DEFAULT_HEADERS));

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            policiesShardRegionProbe.expectMsgClass(RetrievePolicy.class);
            policiesShardRegionProbe.reply(PolicyNotAccessibleException.newBuilder(policyId).build());

            // THEN: Load enforcer to filter response
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            // THEN: initial requester receives Thing without Policy
            final RetrieveThingResponse response = expectMsgClass(RetrieveThingResponse.class);
            assertThat((CharSequence) response.getEntityId()).isEqualTo(thingId);
            assertThat(response.getEntity()).isObject();
            assertThat(response.getEntity().asObject()).doesNotContain(JsonKey.of("_policy"));
        }};
    }

    @Test
    public void retrieveThingErrorOnTimeout() {
        new TestKit(system) {{
            // GIVEN: Thing and its Policy exist in cache
            final ThingId thingId = ThingId.of("thing", UUID.randomUUID().toString());
            final PolicyId policyId = PolicyId.of("policy", UUID.randomUUID().toString());
            final Thing thing = emptyThing(thingId, policyId);
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());
            final Policy policy = defaultPolicy(policyId);
            when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                    .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

            // WHEN: received RetrieveThing but both shard regions time out
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            supervisor.tell(retrieveThing, getRef());

            // THEN: Load enforcer to authorize retrieve thing
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            final RetrieveThing expectedRetrieveThing = retrieveThing.setDittoHeaders(DEFAULT_HEADERS.toBuilder()
                    .readGrantedSubjects(List.of(AUTHORIZATION_SUBJECT)).build());
            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            thingPersistenceActorProbe.reply(new AskTimeoutException("thing timeout"));

            // THEN: initial requester receives error
            TestSetup.fishForMsgClass(this, ThingUnavailableException.class);

            // WHEN: Thing is responsive but Policy times out
            supervisor.tell(retrieveThing, getRef());

            // THEN: Load enforcer to authorize retrieve thing
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            thingPersistenceActorProbe.reply(RetrieveThingResponse.of(thingId, thing, null, null, DEFAULT_HEADERS));

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            policiesShardRegionProbe.expectMsgClass(RetrievePolicy.class);
            policiesShardRegionProbe.reply(new AskTimeoutException("policy timeout"));

            // THEN: Load enforcer to filter response
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            // THEN: initial requester receives Thing without Policy
            final RetrieveThingResponse response = expectMsgClass(RetrieveThingResponse.class);
            assertThat((CharSequence) response.getEntityId()).isEqualTo(thingId);
            assertThat(response.getEntity()).isObject();
            assertThat(response.getEntity().asObject()).doesNotContain(JsonKey.of("_policy"));
        }};
    }

    @Test
    public void modifyExistingThing() {
        new TestKit(system) {{
            // GIVEN: Thing and its Policy exist
            final ThingId thingId = ThingId.of("thing", UUID.randomUUID().toString());
            final PolicyId policyId = PolicyId.of("policy", UUID.randomUUID().toString());
            final Thing thing = emptyThing(thingId, policyId);
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());
            final Policy policy = defaultPolicy(policyId);
            when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                    .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

            // WHEN: received ModifyThing
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, DEFAULT_HEADERS);

            supervisor.tell(modifyThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            final DittoHeaders expectedHeaders = DEFAULT_HEADERS.toBuilder()
                    .readGrantedSubjects(List.of(AUTHORIZATION_SUBJECT)).build();
            final ModifyThing expectedModifyThing = modifyThing.setDittoHeaders(expectedHeaders);
            thingPersistenceActorProbe.expectMsg(expectedModifyThing);
            thingPersistenceActorProbe.reply(ModifyThingResponse.modified(thingId, expectedHeaders));

            // THEN: initial requester receives success
            expectMsgClass(ModifyThingResponse.class);
        }};
    }

    @Test
    public void createThingWithExistingPolicy() {
        new TestKit(system) {{
            // GIVEN: Thing is nonexistent but its Policy exists
            final ThingId thingId = ThingId.of("thing", UUID.randomUUID().toString());
            final PolicyId policyId = PolicyId.of("policy", UUID.randomUUID().toString());
            final Thing thing = emptyThing(thingId, policyId);
            final Policy policy = defaultPolicy(policyId);
            final var retrievePolicyResponse = RetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());
            when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                    .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));
            // WHEN: received CreateThing
            final CreateThing createThing = CreateThing.of(thing, null, DEFAULT_HEADERS);

            supervisor.tell(createThing, getRef());
            expectAndAnswerRetrievePolicy(policyId, retrievePolicyResponse);

            thingPersistenceActorProbe.expectMsgClass(CreateThing.class);
            thingPersistenceActorProbe.reply(CreateThingResponse.of(thing, DEFAULT_HEADERS));

            // THEN: initial requester receives success
            TestSetup.fishForMsgClass(this, CreateThingResponse.class);
        }};
    }

    @Test
    public void createThingAndPolicy() {
        new TestKit(system) {{
            // GIVEN: Thing and Policy are nonexistent
            final ThingId thingId = ThingId.of("thing", UUID.randomUUID().toString());
            final PolicyId policyId = PolicyId.of(thingId);
            final Thing thing = emptyThing(thingId, null);
            final Policy policy = defaultPolicy(policyId);
            when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                    .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

            // WHEN: received CreateThing
            final var createThing = CreateThing.of(thing, null, DEFAULT_HEADERS);

            supervisor.tell(createThing, getRef());

            policiesShardRegionProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionProbe.reply(CreatePolicyResponse.of(policyId, policy, DEFAULT_HEADERS));

            thingPersistenceActorProbe.expectMsgClass(CreateThing.class);
            thingPersistenceActorProbe.reply(CreateThingResponse.of(thing, DEFAULT_HEADERS));

            // THEN: initial requester receives success
            TestSetup.fishForMsgClass(this, CreateThingResponse.class);
        }};
    }

    @Test
    public void createThingWithPolicyConflict() {
        new TestKit(system) {{
            // GIVEN: Thing is nonexistent but Policy exists
            final ThingId thingId = ThingId.of("thing", UUID.randomUUID().toString());
            final PolicyId policyId = PolicyId.of(thingId);
            final Thing thing = emptyThing(thingId, null);
            // WHEN: received CreateThing
            final var createThing = CreateThing.of(thing, null, DEFAULT_HEADERS);

            supervisor.tell(createThing, getRef());

            policiesShardRegionProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionProbe.reply(PolicyConflictException.newBuilder(policyId).build());

            // THEN: initial requester receives failure
            final ThingNotCreatableException error = TestSetup.fishForMsgClass(this, ThingNotCreatableException.class);
            assertThat(error.getMessage()).contains("implicit Policy", "creation", "failed");
        }};
    }

    @Test
    public void createThingWithExplicitPolicy() {
        new TestKit(system) {{
            // GIVEN: Thing and Policy do not exist
            final ThingId thingId = ThingId.of("thing", UUID.randomUUID().toString());
            final PolicyId policyId = PolicyId.of("policy", UUID.randomUUID().toString());
            final Thing thing = emptyThing(thingId, policyId);
            final Policy policy = defaultPolicy(policyId);
            when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                    .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

            // WHEN: received CreateThing
            final var createThing = CreateThing.of(thing, policy.toJson(), DEFAULT_HEADERS);

            supervisor.tell(createThing, getRef());

            policiesShardRegionProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionProbe.reply(CreatePolicyResponse.of(policyId, policy, DEFAULT_HEADERS));

            thingPersistenceActorProbe.expectMsgClass(CreateThing.class);
            thingPersistenceActorProbe.reply(CreateThingResponse.of(thing, DEFAULT_HEADERS));

            // THEN: initial requester receives success
            expectMsgClass(CreateThingResponse.class);
        }};
    }

    @Test
    public void createThingWithExplicitPolicyNotAuthorizedBySelf() {
        new TestKit(system) {{
            // GIVEN: Thing and Policy do not exist
            final ThingId thingId = ThingId.of("thing", UUID.randomUUID().toString());
            final PolicyId policyId = PolicyId.of("policy", UUID.randomUUID().toString());
            final Thing thing = emptyThing(thingId, policyId);
            final Policy policy = thingOnlyPolicy(policyId);
            when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                    .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

            // WHEN: received CreateThing whose inline policy does not permit creation of itself
            final var createThing = CreateThing.of(thing, policy.toJson(), DEFAULT_HEADERS);

            supervisor.tell(createThing, getRef());

            policiesShardRegionProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionProbe.reply(CreatePolicyResponse.of(policyId, policy, DEFAULT_HEADERS));

            thingPersistenceActorProbe.expectMsgClass(CreateThing.class);
            thingPersistenceActorProbe.reply(ThingNotModifiableException.newBuilder(thingId).build());

            // THEN: initial requester receives error
            expectMsgClass(ThingNotModifiableException.class);
        }};
    }

    private static Thing emptyThing(final ThingId thingId, @Nullable final PolicyId policyId) {
        return ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setPolicyId(policyId)
                .setRevision(1L)
                .build();
    }

    private static Policy defaultPolicy(final PolicyId policyId) {
        final Permissions permissions = Permissions.newInstance(Permission.READ, Permission.WRITE);
        return PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel(POLICY)
                .setSubject(DEFAULT_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()), permissions)
                .forLabel(THING)
                .setSubject(DEFAULT_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()), permissions)
                .setRevision(1L)
                .build();
    }

    private static Policy thingOnlyPolicy(final PolicyId policyId) {
        final Permissions permissions = Permissions.newInstance(Permission.READ, Permission.WRITE);
        return PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel(POLICY)
                .setSubject(NOT_DEFAULT_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()), permissions)
                .forLabel(THING)
                .setSubject(DEFAULT_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()), permissions)
                .setRevision(1L)
                .build();
    }

}
