/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.concierge.enforcement;


import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.AskTimeoutException;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;

/**
 * Tests commands that triggers different or multiple commands internally.
 */
public final class MultiStageCommandTest {

    private static final Subject DEFAULT_SUBJECT =
            Subject.newInstance(SubjectIssuer.GOOGLE, "defaultSubject");

    private static final Subject NOT_DEFAULT_SUBJECT =
            Subject.newInstance(SubjectIssuer.GOOGLE, "notDefaultSubject");

    private static final DittoHeaders DEFAULT_HEADERS = DittoHeaders.newBuilder()
            .authorizationSubjects(DEFAULT_SUBJECT.getId())
            .build();

    private static final String THING = ThingCommand.RESOURCE_TYPE;
    private static final String POLICY = PolicyCommand.RESOURCE_TYPE;

    private static final String THING_SUDO =
            org.eclipse.ditto.services.models.things.commands.sudo.SudoCommand.RESOURCE_TYPE;
    private static final String POLICY_SUDO =
            org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommand.RESOURCE_TYPE;

    private ActorSystem system;
    private TestActorRef<MockEntitiesActor> mockThingsActor;
    private TestActorRef<MockEntitiesActor> mockPoliciesActor;

    @Before
    public void init() {
        system = ActorSystem.create();
        mockThingsActor = newMockEntitiesActor(system);
        mockPoliciesActor = newMockEntitiesActor(system);
    }

    @After
    public void shutdown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }


    @Test
    public void retrieveThingAndPolicy() {
        new TestKit(system) {{
            final ActorRef underTest = newEnforcerActor(getRef());

            // GIVEN: Thing and its Policy exist
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = emptyThing(thingId, policyId);
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());
            final Policy policy = defaultPolicy(policyId);
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());
            mockThingsActor.underlyingActor()
                    .setReply(THING_SUDO, sudoRetrieveThingResponse)
                    .setReply(THING, RetrieveThingResponse.of(thingId, thing, DEFAULT_HEADERS));
            mockPoliciesActor.underlyingActor()
                    .setReply(POLICY_SUDO, sudoRetrievePolicyResponse)
                    .setReply(POLICY, RetrievePolicyResponse.of(policyId, policy, DEFAULT_HEADERS));

            // WHEN: received RetrieveThing
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            underTest.tell(retrieveThing, getRef());

            // THEN: initial requester receives Thing with inline policy
            final RetrieveThingResponse response = expectMsgClass(RetrieveThingResponse.class);
            assertThat(response.getThingId()).isEqualTo(thingId);
            assertThat(response.getEntity()).isObject();
            assertThat(response.getEntity().asObject().getValue("_policy/policyId"))
                    .contains(JsonFactory.newValue(policyId));
        }};
    }

    @Test
    public void retrieveThingWithoutPolicyWhenPermissionIsMissing() {
        new TestKit(system) {{
            final ActorRef underTest = newEnforcerActor(getRef());

            // GIVEN: Thing and its Policy exist but default subject has no permission on the policy
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = emptyThing(thingId, policyId);
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());
            final Policy policy = thingOnlyPolicy(policyId);
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());
            mockThingsActor.underlyingActor()
                    .setReply(THING_SUDO, sudoRetrieveThingResponse)
                    .setReply(THING, RetrieveThingResponse.of(thingId, thing, DEFAULT_HEADERS));
            mockPoliciesActor.underlyingActor()
                    .setReply(POLICY_SUDO, sudoRetrievePolicyResponse);

            // WHEN: received RetrieveThing
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            underTest.tell(retrieveThing, getRef());

            // THEN: initial requester receives Thing with inline policy
            final RetrieveThingResponse response = expectMsgClass(RetrieveThingResponse.class);
            assertThat(response.getThingId()).isEqualTo(thingId);
            assertThat(response.getEntity()).isObject();
            assertThat(response.getEntity().asObject()).doesNotContain(JsonKey.of("_policy"));
        }};
    }

    @Test
    public void retrieveThingErrorIfCacheIsOutOfDate() {
        new TestKit(system) {{
            final ActorRef underTest = newEnforcerActor(getRef());

            // GIVEN: Thing and its Policy exist in cache but not actually
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = emptyThing(thingId, policyId);
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());
            final Policy policy = thingOnlyPolicy(policyId);
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());
            mockThingsActor.underlyingActor()
                    .setReply(THING_SUDO, sudoRetrieveThingResponse)
                    .setReply(THING, ThingNotAccessibleException.newBuilder(thingId).build());
            mockPoliciesActor.underlyingActor()
                    .setReply(POLICY_SUDO, sudoRetrievePolicyResponse)
                    .setReply(POLICY, PolicyNotAccessibleException.newBuilder(policyId).build());

            // WHEN: received RetrieveThing
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            underTest.tell(retrieveThing, getRef());

            // THEN: initial requester receives error
            expectMsgClass(ThingNotAccessibleException.class);

            // WHEN: Thing exists but Policy exists only in cache
            mockThingsActor.underlyingActor()
                    .setReply(THING, RetrieveThingResponse.of(thingId, thing, DEFAULT_HEADERS));
            underTest.tell(retrieveThing, getRef());

            // THEN: initial requester receives Thing without Policy
            final RetrieveThingResponse response = expectMsgClass(RetrieveThingResponse.class);
            assertThat(response.getThingId()).isEqualTo(thingId);
            assertThat(response.getEntity()).isObject();
            assertThat(response.getEntity().asObject()).doesNotContain(JsonKey.of("_policy"));
        }};
    }

    @Test
    public void retrieveThingErrorOnTimeout() {
        new TestKit(system) {{
            final ActorRef underTest = newEnforcerActor(getRef());

            // GIVEN: Thing and its Policy exist in cache
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = emptyThing(thingId, policyId);
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());
            final Policy policy = defaultPolicy(policyId);
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());
            mockThingsActor.underlyingActor()
                    .setReply(THING_SUDO, sudoRetrieveThingResponse)
                    .setReply(THING, new AskTimeoutException("thing timeout"));
            mockPoliciesActor.underlyingActor()
                    .setReply(POLICY_SUDO, sudoRetrievePolicyResponse)
                    .setReply(POLICY, new AskTimeoutException("policy timeout"));

            // WHEN: received RetrieveThing but both shard regions time out
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            underTest.tell(retrieveThing, getRef());

            // THEN: initial requester receives error
            expectMsgClass(ThingUnavailableException.class);

            // WHEN: Thing is responsive but Policy times out
            mockThingsActor.underlyingActor()
                    .setReply(THING, RetrieveThingResponse.of(thingId, thing, DEFAULT_HEADERS));
            underTest.tell(retrieveThing, getRef());

            // THEN: initial requester receives Thing without Policy
            final RetrieveThingResponse response = expectMsgClass(RetrieveThingResponse.class);
            assertThat(response.getThingId()).isEqualTo(thingId);
            assertThat(response.getEntity()).isObject();
            assertThat(response.getEntity().asObject()).doesNotContain(JsonKey.of("_policy"));
        }};
    }

    @Test
    public void modifyExistingThing() {
        new TestKit(system) {{
            final ActorRef underTest = newEnforcerActor(getRef());

            // GIVEN: Thing and its Policy exist
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = emptyThing(thingId, policyId);
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());
            final Policy policy = defaultPolicy(policyId);
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());
            mockThingsActor.underlyingActor()
                    .setReply(THING_SUDO, sudoRetrieveThingResponse)
                    .setReply(ModifyThing.TYPE, ModifyThingResponse.modified(thingId, DEFAULT_HEADERS));
            mockPoliciesActor.underlyingActor()
                    .setReply(POLICY_SUDO, sudoRetrievePolicyResponse);

            // WHEN: received ModifyThing
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, DEFAULT_HEADERS);

            underTest.tell(modifyThing, getRef());

            // THEN: initial requester receives success
            expectMsgClass(ModifyThingResponse.class);
        }};
    }

    @Test
    public void createThingWithExistingPolicy() {
        new TestKit(system) {{
            final ActorRef underTest = newEnforcerActor(getRef());

            // GIVEN: Thing is nonexistent but its Policy exists
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = emptyThing(thingId, policyId);
            final Policy policy = defaultPolicy(policyId);
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());
            mockThingsActor.underlyingActor()
                    .setReply(THING_SUDO, ThingNotAccessibleException.newBuilder(thingId).build())
                    .setReply(CreateThing.TYPE, CreateThingResponse.of(thing, DEFAULT_HEADERS));
            mockPoliciesActor.underlyingActor()
                    .setReply(POLICY_SUDO, sudoRetrievePolicyResponse);

            // WHEN: received ModifyThing
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, DEFAULT_HEADERS);

            underTest.tell(modifyThing, getRef());

            // THEN: initial requester receives success
            expectMsgClass(CreateThingResponse.class);
        }};
    }

    @Test
    public void createThingAndPolicy() {
        new TestKit(system) {{
            final ActorRef underTest = newEnforcerActor(getRef());

            // GIVEN: Thing and Policy are nonexistent
            final String thingId = "thing:" + UUID.randomUUID();
            final Thing thing = emptyThing(thingId, null);
            final Policy policy = defaultPolicy(thingId);
            mockThingsActor.underlyingActor()
                    .setReply(THING_SUDO, ThingNotAccessibleException.newBuilder(thingId).build())
                    .setReply(CreateThing.TYPE, CreateThingResponse.of(thing, DEFAULT_HEADERS));
            mockPoliciesActor.underlyingActor()
                    .setReply(POLICY_SUDO, PolicyNotAccessibleException.newBuilder(thingId).build())
                    .setReply(CreatePolicy.TYPE, CreatePolicyResponse.of(thingId, policy, DEFAULT_HEADERS));


            // WHEN: received ModifyThing
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, DEFAULT_HEADERS);

            underTest.tell(modifyThing, getRef());

            // THEN: initial requester receives success
            expectMsgClass(CreateThingResponse.class);
        }};
    }

    @Test
    public void createThingWithPolicyConflict() {
        new TestKit(system) {{
            final ActorRef underTest = newEnforcerActor(getRef());

            // GIVEN: Thing is nonexistent but Policy exists
            final String thingId = "thing:" + UUID.randomUUID();
            final Thing thing = emptyThing(thingId, null);
            final Policy policy = defaultPolicy(thingId);
            mockThingsActor.underlyingActor()
                    .setReply(THING_SUDO, ThingNotAccessibleException.newBuilder(thingId).build());
            mockPoliciesActor.underlyingActor()
                    .setReply(POLICY_SUDO, SudoRetrievePolicyResponse.of(thingId, policy, DittoHeaders.empty()))
                    .setReply(CreatePolicy.TYPE, PolicyConflictException.newBuilder(thingId).build());


            // WHEN: received ModifyThing
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, DEFAULT_HEADERS);

            underTest.tell(modifyThing, getRef());

            // THEN: initial requester receives failure
            final ThingNotCreatableException error = expectMsgClass(ThingNotCreatableException.class);
            assertThat(error.getMessage()).contains("implicit Policy", "already existing");
        }};
    }

    @Test
    public void createThingWithExplicitPolicy() {
        new TestKit(system) {{
            final ActorRef underTest = newEnforcerActor(getRef());

            // GIVEN: Thing and Policy do not exist
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = emptyThing(thingId, policyId);
            final Policy policy = defaultPolicy(policyId);
            mockThingsActor.underlyingActor()
                    .setReply(THING_SUDO, ThingNotAccessibleException.newBuilder(thingId).build())
                    .setReply(CreateThing.TYPE, CreateThingResponse.of(thing, DEFAULT_HEADERS));
            mockPoliciesActor.underlyingActor()
                    .setReply(POLICY_SUDO, PolicyNotAccessibleException.newBuilder(policyId).build())
                    .setReply(CreatePolicy.TYPE, CreatePolicyResponse.of(policyId, policy, DEFAULT_HEADERS));

            // WHEN: received ModifyThing
            final ModifyThing modifyThing =
                    ModifyThing.of(thingId, thing, policy.toJson(), DEFAULT_HEADERS);

            underTest.tell(modifyThing, getRef());

            // THEN: initial requester receives success
            expectMsgClass(CreateThingResponse.class);
        }};
    }

    @Test
    public void createThingWithExplicitPolicyNotAuthorizedBySelf() {
        new TestKit(system) {{
            final ActorRef underTest = newEnforcerActor(getRef());

            // GIVEN: Thing and Policy do not exist
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = emptyThing(thingId, policyId);
            final Policy policy = thingOnlyPolicy(policyId);
            mockThingsActor.underlyingActor()
                    .setReply(THING_SUDO, ThingNotAccessibleException.newBuilder(thingId).build())
                    .setReply(CreateThing.TYPE, CreateThingResponse.of(thing, DEFAULT_HEADERS));
            mockPoliciesActor.underlyingActor()
                    .setReply(POLICY_SUDO, PolicyNotAccessibleException.newBuilder(policyId).build())
                    .setReply(CreatePolicy.TYPE, CreatePolicyResponse.of(policyId, policy, DEFAULT_HEADERS));

            // WHEN: received ModifyThing whose inline policy does not permit creation of itself
            final ModifyThing modifyThing =
                    ModifyThing.of(thingId, thing, policy.toJson(), DEFAULT_HEADERS);

            underTest.tell(modifyThing, getRef());

            // THEN: initial requester receives error
            expectMsgClass(ThingNotModifiableException.class);
        }};
    }

    private ActorRef newEnforcerActor(final ActorRef testActorRef) {
        return TestSetup.newEnforcerActor(system, testActorRef, mockThingsActor, mockPoliciesActor, null);
    }

    private static TestActorRef<MockEntitiesActor> newMockEntitiesActor(final ActorSystem system) {
        return new TestActorRef<>(system, MockEntitiesActor.props(), system.guardian(), UUID.randomUUID().toString());
    }

    private static Thing emptyThing(final String thingId, @Nullable final String policyId) {
        return ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setPolicyId(policyId)
                .setRevision(1L)
                .build();
    }

    private static Policy defaultPolicy(final String policyId) {
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

    private static Policy thingOnlyPolicy(final String policyId) {
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
