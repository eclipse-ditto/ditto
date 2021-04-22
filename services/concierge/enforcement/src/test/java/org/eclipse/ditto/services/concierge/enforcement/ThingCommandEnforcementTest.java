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
package org.eclipse.ditto.services.concierge.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.base.model.json.JsonSchemaVersion.V_2;
import static org.eclipse.ditto.policies.model.SubjectIssuer.GOOGLE;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.POLICY_SUDO;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.SUBJECT;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.SUBJECT_ID;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.THING_ID;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.THING_SUDO;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.fishForMsgClass;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyPolicyId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyPolicyIdResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttribute;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.Duration;

@SuppressWarnings({"squid:S3599", "squid:S1171"})
public final class ThingCommandEnforcementTest {

    @Rule
    public final TestName testName = new TestName();

    private ActorSystem system;
    private MockEntitiesActor mockEntitiesActorInstance;
    private ActorRef mockEntitiesActor;

    @Before
    public void init() {
        system = ActorSystem.create("test", ConfigFactory.load("test"));
        final TestActorRef<MockEntitiesActor> testActorRef =
                new TestActorRef<>(system, MockEntitiesActor.props(), system.guardian(), UUID.randomUUID().toString());
        mockEntitiesActorInstance = testActorRef.underlyingActor();
        mockEntitiesActor = testActorRef;
    }

    @After
    public void shutdown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void rejectByPolicy() {
        final PolicyId policyId = PolicyId.of("empty:policy");
        final JsonObject thingWithEmptyPolicy = newThingWithPolicyId(policyId);
        final JsonObject emptyPolicy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .build()
                .toJson(FieldType.all());
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithEmptyPolicy, DittoHeaders.empty());
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, emptyPolicy, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(getReadCommand(), getRef());
            fishForMsgClass(this, ThingNotAccessibleException.class);

            underTest.tell(getModifyCommand(), getRef());
            expectMsgClass(FeatureNotModifiableException.class);
        }};
    }

    @Test
    public void rejectQueryByThingNotAccessibleException() {
        final DittoRuntimeException error = ThingNotAccessibleException.newBuilder(THING_ID).build();

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, error);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(getReadCommand(), getRef());
            fishForMessage(Duration.create(500, TimeUnit.MILLISECONDS), "error", msg -> msg.equals(error));
        }};
    }

    @Test
    public void rejectUpdateByThingNotAccessibleException() {
        final DittoRuntimeException error = ThingNotAccessibleException.newBuilder(THING_ID).build();
        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, error);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(getModifyCommand(), getRef());
            fishForMsgClass(this, ThingNotAccessibleException.class);
        }};
    }

    @Test
    public void rejectQueryByPolicyNotAccessibleException() {
        final PolicyId policyId = PolicyId.of("not:accessible");
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(newThingWithPolicyId(policyId), DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(POLICY_SUDO,
                    PolicyNotAccessibleException.newBuilder(policyId).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(getReadCommand(), getRef());
            final DittoRuntimeException error = fishForMsgClass(this, ThingNotAccessibleException.class);
            assertThat(error.getMessage()).contains(policyId);
            assertThat(error.getDescription().orElse("")).contains(policyId);
        }};
    }

    @Test
    public void rejectUpdateByPolicyNotAccessibleException() {
        final PolicyId policyId = PolicyId.of("not:accessible");
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(newThingWithPolicyId(policyId), DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(POLICY_SUDO,
                    PolicyNotAccessibleException.newBuilder(policyId).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(getModifyCommand(), getRef());
            final DittoRuntimeException error = fishForMsgClass(this, ThingNotModifiableException.class);
            assertThat(error.getMessage()).contains(policyId);
            assertThat(error.getDescription().orElse("")).contains(policyId);
        }};
    }

    @Test
    public void rejectCreateByOwnPolicy() {
        final PolicyId policyId = PolicyId.of("empty:policy");
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("dummy")
                .setSubject(GOOGLE, "not-subject")
                .setGrantedPermissions(PoliciesResourceType.policyResource("/"),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final Thing thing = newThing().build();

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO,
                    ThingNotAccessibleException.newBuilder(THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, policy.toJson(), headers(V_2));
            underTest.tell(createThing, getRef());
            fishForMsgClass(this, ThingNotModifiableException.class);
        }};

    }

    @Test
    public void acceptByPolicy() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build()
                .toJson(FieldType.all());
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithPolicy, DittoHeaders.empty());
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());

            final ThingCommand write = getModifyCommand();
            mockEntitiesActorInstance.setReply(write);
            underTest.tell(write, getRef());
            assertThat((CharSequence) fishForMsgClass(this, write.getClass()).getEntityId())
                    .isEqualTo(write.getEntityId());

            final ThingCommand read = getReadCommand();
            final RetrieveThingResponse retrieveThingResponse =
                    RetrieveThingResponse.of(THING_ID, JsonFactory.newObject(), DittoHeaders.empty());
            mockEntitiesActorInstance.setReply(retrieveThingResponse);
            underTest.tell(read, getRef());
            assertThat((CharSequence) expectMsgClass(retrieveThingResponse.getClass()).getEntityId()).isEqualTo(
                    read.getEntityId());
        }};
    }

    @Test
    public void acceptByPolicyWithRevokeOnAttribute() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = newThingWithAttributeWithPolicyId(policyId);
        final JsonPointer attributePointer = JsonPointer.of("/attributes/testAttr");
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setRevokedPermissions(PoliciesResourceType.thingResource(attributePointer),
                        Permissions.newInstance(Permission.READ))
                .build()
                .toJson(FieldType.all());
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithPolicy, DittoHeaders.empty());
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());

            final ThingCommand modifyCommand = getModifyCommand();
            mockEntitiesActorInstance.setReply(modifyCommand);
            underTest.tell(modifyCommand, getRef());
            assertThat((CharSequence) fishForMsgClass(this, modifyCommand.getClass()).getEntityId())
                    .isEqualTo(modifyCommand.getEntityId());

            final RetrieveThingResponse retrieveThingResponseWithAttr =
                    RetrieveThingResponse.of(THING_ID, thingWithPolicy, headers(V_2));

            final JsonObject jsonObjectWithoutAttr = thingWithPolicy.remove(attributePointer);
            final RetrieveThingResponse retrieveThingResponseWithoutAttr =
                    RetrieveThingResponse.of(THING_ID, jsonObjectWithoutAttr, headers(V_2));

            mockEntitiesActorInstance.setReply(retrieveThingResponseWithAttr);
            underTest.tell(getReadCommand(), getRef());

            final RetrieveThingResponse retrieveThingResponse =
                    expectMsgClass(retrieveThingResponseWithoutAttr.getClass());
            assertThat((CharSequence) retrieveThingResponse.getEntityId()).isEqualTo(
                    retrieveThingResponseWithoutAttr.getEntityId());
            DittoJsonAssertions.assertThat(retrieveThingResponse.getEntity())
                    .hasJsonString(retrieveThingResponseWithoutAttr.getEntity().toString());
        }};
    }

    @Test
    public void acceptCreateByInlinePolicy() {
        final PolicyId policyId = PolicyId.of(THING_ID);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .build();
        final Thing thing = newThing().build();

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO,
                    ThingNotAccessibleException.newBuilder(THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, policy.toJson(), headers(V_2));
            mockEntitiesActorInstance.setReply(CreateThingResponse.of(thing, headers(V_2)))
                    .setReply(CreatePolicyResponse.of(policyId, policy, headers(V_2)));
            underTest.tell(createThing, getRef());
            final CreateThingResponse expectedCreateThingResponse = fishForMsgClass(this, CreateThingResponse.class);
            assertThat(expectedCreateThingResponse.getThingCreated().orElse(null)).isEqualTo(thing);
        }};
    }

    @Test
    public void acceptCreateByImplicitPolicyAndInvalidateCache() {
        final Thing thing = newThing().build();
        final PolicyId policyId = PolicyId.of(THING_ID);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId).build();
        final AtomicInteger sudoRetrieveThingCounter = new AtomicInteger(0);
        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(CreateThingResponse.of(thing, headers(V_2)))
                    .setReply(CreatePolicyResponse.of(policyId, policy, headers(V_2)))
                    .setHandler(THING_SUDO, sudo -> {
                        sudoRetrieveThingCounter.getAndIncrement();
                        return ThingNotAccessibleException.newBuilder(THING_ID).build();
                    });

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, null, headers(V_2));

            // first Thing command triggers cache load
            underTest.tell(createThing, getRef());

            // cache should be invalidated before response is sent
            fishForMsgClass(this, CreateThingResponse.class);

            // second Thing command should trigger cache load again
            underTest.tell(createThing, getRef());
            fishForMsgClass(this, CreateThingResponse.class);

            // verify cache is loaded twice
            assertThat(sudoRetrieveThingCounter.get()).isEqualTo(2);
        }};
    }

    @Test
    public void testParallelEnforcementTaskScheduling() {
        final TestProbe pubSubProbe = TestProbe.apply("pubSubMediator", system);
        final TestProbe entitiesProbe = TestProbe.apply("entities", system);

        final Thing thing = newThing().build();
        final PolicyId policyId = PolicyId.of(THING_ID);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final CreatePolicyResponse createPolicyResponse = CreatePolicyResponse.of(policyId, policy, headers(V_2));
        final ThingNotAccessibleException thingNotAccessibleException =
                ThingNotAccessibleException.newBuilder(THING_ID).build();
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(
                        JsonObject.newBuilder()
                                .set(Thing.JsonFields.ID, THING_ID.toString())
                                .set(Thing.JsonFields.POLICY_ID, policyId.toString())
                                .set(Thing.JsonFields.REVISION, 1L)
                                .build(),
                        headers(V_2));
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy.toJson(FieldType.all()), headers(V_2));
        final CreateThing createThing = CreateThing.of(thing, null, headers(V_2));
        final CreateThingResponse createThingResponse = CreateThingResponse.of(thing, headers(V_2));
        final RetrieveThing retrieveThing = RetrieveThing.of(THING_ID, headers(V_2));
        final RetrieveThingResponse retrieveThingResponse =
                RetrieveThingResponse.of(THING_ID, JsonObject.empty(), headers(V_2));
        final ModifyPolicyId modifyPolicyId = ModifyPolicyId.of(THING_ID, policyId, headers(V_2));
        final ModifyPolicyIdResponse modifyPolicyIdResponse = ModifyPolicyIdResponse.modified(THING_ID, headers(V_2));
        final ModifyAttribute modifyAttribute =
                ModifyAttribute.of(THING_ID, JsonPointer.of("x"), JsonValue.of(5), headers(V_2));
        final ModifyAttributeResponse modifyAttributeResponse =
                ModifyAttributeResponse.created(THING_ID, JsonPointer.of("x"), JsonValue.of(5), headers(V_2));
        final RetrieveAttribute retrieveAttribute =
                RetrieveAttribute.of(THING_ID, JsonPointer.of("x"), headers(V_2));
        final RetrieveAttributeResponse retrieveAttributeResponse =
                RetrieveAttributeResponse.of(THING_ID, JsonPointer.of("x"), JsonValue.of(5), headers(V_2));

        new TestKit(system) {{
            final ActorRef underTest =
                    TestSetup.newEnforcerActor(system, pubSubProbe.ref(), entitiesProbe.ref());

            // GIVEN: all commands are sent in one batch
            underTest.tell(createThing, getRef());
            underTest.tell(retrieveThing, getRef());
            underTest.tell(modifyPolicyId, getRef());
            underTest.tell(modifyAttribute, getRef());
            underTest.tell(retrieveAttribute, getRef());

            // THEN: expect enforcement start: CreateThing
            entitiesProbe.expectMsgClass(SudoRetrieveThing.class);
            entitiesProbe.reply(thingNotAccessibleException);
            entitiesProbe.expectMsgClass(CreatePolicy.class);

            // THEN: no other message should come through before enforcement of CreateThing completes
            entitiesProbe.expectNoMessage(Duration.create(150, TimeUnit.MILLISECONDS));

            // WHEN: CreateThing completes
            entitiesProbe.reply(createPolicyResponse);
            entitiesProbe.expectMsgClass(CreateThing.class);
            final ActorRef createThingSender = entitiesProbe.lastSender();

            // THEN: cache reloads immediately due to queued commands
            entitiesProbe.expectMsgClass(SudoRetrieveThing.class);
            entitiesProbe.reply(sudoRetrieveThingResponse);
            entitiesProbe.expectMsgClass(SudoRetrievePolicy.class);

            // WHEN: cache reload completes
            entitiesProbe.reply(sudoRetrievePolicyResponse);

            // THEN: the next commands are dispatched until authorization change happens again
            entitiesProbe.expectMsgClass(RetrieveThing.class);
            final ActorRef retrieveThingSender = entitiesProbe.lastSender();
            entitiesProbe.expectMsgClass(ModifyPolicyId.class);
            final ActorRef modifyPolicyIdSender = entitiesProbe.lastSender();

            // THEN: the authorization-changing command causes a cache reload
            entitiesProbe.expectMsgClass(SudoRetrieveThing.class);

            // WHEN: cache reload completes
            entitiesProbe.reply(sudoRetrieveThingResponse);

            // THEN: the other queued commands are dispatched
            entitiesProbe.expectMsgClass(ModifyAttribute.class);
            final ActorRef modifyAttributeSender = entitiesProbe.lastSender();
            entitiesProbe.expectMsgClass(RetrieveAttribute.class);
            final ActorRef retrieveAttributeSender = entitiesProbe.lastSender();

            // WHEN: responses come back from the entities shard region
            // THEN: responses are delivered to the command sender regardless of command order

            modifyAttributeSender.tell(modifyAttributeResponse, ActorRef.noSender());
            modifyPolicyIdSender.tell(modifyPolicyIdResponse, ActorRef.noSender());
            createThingSender.tell(createThingResponse, ActorRef.noSender());

            // ThingModifyCommandResponses involve no detour and can be sent together
            expectMsgClass(ModifyAttributeResponse.class);
            expectMsgClass(ModifyPolicyIdResponse.class);
            expectMsgClass(CreateThingResponse.class);

            // ThingQueryCommandResponses involve a detour. If sent together, arrival order won't be deterministic.
            retrieveAttributeSender.tell(retrieveAttributeResponse, ActorRef.noSender());
            expectMsgClass(RetrieveAttributeResponse.class);
            retrieveThingSender.tell(retrieveThingResponse, ActorRef.noSender());
            expectMsgClass(RetrieveThingResponse.class);
        }};
    }

    @Test
    public void acceptCreateByInlinePolicyWithDifferentId() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .build();
        final Thing thing = newThing().build();

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO,
                    ThingNotAccessibleException.newBuilder(THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, policy.toJson(), headers(V_2));
            mockEntitiesActorInstance.setReply(CreateThingResponse.of(thing, headers(V_2)))
                    .setReply(CreatePolicyResponse.of(PolicyId.of(THING_ID), policy, headers(V_2)));
            underTest.tell(createThing, getRef());
            final CreateThingResponse expectedCreateThingResponse = fishForMsgClass(this, CreateThingResponse.class);
            assertThat(expectedCreateThingResponse.getThingCreated().orElse(null)).isEqualTo(thing);
        }};

    }

    @Test
    public void testCreateByCopyFromPolicyWithAllowPolicyLockout() {
        testCreateByCopyFromPolicy(headers(V_2).toBuilder().allowPolicyLockout(true).build(),
                CreateThingResponse.class);
    }

    @Test
    public void testCreateByCopyFromPolicyWithPolicyLockout() {
        testCreateByCopyFromPolicy(headers(V_2), ThingNotModifiableException.class);
    }

    @Test
    public void testCreateByCopyFromPolicyWithIfNoneMatch() {
        // the "if-none-match" headers must be removed when retrieving the policy to copy
        // if they are not removed, a PolicyPreconditionNotModifiedException would be returned
        testCreateByCopyFromPolicy(headers(V_2).toBuilder()
                        .allowPolicyLockout(true)
                        .ifNoneMatch(EntityTagMatchers.fromList(Collections.singletonList(EntityTagMatcher.asterisk())))
                        .build(),
                CreateThingResponse.class);
    }

    private void testCreateByCopyFromPolicy(final DittoHeaders dittoHeaders, final Class<?> expectedResponseClass) {
        final PolicyId policyId = PolicyId.of("policy:id");
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                // authorized subject has WRITE on thing:/ resource
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .forLabel("admin")
                .setSubject(GOOGLE, "admin")
                // some other subject has WRITE on policy:/ resource
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .build();
        final Thing thing = newThing().build();

        new TestKit(system) {{
            mockEntitiesActorInstance
                    .setReply(THING_SUDO, ThingNotAccessibleException.newBuilder(THING_ID).build())
                    .setReply(CreatePolicyResponse.of(policyId, policy, headers(V_2)));

            final TestProbe conciergeProbe = new TestProbe(system, TestSetup.createUniqueName());

            final ActorRef underTest = newEnforcerActor(getRef(), conciergeProbe.ref());
            final CreateThing createThing = CreateThing.of(thing, null, policyId.toString(), dittoHeaders);

            mockEntitiesActorInstance.setReply(CreateThingResponse.of(thing, headers(V_2)));

            underTest.tell(createThing, getRef());

            final RetrievePolicy retrievePolicy = conciergeProbe.expectMsgClass(RetrievePolicy.class);
            assertThat(retrievePolicy.getDittoHeaders().getIfMatch()).isEmpty();
            assertThat(retrievePolicy.getDittoHeaders().getIfNoneMatch()).isEmpty();
            conciergeProbe.lastSender().tell(RetrievePolicyResponse.of(policyId, policy,
                    retrievePolicy.getDittoHeaders()), ActorRef.noSender());

            fishForMsgClass(this, expectedResponseClass);
        }};
    }

    @Test
    public void rejectCreateByInlinePolicyWithInvalidId() {
        final PolicyId policyId = PolicyId.of(THING_ID);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .build();
        final JsonObject invalidPolicyJson = policy.toJson()
                .setValue("policyId", "invalid-policy-id");
        final Thing thing = newThing().build();

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO,
                    ThingNotAccessibleException.newBuilder(THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, invalidPolicyJson, headers(V_2));
            mockEntitiesActorInstance.setReply(CreateThingResponse.of(thing, headers(V_2)))
                    .setReply(CreatePolicyResponse.of(policyId, policy, headers(V_2)));
            underTest.tell(createThing, getRef());

            // result may not be an instance of PolicyIdInvalidException but should have the same error code.
            final DittoRuntimeException response = fishForMsgClass(this, DittoRuntimeException.class);
            assertThat(response.getErrorCode()).isEqualTo(PolicyIdInvalidException.ERROR_CODE);
        }};
    }

    @Test
    public void rejectCreateByInvalidPolicy() {
        final PolicyId policyId = PolicyId.of(THING_ID);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .setModified(Instant.now())
                .build();
        final JsonObject invalidPolicyJson = policy.toJson()
                .setValue("_modified", "invalid-timestamp");
        final Thing thing = newThing().build();

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO,
                    ThingNotAccessibleException.newBuilder(THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, invalidPolicyJson, headers(V_2));
            mockEntitiesActorInstance.setReply(CreateThingResponse.of(thing, headers(V_2)))
                    .setReply(CreatePolicyResponse.of(policyId, policy, headers(V_2)));
            underTest.tell(createThing, getRef());

            // result may not be an instance of PolicyInvalidException but should have the same error code.
            final DittoRuntimeException response = fishForMsgClass(this, DittoRuntimeException.class);
            assertThat(response.getErrorCode()).isEqualTo(PolicyInvalidException.ERROR_CODE);
        }};

    }

    @Test
    public void transformModifyThingToCreateThing() {
        final Thing thingInPayload = newThing().setId(THING_ID).build();

        new TestKit(system) {{
            final ActorRef underTest = TestSetup.newEnforcerActor(system, getRef(), getRef());
            final ModifyThing modifyThing = ModifyThing.of(THING_ID, thingInPayload, null, headers(V_2));
            underTest.tell(modifyThing, getRef());

            fishForMsgClass(this, SudoRetrieveThing.class);
            reply(ThingNotAccessibleException.newBuilder(THING_ID).build());

            final CreatePolicy createPolicy = fishForMsgClass(this, CreatePolicy.class);
            reply(CreatePolicyResponse.of(createPolicy.getEntityId(), createPolicy.getPolicy(),
                    createPolicy.getDittoHeaders()));

            fishForMsgClass(this, CreateThing.class);
        }};
    }

    private ActorRef newEnforcerActor(final ActorRef testActorRef) {
        return TestSetup.newEnforcerActor(system, testActorRef, mockEntitiesActor);
    }

    private ActorRef newEnforcerActor(final ActorRef testActorRef, final ActorRef conciergeForwarderRef) {
        return new TestSetup.EnforcerActorBuilder(system, testActorRef, mockEntitiesActor).setConciergeForwarder(
                conciergeForwarderRef).build();
    }

    private static JsonObject newThingWithPolicyId(final CharSequence policyId) {
        return newThing()
                .setPolicyId(PolicyId.of(policyId))
                .build()
                .toJson(V_2, FieldType.all());
    }

    private DittoHeaders headers(final JsonSchemaVersion schemaVersion) {
        return DittoHeaders.newBuilder()
                .authorizationContext(
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, SUBJECT,
                                AuthorizationSubject.newInstance(String.format("%s:%s", GOOGLE, SUBJECT_ID))))
                .correlationId(testName.getMethodName())
                .schemaVersion(schemaVersion)
                .build();
    }

    private static ThingBuilder.FromScratch newThing() {
        return ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(1L);
    }

    private static JsonObject newThingWithAttributeWithPolicyId(final CharSequence policyId) {
        return ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setAttribute(JsonPointer.of("/testAttr"), JsonValue.of("testString"))
                .setRevision(1L)
                .setPolicyId(PolicyId.of(policyId))
                .build()
                .toJson(V_2, FieldType.all());
    }

    private ThingCommand getReadCommand() {
        return RetrieveThing.of(THING_ID, headers(V_2));
    }

    private ThingCommand getModifyCommand() {
        return ModifyFeature.of(THING_ID, Feature.newBuilder().withId("x").build(), headers(V_2));
    }

}
