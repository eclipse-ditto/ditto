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
import static org.eclipse.ditto.things.service.enforcement.TestSetup.THING_ID;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
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
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
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
import org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.AskTimeoutException;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests commands that triggers different or multiple commands internally.
 */
public final class MultiStageCommandTest {

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
            .build();

    private static final String THING = ThingCommand.RESOURCE_TYPE;
    private static final String POLICY = PolicyCommand.RESOURCE_TYPE;

    private ActorSystem system;
    private TestProbe pubSubMediatorProbe;
    private TestProbe thingPersistenceActorProbe;
    private TestProbe policiesShardRegionProbe;
    private ActorRef supervisor;
    private ThingSupervisorActor mockThingPersistenceSupervisor;

    @Before
    public void init() {
        system = ActorSystem.create("test", ConfigFactory.load("test"));
        pubSubMediatorProbe = createPubSubMediatorProbe();
        thingPersistenceActorProbe = createThingPersistenceActorProbe();
        policiesShardRegionProbe = getTestProbe(createUniqueName("policiesShardRegionProbe-"));
        final TestActorRef<ThingSupervisorActor> thingPersistenceSupervisorTestActorRef =
                createThingPersistenceSupervisor();
        supervisor = thingPersistenceSupervisorTestActorRef;
        mockThingPersistenceSupervisor = thingPersistenceSupervisorTestActorRef.underlyingActor();
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
            // GIVEN: Thing and its Policy exist
            final ThingId thingId = ThingId.of("thing", UUID.randomUUID().toString());
            final PolicyId policyId = PolicyId.of("policy", UUID.randomUUID().toString());
            final Thing thing = emptyThing(thingId, policyId);
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());
            final Policy policy = defaultPolicy(policyId);
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());

            // WHEN: received RetrieveThing
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            final RetrieveThing expectedRetrieveThing = retrieveThing.setDittoHeaders(DEFAULT_HEADERS.toBuilder()
                    .readGrantedSubjects(List.of(AUTHORIZATION_SUBJECT)).build());
            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            thingPersistenceActorProbe.reply(
                    RetrieveThingResponse.of(thingId, thing, null, null, DEFAULT_HEADERS));

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            policiesShardRegionProbe.expectMsgClass(RetrievePolicy.class);
            policiesShardRegionProbe.reply(RetrievePolicyResponse.of(policyId, policy, DEFAULT_HEADERS));

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
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());

            // WHEN: received RetrieveThing
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            final DittoHeaders expectedHeaders = DEFAULT_HEADERS.toBuilder()
                    .readGrantedSubjects(List.of(AUTHORIZATION_SUBJECT)).build();
            final RetrieveThing expectedRetrieveThing = retrieveThing.setDittoHeaders(expectedHeaders);
            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            thingPersistenceActorProbe.reply(
                    RetrieveThingResponse.of(thingId, thing, null, null, expectedHeaders));

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            policiesShardRegionProbe.expectMsgClass(RetrievePolicy.class);
            policiesShardRegionProbe.reply(PolicyNotAccessibleException.newBuilder(policyId).build());

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
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());

            // WHEN: received RetrieveThing
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            final RetrieveThing expectedRetrieveThing = retrieveThing.setDittoHeaders(DEFAULT_HEADERS.toBuilder()
                    .readGrantedSubjects(List.of(AUTHORIZATION_SUBJECT)).build());
            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            thingPersistenceActorProbe.reply(ThingNotAccessibleException.newBuilder(thingId).build());

            // THEN: initial requester receives error
            TestSetup.fishForMsgClass(this, ThingNotAccessibleException.class);

            // WHEN: Thing exists but Policy exists only in cache
            supervisor.tell(retrieveThing, getRef());

            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            thingPersistenceActorProbe.reply(RetrieveThingResponse.of(thingId, thing, null, null, DEFAULT_HEADERS));

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            policiesShardRegionProbe.expectMsgClass(RetrievePolicy.class);
            policiesShardRegionProbe.reply(PolicyNotAccessibleException.newBuilder(policyId).build());

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
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());

            // WHEN: received RetrieveThing but both shard regions time out
            final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("_policy", "thingId");
            final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, DEFAULT_HEADERS)
                    .withSelectedFields(selectedFields)
                    .build();

            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            final RetrieveThing expectedRetrieveThing = retrieveThing.setDittoHeaders(DEFAULT_HEADERS.toBuilder()
                    .readGrantedSubjects(List.of(AUTHORIZATION_SUBJECT)).build());
            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            thingPersistenceActorProbe.reply(new AskTimeoutException("thing timeout"));

            // THEN: initial requester receives error
            TestSetup.fishForMsgClass(this, ThingUnavailableException.class);

            // WHEN: Thing is responsive but Policy times out
            supervisor.tell(retrieveThing, getRef());

            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            thingPersistenceActorProbe.reply(RetrieveThingResponse.of(thingId, thing, null, null, DEFAULT_HEADERS));

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            policiesShardRegionProbe.expectMsgClass(RetrievePolicy.class);
            policiesShardRegionProbe.reply(new AskTimeoutException("policy timeout"));

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
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());

            // WHEN: received ModifyThing
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, DEFAULT_HEADERS);

            supervisor.tell(modifyThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            final DittoHeaders expectedHeaders = DEFAULT_HEADERS.toBuilder()
                    .readGrantedSubjects(List.of(AUTHORIZATION_SUBJECT)).build();
            final ModifyThing expectedModifyThing = modifyThing.setDittoHeaders(expectedHeaders);
            thingPersistenceActorProbe.expectMsg(expectedModifyThing);
            thingPersistenceActorProbe.reply(ModifyThingResponse.modified(thingId, expectedHeaders));

            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

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
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());

            // WHEN: received ModifyThing
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, DEFAULT_HEADERS);

            supervisor.tell(modifyThing, getRef());
            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(thingId).build());
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

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

            // WHEN: received ModifyThing
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, DEFAULT_HEADERS);

            supervisor.tell(modifyThing, getRef());
            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(thingId).build());

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
            final Policy policy = defaultPolicy(policyId);

            // WHEN: received ModifyThing
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, DEFAULT_HEADERS);

            supervisor.tell(modifyThing, getRef());
            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(thingId).build());

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
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                    SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());

            // WHEN: received ModifyThing
            final ModifyThing modifyThing =
                    ModifyThing.of(thingId, thing, policy.toJson(), DEFAULT_HEADERS);

            supervisor.tell(modifyThing, getRef());
            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(thingId).build());

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
            final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                    SudoRetrieveThingResponse.of(thing.toJson(FieldType.all()), DittoHeaders.empty());

            // WHEN: received ModifyThing whose inline policy does not permit creation of itself
            final ModifyThing modifyThing =
                    ModifyThing.of(thingId, thing, policy.toJson(), DEFAULT_HEADERS);

            supervisor.tell(modifyThing, getRef());
            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(thingId).build());

            policiesShardRegionProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionProbe.reply(CreatePolicyResponse.of(policyId, policy, DEFAULT_HEADERS));

            thingPersistenceActorProbe.expectMsgClass(CreateThing.class);
            thingPersistenceActorProbe.reply(ThingNotModifiableException.newBuilder(thingId).build());

            // THEN: initial requester receives error
            expectMsgClass(ThingNotModifiableException.class);
        }};
    }

    private TestActorRef<ThingSupervisorActor> createThingPersistenceSupervisor() {
        return new TestActorRef<>(system, ThingSupervisorActor.props(
                pubSubMediatorProbe.ref(),
                policiesShardRegionProbe.ref(),
                new TestSetup.DummyLiveSignalPub(pubSubMediatorProbe.ref()),
                thingPersistenceActorProbe.ref(),
                null,
                CompletableFuture::completedStage
        ), system.guardian(), URLEncoder.encode(THING_ID.toString(), Charset.defaultCharset()));
    }

    private TestProbe createPubSubMediatorProbe() {
        return getTestProbe(createUniqueName("pubSubMediatorProbe-"));
    }

    private TestProbe createThingPersistenceActorProbe() {
        return getTestProbe(createUniqueName("thingPersistenceActorProbe-"));
    }

    private TestProbe getTestProbe(final String uniqueName) {
        return new TestProbe(system, uniqueName);
    }

    private static String createUniqueName(final String prefix) {
        return prefix + UUID.randomUUID();
    }

    private void expectAndAnswerSudoRetrieveThing(final Object sudoRetrieveThingResponse) {
        final SudoRetrieveThing sudoRetrieveThing =
                thingPersistenceActorProbe.expectMsgClass(SudoRetrieveThing.class);
        assertThat((CharSequence) sudoRetrieveThing.getEntityId()).isEqualTo(THING_ID);
        thingPersistenceActorProbe.reply(sudoRetrieveThingResponse);
    }

    private void expectAndAnswerSudoRetrievePolicy(final PolicyId policyId, final Object sudoRetrievePolicyResponse) {
        final SudoRetrievePolicy sudoRetrievePolicy =
                policiesShardRegionProbe.expectMsgClass(SudoRetrievePolicy.class);
        assertThat((CharSequence) sudoRetrievePolicy.getEntityId()).isEqualTo(policyId);
        policiesShardRegionProbe.reply(sudoRetrievePolicyResponse);
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
