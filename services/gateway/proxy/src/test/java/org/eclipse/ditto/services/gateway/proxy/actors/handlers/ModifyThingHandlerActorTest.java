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
package org.eclipse.ditto.services.gateway.proxy.actors.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.base.json.FieldType.regularOrSpecial;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;

import java.util.UUID;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.utils.cluster.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceUnavailableException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToModifyExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingConflictException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.junit.After;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.testkit.javadsl.TestKit;

/**
 * Tests workflow of {@link ModifyThingHandlerActor}. Update or delete these tests as the behavior of
 * {@link ModifyThingHandlerActor} changes.
 */
public class ModifyThingHandlerActorTest extends AbstractThingHandlerActorTest {

    private static final ThingCommandToModifyExceptionRegistry thingCommandToModifyExceptionRegistry =
            ThingCommandToModifyExceptionRegistry.getInstance();

    private ActorRef underTest;

    private void startHandlerWithEnforcer(final TestKit testkit) {
        underTest = actorSystem.actorOf(
                ModifyThingHandlerActor.props(enforcerShard.ref(), defaultEnforcerId, aclEnforcerShard.ref(),
                        policyEnforcerShard.ref()));
        testkit.watch(underTest);
    }

    private void startHandlerWithoutEnforcer(final TestKit testkit) {
        underTest = actorSystem.actorOf(
                ModifyThingHandlerActor.props(null, null, aclEnforcerShard.ref(), policyEnforcerShard.ref()));
        testkit.watch(underTest);
    }

    private void expectUnderTestTerminated(final TestKit testkit) {
        testkit.expectTerminated(underTest);
        underTest = null;
    }

    @After
    public void cleanupUnderTest() {
        if (actorSystem != null && underTest != null) {
            actorSystem.stop(underTest);
        }
    }

    @Test
    public void modifyThingWithEnforcerHappyPath() {
        new TestKit(actorSystem) {{
            startHandlerWithEnforcer(this);

            // receives ModifyThing
            final String thingId = "thing:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // forwards ModifyThing to the given enforcer shard region
            final ModifyThingResponse modifyThingResponse = ModifyThingResponse.modified(thingId, defaultHeaders);
            enforcerShard.expectMsg(createShardedMessage(defaultEnforcerId, modifyThing));
            enforcerShard.reply(modifyThingResponse);

            // initial requester receives the modify thing response
            expectMsg(modifyThingResponse);
            expectUnderTestTerminated(this);
        }};
    }

    @Test
    public void modifyThingWithEnforcerWithModifyFailure() {
        new TestKit(actorSystem) {{
            startHandlerWithEnforcer(this);

            // receives ModifyThing
            final String thingId = "thing:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // forwards ModifyThing to the given enforcer shard region, but the command failed
            final Object failureResponse =
                    GatewayServiceUnavailableException.fromMessage(UUID.randomUUID().toString(), defaultHeaders);
            enforcerShard.expectMsg(createShardedMessage(defaultEnforcerId, modifyThing));
            enforcerShard.reply(failureResponse);

            // initial requester receives the failure response verbatim
            expectMsg(failureResponse);
            expectUnderTestTerminated(this);
        }};
    }

    @Test
    public void modifyThingWithEnforcerNoDittoHeaders() {
        new TestKit(actorSystem) {{
            final DittoHeaders emptyHeaders = DittoHeaders.empty();
            startHandlerWithEnforcer(this);

            // receives ModifyThing
            final String thingId = "thing:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, emptyHeaders);
            underTest.tell(modifyThing, getRef());

            // forwards ModifyThing to the given enforcer shard region, which fails because of no headers
            final DittoRuntimeException error = thingCommandToModifyExceptionRegistry.exceptionFrom(modifyThing);
            enforcerShard.expectMsg(createShardedMessage(defaultEnforcerId, modifyThing));
            enforcerShard.reply(error);

            // initial requester receives the failure response verbatim
            expectMsg(error);
            expectUnderTestTerminated(this);
        }};
    }

    @Test
    public void modifyThingWithoutPolicyIDWithoutEnforcerWithExistingPolicy() {
        new TestKit(actorSystem) {{
            startHandlerWithoutEnforcer(this);

            // receives ModifyThing
            final String thingId = "thing:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // assumes thing doesn't exist because enforcer doesn't exist
            // transforms ModifyThing to CreateThing right away
            // due to absence of enforcer, assumes nonexistence of policy and creates implicit policy, which fails
            final PolicyConflictException policyConflictException =
                    PolicyConflictException.fromMessage(UUID.randomUUID().toString(), defaultHeaders);
            assertThat(policyEnforcerShard.expectMsgClass(ShardedMessageEnvelope.class).getType())
                    .isEqualTo(CreatePolicy.TYPE);
            policyEnforcerShard.reply(policyConflictException);

            // replies an error due to policy creation failure
            final DittoRuntimeException error = ThingNotCreatableException.newBuilderForPolicyExisting(thingId, thingId)
                    .dittoHeaders(defaultHeaders)
                    .build();

            // initial requester receives the failure response verbatim
            expectMsg(error);
            expectUnderTestTerminated(this);
        }};

    }

    @Test
    public void createThingWithoutPolicyWithoutEnforcerHappyPath() {
        new TestKit(actorSystem) {{
            startHandlerWithoutEnforcer(this);

            // receives ModifyThing
            final String thingId = "thing:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).build();
            final Policy policy = Policy.newBuilder(thingId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // assumes thing doesn't exist because enforcer doesn't exist
            // transforms ModifyThing to CreateThing right away
            // due to absence of enforcer, assumes nonexistence of policy and creates implicit policy, which succeeds
            final CreatePolicyResponse createPolicyResponse = CreatePolicyResponse.of(thingId, policy, defaultHeaders);
            assertThat(policyEnforcerShard.expectMsgClass(ShardedMessageEnvelope.class).getType())
                    .isEqualTo(CreatePolicy.TYPE);
            policyEnforcerShard.reply(createPolicyResponse);

            // sends CreateThing to policy enforcer shard with newly created policy ID, which succeeds
            final Thing thingWithPolicyId = thing.setPolicyId(thingId);
            final CreateThing createThingWithPolicyId = CreateThing.of(thingWithPolicyId, null, defaultHeaders);
            final ShardedMessageEnvelope createThingEnvelope = ShardedMessageEnvelope.of(thingId, CreateThing.TYPE,
                    createThingWithPolicyId.toJson(V_2, regularOrSpecial()), defaultHeaders);
            final CreateThingResponse createThingResponse = CreateThingResponse.of(thingWithPolicyId, defaultHeaders);
            assertThat(policyEnforcerShard.expectMsgClass(Object.class)).isEqualTo(createThingEnvelope);
            policyEnforcerShard.reply(createThingResponse);

            // initial requester receives the success response with inlined policy
            final CreateThingResponse finalResponse =
                    CreateThingResponse.of(thingWithPolicyId, defaultHeaders);
            assertThat(expectMsgClass(Object.class)).isEqualTo(finalResponse);
            expectUnderTestTerminated(this);
        }};
    }

    @Test
    public void createThingWithoutPolicyWithEnforcerHappyPath() {
        new TestKit(actorSystem) {{

            startHandlerWithEnforcer(this);

            // receives ModifyThing
            final String thingId = "thing:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).build();
            final Policy policy = Policy.newBuilder(thingId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // attempts to modify thing but thing did not exist
            enforcerShard.expectMsg(createShardedMessage(defaultEnforcerId, modifyThing));
            enforcerShard.reply(ThingNotAccessibleException.newBuilder(thingId).build());

            // the enforcer is void once it is discovered that the thing associated with the ModifyThing command
            // does not exist. subsequent message exchanges are completely identical to
            // createThingWithoutPolicyWithoutEnforcerHappyPath.

            // transforms ModifyThing to CreateThing right away
            // due to absence of enforcer, assumes nonexistence of policy and creates implicit policy, which succeeds
            final CreatePolicyResponse createPolicyResponse = CreatePolicyResponse.of(thingId, policy, defaultHeaders);
            assertThat(policyEnforcerShard.expectMsgClass(ShardedMessageEnvelope.class).getType())
                    .isEqualTo(CreatePolicy.TYPE);
            policyEnforcerShard.reply(createPolicyResponse);

            // sends CreateThing to policy enforcer shard with newly created policy ID, which succeeds
            final Thing thingWithPolicyId = thing.setPolicyId(thingId);
            final CreateThing createThingWithPolicyId = CreateThing.of(thingWithPolicyId, null, defaultHeaders);
            final ShardedMessageEnvelope createThingEnvelope = ShardedMessageEnvelope.of(thingId, CreateThing.TYPE,
                    createThingWithPolicyId.toJson(V_2, regularOrSpecial()), defaultHeaders);
            final CreateThingResponse createThingResponse = CreateThingResponse.of(thingWithPolicyId, defaultHeaders);
            assertThat(policyEnforcerShard.expectMsgClass(Object.class)).isEqualTo(createThingEnvelope);
            policyEnforcerShard.reply(createThingResponse);

            // initial requester receives the success response with inlined policy
            final CreateThingResponse finalResponse =
                    CreateThingResponse.of(thingWithPolicyId, defaultHeaders);
            assertThat(expectMsgClass(Object.class)).isEqualTo(finalResponse);
            expectUnderTestTerminated(this);
        }};
    }

    @Test
    public void createThingWithPolicyIdWithEnforcerHappyPath() {
        new TestKit(actorSystem) {{
            startHandlerWithEnforcer(this);

            // receives ModifyThing
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).setPolicyId(policyId).build();
            final Policy policy = Policy.newBuilder(policyId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // attempts to modify thing but thing did not exist
            enforcerShard.expectMsg(createShardedMessage(defaultEnforcerId, modifyThing));
            enforcerShard.reply(ThingNotAccessibleException.newBuilder(thingId).build());

            // ensures existence of the policy identified by policyId field of the thing
            policyEnforcerShard.expectMsg(SudoRetrievePolicy.of(policyId, defaultHeaders));
            policyEnforcerShard.reply(SudoRetrievePolicyResponse.of(policyId, policy, defaultHeaders));

            // transforms ModifyThing to CreateThing, which succeeds
            final ShardedMessageEnvelope createThingEnvelope = ShardedMessageEnvelope.of(policyId, CreateThing.TYPE,
                    CreateThing.of(thing, null, defaultHeaders).toJson(V_2, regularOrSpecial()), defaultHeaders);
            policyEnforcerShard.expectMsg(createThingEnvelope);
            policyEnforcerShard.reply(CreateThingResponse.of(thing, defaultHeaders));

            // responds to original requester with policy embedded in thing
            final CreateThingResponse expectedFinalResponse =
                    CreateThingResponse.of(thing.toBuilder().build(), defaultHeaders);
            expectMsg(expectedFinalResponse);
            expectUnderTestTerminated(this);
        }};
    }

    @Test
    public void createThingWithPolicyIdWithEnforcerRaceCondition() {
        new TestKit(actorSystem) {{
            startHandlerWithEnforcer(this);

            // receives ModifyThing
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).setPolicyId(policyId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // attempts to modify thing but thing did not exist
            enforcerShard.expectMsg(createShardedMessage(defaultEnforcerId, modifyThing));
            enforcerShard.reply(ThingNotAccessibleException.newBuilder(thingId).build());

            // ensures existence of the policy identified by policyId field of the thing
            final Policy policy = Policy.newBuilder(policyId).build();
            policyEnforcerShard.expectMsg(SudoRetrievePolicy.of(policyId, defaultHeaders));
            policyEnforcerShard.reply(SudoRetrievePolicyResponse.of(policyId, policy, defaultHeaders));

            // transforms ModifyThing to CreateThing, which fails because someone else created the thing in between
            final ShardedMessageEnvelope createThingEnvelope = ShardedMessageEnvelope.of(policyId, CreateThing.TYPE,
                    CreateThing.of(thing, null, defaultHeaders).toJson(V_2, regularOrSpecial()), defaultHeaders);
            final ThingConflictException thingConflictException =
                    ThingConflictException.newBuilder(thingId).dittoHeaders(defaultHeaders).build();
            policyEnforcerShard.expectMsg(createThingEnvelope);
            policyEnforcerShard.reply(thingConflictException);

            // responds to original requester with the ThingConflictException
            expectMsg(thingConflictException);
            expectUnderTestTerminated(this);
        }};
    }

    @Test
    public void createThingWithPolicyIdWithEnforcerUnauthorizedCreate() {
        new TestKit(actorSystem) {{
            startHandlerWithEnforcer(this);

            // receives ModifyThing
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).setPolicyId(policyId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // attempts to modify thing but thing did not exist
            enforcerShard.expectMsg(createShardedMessage(defaultEnforcerId, modifyThing));
            enforcerShard.reply(ThingNotAccessibleException.newBuilder(thingId).build());

            // ensures existence of the policy identified by policyId field of the thing
            final Policy policy = Policy.newBuilder(policyId).build();
            policyEnforcerShard.expectMsg(SudoRetrievePolicy.of(policyId, defaultHeaders));
            policyEnforcerShard.reply(SudoRetrievePolicyResponse.of(policyId, policy, defaultHeaders));

            // transforms ModifyThing to CreateThing, which fails because it is not authorized
            final CreateThing createThing = CreateThing.of(thing, null, defaultHeaders);
            final ShardedMessageEnvelope createThingEnvelope = ShardedMessageEnvelope.of(policyId, CreateThing.TYPE,
                    createThing.toJson(V_2, regularOrSpecial()), defaultHeaders);
            final DittoRuntimeException notAuthorized =
                    thingCommandToModifyExceptionRegistry.exceptionFrom(createThing);
            policyEnforcerShard.expectMsg(createThingEnvelope);
            policyEnforcerShard.reply(notAuthorized);

            // responds to original requester with the ThingConflictException
            expectMsg(notAuthorized);
            expectUnderTestTerminated(this);
        }};
    }

    @Test
    public void createThingWithPolicyIdWithEnforcerPolicyNotAccessible() {
        new TestKit(actorSystem) {{
            startHandlerWithEnforcer(this);

            // receives ModifyThing
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).setPolicyId(policyId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // attempts to modify thing but thing did not exist
            enforcerShard.expectMsg(createShardedMessage(defaultEnforcerId, modifyThing));
            enforcerShard.reply(ThingNotAccessibleException.newBuilder(thingId).build());

            // ensures existence of the policy identified by policyId field of the thing
            final PolicyNotAccessibleException policyNotAccessibleException =
                    PolicyNotAccessibleException.fromMessage(UUID.randomUUID().toString(), defaultHeaders);
            policyEnforcerShard.expectMsg(SudoRetrievePolicy.of(policyId, defaultHeaders));
            policyEnforcerShard.reply(policyNotAccessibleException);

            // responds to original requester with ThingNotCreatableException
            final ThingNotCreatableException expectedFinalResponse =
                    ThingNotCreatableException.newBuilderForPolicyMissing(thingId, policyId)
                            .dittoHeaders(defaultHeaders)
                            .build();
            expectMsg(expectedFinalResponse);
            expectUnderTestTerminated(this);
        }};
    }

    @Test
    public void createThingWithPolicyIdWithEnforcerPolicyNotAvailable() {
        new TestKit(actorSystem) {{
            startHandlerWithEnforcer(this);

            // receives ModifyThing
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).setPolicyId(policyId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // attempts to modify thing but thing did not exist
            enforcerShard.expectMsg(createShardedMessage(defaultEnforcerId, modifyThing));
            enforcerShard.reply(ThingNotAccessibleException.newBuilder(thingId).build());

            // ensures existence of the policy identified by policyId field of the thing
            final GatewayServiceUnavailableException unavailable =
                    GatewayServiceUnavailableException.fromMessage(UUID.randomUUID().toString(), defaultHeaders);
            policyEnforcerShard.expectMsg(SudoRetrievePolicy.of(policyId, defaultHeaders));
            policyEnforcerShard.reply(unavailable);

            // responds to original requester with unavailability
            expectMsg(unavailable);
            expectUnderTestTerminated(this);
        }};
    }

    @Test
    public void createThingWithPolicyIdWithEnforcerNoDittoHeaders() {
        new TestKit(actorSystem) {{
            final DittoHeaders emptyHeaders = DittoHeaders.empty();
            startHandlerWithEnforcer(this);

            // receives ModifyThing
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).setPolicyId(policyId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, emptyHeaders);
            underTest.tell(modifyThing, getRef());

            // attempts to modify thing but thing did not exist
            enforcerShard.expectMsg(createShardedMessage(defaultEnforcerId, modifyThing));
            enforcerShard.reply(ThingNotAccessibleException.newBuilder(thingId).build());

            // ensures existence of the policy identified by policyId field of the thing
            final Policy policy = Policy.newBuilder(policyId).build();
            policyEnforcerShard.expectMsg(SudoRetrievePolicy.of(policyId, emptyHeaders));
            policyEnforcerShard.reply(SudoRetrievePolicyResponse.of(policyId, policy, emptyHeaders));

            // transforms ModifyThing to CreateThing, which fails because it has no authorization subject in the header
            final CreateThing createThing = CreateThing.of(thing, null, emptyHeaders);
            final ShardedMessageEnvelope createThingEnvelope = ShardedMessageEnvelope.of(policyId, CreateThing.TYPE,
                    createThing.toJson(V_2, regularOrSpecial()), emptyHeaders);
            final DittoRuntimeException notAuthorized =
                    thingCommandToModifyExceptionRegistry.exceptionFrom(createThing);
            policyEnforcerShard.expectMsg(createThingEnvelope);
            policyEnforcerShard.reply(notAuthorized);

            // responds to original requester with the ThingConflictException
            expectMsg(notAuthorized);
            expectUnderTestTerminated(this);
        }};
    }

    @Test
    public void createThingWithInlinedPolicyOfSameIdHappyPath() {
        new TestKit(actorSystem) {{
            startHandlerWithoutEnforcer(this);

            // receives ModifyThing with inlined policy, which is invalid because it has policy ID
            final String thingId = "thing:" + UUID.randomUUID();
            final String subjectId = "subject:" + UUID.randomUUID();
            final Policy policy = Policy.newBuilder(thingId)
                    .forLabel("dummylabel")
                    .setSubject(defaultSubject)
                    .setGrantedPermissions("policy", "/", "WRITE", "EAT")
                    .setGrantedPermissions("thing", "/", "READ")
                    .build();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, policy.toJson(), defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // creates inlined policy
            assertThat(policyEnforcerShard.expectMsgClass(Object.class))
                    .isEqualTo(createShardedMessage(thingId, CreatePolicy.of(policy, defaultHeaders)));
            policyEnforcerShard.reply(CreatePolicyResponse.of(thingId, policy, defaultHeaders));

            // creates thing
            final Thing thingWithPolicyIdWithoutPolicy = thing.setPolicyId(thingId);
            assertThat(policyEnforcerShard.expectMsgClass(Object.class)).isEqualTo(
                    createShardedMessage(thingId,
                            CreateThing.of(thingWithPolicyIdWithoutPolicy, policy.toJson(), defaultHeaders)));
            policyEnforcerShard.reply(CreateThingResponse.of(thingWithPolicyIdWithoutPolicy, defaultHeaders));

            // replies CreateThingResponse with inlined policy
            assertThat(expectMsgClass(Object.class)).isEqualTo(
                    CreateThingResponse.of(thingWithPolicyIdWithoutPolicy, defaultHeaders));
        }};
    }

    @Test
    public void createThingWithInlinedPolicyOfDifferentIdHappyPath() {
        new TestKit(actorSystem) {{
            startHandlerWithoutEnforcer(this);

            // receives ModifyThing with inlined policy, which is invalid because it has policy ID
            final String thingId = "thing:" + UUID.randomUUID();
            final String subjectId = "subject:" + UUID.randomUUID();
            final Policy policy = Policy.newBuilder(thingId)
                    .forLabel("dummylabel")
                    .setSubject(defaultSubject)
                    .setGrantedPermissions("policy", "/", "WRITE", "EAT")
                    .setGrantedPermissions("thing", "/", "READ")
                    .build();
            final Thing thing =
                    ThingsModelFactory.newThingBuilder().setId(thingId).setPolicyId(thingId).build();
            final ModifyThing modifyThing =
                    ModifyThing.of(thingId, thing, policy.toJson().remove("policyId"),
                            defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // verifies nonexistence of policy
            assertThat(policyEnforcerShard.expectMsgClass(Object.class)).isEqualTo(
                    SudoRetrievePolicy.of(thingId, defaultHeaders));
            policyEnforcerShard.reply(
                    PolicyNotAccessibleException.newBuilder(thingId).dittoHeaders(defaultHeaders).build());

            // creates inlined policy
            assertThat(policyEnforcerShard.expectMsgClass(Object.class))
                    .isEqualTo(createShardedMessage(thingId, CreatePolicy.of(policy, defaultHeaders)));
            policyEnforcerShard.reply(CreatePolicyResponse.of(thingId, policy, defaultHeaders));

            // creates thing
            final Thing thingWithPolicyIdWithoutPolicy = thing.setPolicyId(thingId);
            assertThat(policyEnforcerShard.expectMsgClass(Object.class)).isEqualTo(
                    createShardedMessage(thingId,
                            CreateThing.of(thingWithPolicyIdWithoutPolicy, policy.toJson().remove("policyId"), defaultHeaders)));
            policyEnforcerShard.reply(CreateThingResponse.of(thingWithPolicyIdWithoutPolicy, defaultHeaders));

            // replies CreateThingResponse with inlined policy
            assertThat(expectMsgClass(Object.class)).isEqualTo(
                    CreateThingResponse.of(thingWithPolicyIdWithoutPolicy, defaultHeaders));
        }};
    }

    @Test
    public void createThingWithInlinedPolicyOfDifferentIdRaceCondition() {
        new TestKit(actorSystem) {{
            startHandlerWithoutEnforcer(this);

            // receives ModifyThing with inlined policy, which is invalid because it has policy ID
            final String thingId = "thing:" + UUID.randomUUID();
            final String subjectId = "subject:" + UUID.randomUUID();
            final Policy policy = Policy.newBuilder(thingId)
                    .forLabel("dummylabel")
                    .setSubject(Subject.newInstance(SubjectIssuer.GOOGLE_URL, subjectId, SubjectType.JWT))
                    .setGrantedPermissions("policy", "/", "WRITE", "EAT")
                    .build();
            final Thing thing =
                    ThingsModelFactory.newThingBuilder().setId(thingId).setPolicyId(thingId).build();
            final ModifyThing modifyThing =
                    ModifyThing.of(thingId, thing, policy.toJson().remove("policyId"), defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // verifies nonexistence of policy
            assertThat(policyEnforcerShard.expectMsgClass(Object.class)).isEqualTo(
                    SudoRetrievePolicy.of(thingId, defaultHeaders));
            policyEnforcerShard.reply(
                    PolicyNotAccessibleException.newBuilder(thingId).dittoHeaders(defaultHeaders).build());

            // tries to create inlined policy, but someone else beats us to it
            assertThat(policyEnforcerShard.expectMsgClass(Object.class))
                    .isEqualTo(createShardedMessage(thingId, CreatePolicy.of(policy, defaultHeaders)));
            policyEnforcerShard.reply(
                    PolicyConflictException.newBuilder(thingId).dittoHeaders(defaultHeaders).build());

            assertThat(expectMsgClass(Object.class)).isEqualTo(
                    ThingNotCreatableException.newBuilderForPolicyExisting(thingId, thingId)
                            .dittoHeaders(defaultHeaders)
                            .build());
        }};
    }

    @Test
    public void createThingWithInlinedPolicyWithEnforcer() {
        new TestKit(actorSystem) {{
            startHandlerWithEnforcer(this);

            // receives ModifyThing with inlined policy, which is invalid because it has policy ID
            final String thingId = "thing:" + UUID.randomUUID();
            final String policyId = "policy:" + UUID.randomUUID();
            final Policy policy = Policy.newBuilder(policyId).build();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, policy.toJson(), defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // rejects the request because the existence of enforcer means the thing exists already
            assertThat(expectMsgClass(Object.class)).isEqualTo(PolicyNotAllowedException.newBuilder(thingId).build());
        }};
    }

    @Test
    public void createThingWithImplicitPolicyHappyPath() {
        new TestKit(actorSystem) {{
            startHandlerWithoutEnforcer(this);

            // receives ModifyThing with inlined policy, which is invalid because it has policy ID
            final String thingId = "thing:" + UUID.randomUUID();
            final Policy policy = Policy.newBuilder(thingId)
                    .forLabel("DEFAULT")
                    .setSubject(defaultSubject)
                    .setGrantedPermissions("policy", "/", "READ", "WRITE")
                    .setGrantedPermissions("thing", "/", "READ", "WRITE")
                    .setGrantedPermissions("message", "/", "READ", "WRITE")
                    .build();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // creates inlined policy
            assertThat(policyEnforcerShard.expectMsgClass(Object.class))
                    .isEqualTo(createShardedMessage(thingId, CreatePolicy.of(policy, defaultHeaders)));
            policyEnforcerShard.reply(CreatePolicyResponse.of(thingId, policy, defaultHeaders));

            // creates thing
            final Thing thingWithPolicyIdWithoutPolicy = thing.setPolicyId(thingId);
            assertThat(policyEnforcerShard.expectMsgClass(Object.class)).isEqualTo(
                    createShardedMessage(thingId,
                            CreateThing.of(thingWithPolicyIdWithoutPolicy, null, defaultHeaders)));
            policyEnforcerShard.reply(CreateThingResponse.of(thingWithPolicyIdWithoutPolicy, defaultHeaders));

            // replies CreateThingResponse with inlined policy
            assertThat(expectMsgClass(Object.class)).isEqualTo(
                    CreateThingResponse.of(thingWithPolicyIdWithoutPolicy, defaultHeaders));
        }};
    }

    @Test
    public void createThingWithImplicitPolicyThatExists() {
        new TestKit(actorSystem) {{
            startHandlerWithoutEnforcer(this);

            // receives ModifyThing with inlined policy, which is invalid because it has policy ID
            final String thingId = "thing:" + UUID.randomUUID();
            final Policy policy = Policy.newBuilder(thingId)
                    .forLabel("DEFAULT")
                    .setSubject(defaultSubject)
                    .setGrantedPermissions("policy", "/", "READ", "WRITE")
                    .setGrantedPermissions("thing", "/", "READ", "WRITE")
                    .setGrantedPermissions("message", "/", "READ", "WRITE")
                    .build();
            final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).build();
            final ModifyThing modifyThing = ModifyThing.of(thingId, thing, null, defaultHeaders);
            underTest.tell(modifyThing, getRef());

            // creates inlined policy failed because a policy existed with the same ID
            assertThat(policyEnforcerShard.expectMsgClass(Object.class))
                    .isEqualTo(createShardedMessage(thingId, CreatePolicy.of(policy, defaultHeaders)));
            policyEnforcerShard.reply(
                    PolicyConflictException.newBuilder(thingId).dittoHeaders(defaultHeaders).build());

            // replies with ThingNotCreatableException
            assertThat(expectMsgClass(Object.class)).isEqualTo(
                    ThingNotCreatableException.newBuilderForPolicyExisting(thingId, thingId)
                            .dittoHeaders(defaultHeaders)
                            .build());
        }};
    }
}
