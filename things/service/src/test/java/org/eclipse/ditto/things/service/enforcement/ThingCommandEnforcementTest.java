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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.base.model.json.JsonSchemaVersion.V_2;
import static org.eclipse.ditto.policies.model.SubjectIssuer.GOOGLE;
import static org.eclipse.ditto.things.service.enforcement.TestSetup.THING_ID;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.LiveChannelConditionNotAllowedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotCreatableException;
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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import akka.actor.ActorRef;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.Duration;

/**
 * Tests {@link org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor} and its
 * {@link org.eclipse.ditto.things.service.enforcement.ThingEnforcement} for "twin" related commands enforced by
 * {@link org.eclipse.ditto.things.service.enforcement.ThingCommandEnforcement}.
 */
@SuppressWarnings({"squid:S3599", "squid:S1171"})
public final class ThingCommandEnforcementTest extends AbstractThingEnforcementTest {

    @Rule
    public final TestName testName = new TestName();

    @Test
    public void rejectByPolicy() {
        final PolicyId policyId = PolicyId.of("empty:policy");
        final JsonObject thingWithEmptyPolicy = TestSetup.newThingWithPolicyId(policyId);
        final JsonObject emptyPolicy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .build()
                .toJson(FieldType.all());
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithEmptyPolicy, DittoHeaders.empty());
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, emptyPolicy, DittoHeaders.empty());

        new TestKit(system) {{
            supervisor.tell(getReadCommand(), getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            TestSetup.fishForMsgClass(this, ThingNotAccessibleException.class);

            supervisor.tell(getModifyCommand(), getRef());
            expectMsgClass(FeatureNotModifiableException.class);
        }};
    }

    @Test
    public void rejectQueryByThingNotAccessibleException() {
        final DittoRuntimeException error = ThingNotAccessibleException.newBuilder(THING_ID).build();

        new TestKit(system) {{
            expectAndAnswerSudoRetrieveThing(error);

            supervisor.tell(getReadCommand(), getRef());
            fishForMessage(Duration.create(500, TimeUnit.MILLISECONDS), "error", msg -> msg.equals(error));
        }};
    }

    @Test
    public void rejectUpdateByThingNotAccessibleException() {
        final DittoRuntimeException error = ThingNotAccessibleException.newBuilder(THING_ID).build();
        new TestKit(system) {{
            expectAndAnswerSudoRetrieveThing(error);

            supervisor.tell(getModifyCommand(), getRef());
            expectMsgClass(ThingNotModifiableException.class);
        }};
    }

    @Test
    public void rejectQueryByPolicyNotAccessibleException() {
        final PolicyId policyId = PolicyId.of("not:accessible");
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(TestSetup.newThingWithPolicyId(policyId), DittoHeaders.empty());

        new TestKit(system) {{
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, PolicyNotAccessibleException.newBuilder(policyId).build());

            supervisor.tell(getReadCommand(), getRef());
            final DittoRuntimeException error = TestSetup.fishForMsgClass(this, ThingNotAccessibleException.class);
            assertThat(error.getMessage()).contains(THING_ID);
        }};
    }

    @Test
    public void rejectUpdateByPolicyNotAccessibleException() {
        final PolicyId policyId = PolicyId.of("not:accessible");
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(TestSetup.newThingWithPolicyId(policyId), DittoHeaders.empty());

        new TestKit(system) {{
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, PolicyNotAccessibleException.newBuilder(policyId).build());

            supervisor.tell(getModifyCommand(), getRef());
            final DittoRuntimeException error = TestSetup.fishForMsgClass(this, ThingNotModifiableException.class);
            assertThat(error.getMessage()).contains(THING_ID);
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
            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(THING_ID).build());

            final CreateThing createThing = CreateThing.of(thing, policy.toJson(), headers());
            supervisor.tell(createThing, getRef());
            TestSetup.fishForMsgClass(this, ThingNotModifiableException.class);
        }};

    }

    @Test
    public void acceptByPolicy() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = TestSetup.newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build()
                .toJson(FieldType.all());
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithPolicy, DittoHeaders.empty());
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());

        new TestKit(system) {{
            final ThingCommand<?> write = getModifyCommand();
            supervisor.tell(write, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            final ThingCommand<?> expectedWriteCommand = addReadSubjectHeader(write,
                    SubjectId.newInstance(GOOGLE, TestSetup.SUBJECT_ID));
            thingPersistenceActorProbe.expectMsg(expectedWriteCommand);

            final ThingCommand<?> read = getReadCommand();
            final RetrieveThingResponse retrieveThingResponse =
                    RetrieveThingResponse.of(THING_ID, JsonFactory.newObject(), DittoHeaders.empty());
            supervisor.tell(read, getRef());

            final ThingCommand<?> expectedReadCommand = addReadSubjectHeader(read,
                    SubjectId.newInstance(GOOGLE, TestSetup.SUBJECT_ID));
            thingPersistenceActorProbe.expectMsg(expectedReadCommand);
            thingPersistenceActorProbe.reply(retrieveThingResponse);
            expectMsg(retrieveThingResponse);
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
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
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
            final ThingCommand<?> modifyCommand = getModifyCommand();
            supervisor.tell(modifyCommand, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            final ThingCommand<?> expectedModifyCommand = addReadSubjectHeader(modifyCommand,
                    SubjectId.newInstance(GOOGLE, TestSetup.SUBJECT_ID));
            thingPersistenceActorProbe.expectMsg(expectedModifyCommand);

            final RetrieveThingResponse retrieveThingResponseWithAttr =
                    RetrieveThingResponse.of(THING_ID, thingWithPolicy, headers());

            supervisor.tell(getReadCommand(), getRef());
            thingPersistenceActorProbe.reply(retrieveThingResponseWithAttr);

            final JsonObject jsonObjectWithoutAttr = thingWithPolicy.remove(attributePointer);
            final RetrieveThingResponse retrieveThingResponseWithoutAttr =
                    RetrieveThingResponse.of(THING_ID, jsonObjectWithoutAttr, headers());
            expectMsg(retrieveThingResponseWithoutAttr);
        }};
    }

    @Test
    public void enforceConditionAndLiveChannelCondition() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thing = newThingWithAttributeWithPolicyId(policyId).toBuilder()
                .set(JsonPointer.of("/features/revokedFeature/properties/right"), "revoked")
                .set(JsonPointer.of("/features/grantedFeature/properties/right"), "granted")
                .build();
        final JsonPointer revokedFeaturePointer = JsonPointer.of("/features/revokedFeature");

        // GIVEN: policy revoke READ on 1 feature
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setRevokedPermissions(PoliciesResourceType.thingResource(revokedFeaturePointer),
                        Permissions.newInstance(Permission.READ))
                .build()
                .toJson(FieldType.all());
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thing, DittoHeaders.empty());
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());

        new TestKit(system) {{

            // WHEN: Condition is set on a readable feature
            final var conditionalRetrieveThing1 =
                    getRetrieveThing(builder -> builder.condition("exists(features/grantedFeature)"));
            supervisor.tell(conditionalRetrieveThing1, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            final ThingCommand<?> expectedReadCommand = addReadSubjectHeader(conditionalRetrieveThing1);
            thingPersistenceActorProbe.expectMsg(expectedReadCommand);
            final RetrieveThingResponse retrieveThingResponse = RetrieveThingResponse.of(THING_ID, thing, headers());
            thingPersistenceActorProbe.reply(retrieveThingResponse);

            // THEN: The command is authorized and response is forwarded
            final var response1 = expectMsgClass(RetrieveThingResponse.class);
            Assertions.assertThat(response1.getThing().toJson().get(revokedFeaturePointer))
                    .describedAs("Revoked feature should be filtered out: " + response1.getThing())
                    .isEmpty();

            // WHEN: Condition is set on an unreadable feature
            final var conditionalRetrieveThing2 =
                    getRetrieveThing(builder -> builder.condition("exists(features/revokedFeature)"));
            supervisor.tell(conditionalRetrieveThing2, getRef());

            // THEN: The command is rejected
            expectMsgClass(ThingConditionFailedException.class);

            // WHEN: Live channel condition is set on an unreadable feature
            final var conditionalRetrieveThing3 = getRetrieveThing(builder ->
                    builder.liveChannelCondition("exists(features/revokedFeature)").channel("live"));
            supervisor.tell(conditionalRetrieveThing3, getRef());

            // THEN: The command is rejected
            expectMsgClass(ThingConditionFailedException.class);
        }};
    }

    @Test
    public void enforceLiveChannelConditionOnModifyCommandFails() {
        final PolicyId policyId = PolicyId.of("policy:id");

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final JsonObject policyJsonObject = policy.toJson(FieldType.all());

        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(newThingWithAttributeWithPolicyId(policyId), DittoHeaders.empty());
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policyJsonObject, DittoHeaders.empty());

        new TestKit(system) {{
            // WHEN: Live channel condition is set on an unreadable feature
            final DittoHeaders dittoHeadersWithLiveChannelCondition = DittoHeaders.newBuilder(headers())
                    .liveChannelCondition("exists(thingId)")
                    .build();

            final ThingCommand<?> modifyCommand = getModifyCommand(dittoHeadersWithLiveChannelCondition);
            supervisor.tell(modifyCommand, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            // THEN: The command is rejected
            thingPersistenceActorProbe.expectNoMessage();
            final LiveChannelConditionNotAllowedException response =
                    TestSetup.fishForMsgClass(this, LiveChannelConditionNotAllowedException.class);

            Assertions.assertThat(WithDittoHeaders.getCorrelationId(response))
                    .isEqualTo(WithDittoHeaders.getCorrelationId(modifyCommand));
        }};
    }

    @Test
    public void acceptCreateByInlinePolicy() {
        final PolicyId policyId = PolicyId.of(THING_ID);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .setRevision(1)
                .build();
        final Thing thing = newThing().build();

        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy.toJson(FieldType.all()), DittoHeaders.empty());

        new TestKit(system) {{
            final CreateThing createThing = CreateThing.of(thing, policy.toJson(), headers());
            supervisor.tell(createThing, getRef());

            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(THING_ID).build());

            policiesShardRegionProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionProbe.reply(CreatePolicyResponse.of(policyId, policy, headers()));

            final CreateThing expectedCreateThing = addReadSubjectHeader(
                    CreateThing.of(thing.setPolicyId(policyId), null, headers()));
            thingPersistenceActorProbe.expectMsg(expectedCreateThing);
            final CreateThingResponse createThingResponse = CreateThingResponse.of(thing, headers());
            thingPersistenceActorProbe.reply(createThingResponse);

            final CreateThingResponse expectedCreateThingResponse = expectMsgClass(CreateThingResponse.class);
            assertThat(expectedCreateThingResponse.getThingCreated().orElse(null)).isEqualTo(thing);
        }};
    }

    @Test
    public void acceptCreateByImplicitPolicy() {
        final Thing thing = newThing().build();
        final PolicyId policyId = PolicyId.of(THING_ID);
        final Policy policy = provideDefaultImplicitPolicy(policyId);

        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy.toJson(FieldType.all()), DittoHeaders.empty());

        new TestKit(system) {{
            final CreateThing createThing = CreateThing.of(thing, null, headers());
            // first Thing command triggers cache load
            supervisor.tell(createThing, getRef());

            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(THING_ID).build());

            policiesShardRegionProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionProbe.reply(CreatePolicyResponse.of(policyId, policy, headers()));

            final CreateThing expectedCreateThing = addReadSubjectHeader(
                    CreateThing.of(thing.setPolicyId(policyId), null, headers()), TestSetup.SUBJECT);
            thingPersistenceActorProbe.expectMsg(expectedCreateThing);
            final CreateThingResponse createThingResponse = CreateThingResponse.of(thing, headers());
            thingPersistenceActorProbe.reply(createThingResponse);

            // cache should be invalidated before response is sent
            expectMsg(createThingResponse);
        }};
    }

    @Test
    @Ignore("TODO: CR-11387 pre-enforcement can complete faster for some messages, thus f-ing up the message order")
    public void testParallelEnforcementTaskScheduling() {
        final Thing thing = newThing().build();
        final PolicyId policyId = PolicyId.of(THING_ID);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final CreatePolicyResponse createPolicyResponse = CreatePolicyResponse.of(policyId, policy, headers());
        final ThingNotAccessibleException thingNotAccessibleException =
                ThingNotAccessibleException.newBuilder(THING_ID).build();
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(
                        JsonObject.newBuilder()
                                .set(Thing.JsonFields.ID, THING_ID.toString())
                                .set(Thing.JsonFields.POLICY_ID, policyId.toString())
                                .set(Thing.JsonFields.REVISION, 1L)
                                .build(),
                        headers());
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy.toJson(FieldType.all()), headers());
        final CreateThing createThing = CreateThing.of(thing, null, headers());
        final CreateThingResponse createThingResponse = CreateThingResponse.of(thing, headers());
        final RetrieveThing retrieveThing = RetrieveThing.of(THING_ID, headers());
        final RetrieveThingResponse retrieveThingResponse =
                RetrieveThingResponse.of(THING_ID, JsonObject.empty(), headers());
        final ModifyPolicyId modifyPolicyId = ModifyPolicyId.of(THING_ID, policyId, headers());
        final ModifyPolicyIdResponse modifyPolicyIdResponse =
                ModifyPolicyIdResponse.modified(THING_ID, headers());
        final ModifyAttribute modifyAttribute =
                ModifyAttribute.of(THING_ID, JsonPointer.of("x"), JsonValue.of(5), headers());
        final ModifyAttributeResponse modifyAttributeResponse =
                ModifyAttributeResponse.created(THING_ID, JsonPointer.of("x"), JsonValue.of(5), headers());
        final RetrieveAttribute retrieveAttribute =
                RetrieveAttribute.of(THING_ID, JsonPointer.of("x"), headers());
        final RetrieveAttributeResponse retrieveAttributeResponse =
                RetrieveAttributeResponse.of(THING_ID, JsonPointer.of("x"), JsonValue.of(5), headers());

        new TestKit(system) {{
            // GIVEN: all commands are sent in one batch
            supervisor.tell(createThing, getRef());
            supervisor.tell(retrieveThing, getRef());
            supervisor.tell(modifyPolicyId, getRef());
            supervisor.tell(modifyAttribute, getRef());
            supervisor.tell(retrieveAttribute, getRef());

            // THEN: expect enforcement start: CreateThing
            expectAndAnswerSudoRetrieveThing(thingNotAccessibleException);
            policiesShardRegionProbe.expectMsgClass(CreatePolicy.class);

            // THEN: no other message should come through before enforcement of CreateThing completes
            thingPersistenceActorProbe.expectNoMessage(Duration.create(150, TimeUnit.MILLISECONDS));

            // WHEN: CreateThing completes
            policiesShardRegionProbe.reply(createPolicyResponse);
            thingPersistenceActorProbe.expectMsgClass(CreateThing.class);
            thingPersistenceActorProbe.reply(createThingResponse);

            // THEN: the next commands are dispatched until authorization change happens again
            thingPersistenceActorProbe.expectMsgClass(RetrieveThing.class);
            final ActorRef retrieveThingSender = thingPersistenceActorProbe.lastSender();
            thingPersistenceActorProbe.expectMsgClass(ModifyPolicyId.class);
            final ActorRef modifyPolicyIdSender = thingPersistenceActorProbe.lastSender();

            // THEN: the authorization-changing command causes a cache reload
            thingPersistenceActorProbe.expectMsgClass(SudoRetrieveThing.class);

            // WHEN: cache reload completes
            thingPersistenceActorProbe.reply(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            modifyPolicyIdSender.tell(modifyPolicyIdResponse, ActorRef.noSender());

            // THEN: the other queued commands are dispatched
            thingPersistenceActorProbe.expectMsgClass(ModifyAttribute.class);
            final ActorRef modifyAttributeSender = thingPersistenceActorProbe.lastSender();
            thingPersistenceActorProbe.expectMsgClass(RetrieveAttribute.class);
            final ActorRef retrieveAttributeSender = thingPersistenceActorProbe.lastSender();

            // THEN: responses are delivered to the command sender
            modifyAttributeSender.tell(modifyAttributeResponse, ActorRef.noSender());

            // and THEN: order of command responses is preserved
            expectMsgClass(CreateThingResponse.class);
            expectMsgClass(ModifyPolicyIdResponse.class);
            expectMsgClass(ModifyAttributeResponse.class);

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
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .setRevision(1)
                .build();
        final Thing thing = newThing().build();

        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy.toJson(FieldType.all()), DittoHeaders.empty());

        new TestKit(system) {{
            final CreateThing createThing = CreateThing.of(thing, policy.toJson(), headers());
            supervisor.tell(createThing, getRef());

            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(THING_ID).build());

            policiesShardRegionProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionProbe.reply(CreatePolicyResponse.of(PolicyId.of(THING_ID), policy, headers()));

            final CreateThing expectedCreateThing = addReadSubjectHeader(
                    CreateThing.of(thing.setPolicyId(policyId), null, headers()));
            thingPersistenceActorProbe.expectMsg(expectedCreateThing);
            final CreateThingResponse createThingResponse = CreateThingResponse.of(thing, headers());
            thingPersistenceActorProbe.reply(createThingResponse);

            final CreateThingResponse expectedCreateThingResponse = expectMsgClass(CreateThingResponse.class);
            assertThat(expectedCreateThingResponse.getThingCreated().orElse(null)).isEqualTo(thing);
        }};
    }

    @Test
    public void testCreateByCopyFromPolicyWithAllowPolicyLockout() {
        testCreateByCopyFromPolicy(headers().toBuilder().allowPolicyLockout(true).build(), false,
                CreateThingResponse.class);
    }

    @Test
    public void testCreateByCopyFromPolicyWithPolicyLockout() {
        testCreateByCopyFromPolicy(headers(), true, ThingNotCreatableException.class);
    }

    @Test
    public void testCreateByCopyFromPolicyWithIfNoneMatch() {
        // the "if-none-match" headers must be removed when retrieving the policy to copy
        // if they are not removed, a PolicyPreconditionNotModifiedException would be returned
        testCreateByCopyFromPolicy(headers().toBuilder()
                        .allowPolicyLockout(true)
                        .ifNoneMatch(EntityTagMatchers.fromList(Collections.singletonList(EntityTagMatcher.asterisk())))
                        .build(),
                false, CreateThingResponse.class);
    }

    private void testCreateByCopyFromPolicy(final DittoHeaders dittoHeaders, final boolean lockoutExpected,
            final Class<?> expectedResponseClass) {
        final PolicyId policyId = PolicyId.of("policy:id");
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                // authorized subject has WRITE on thing:/ resource
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .forLabel("admin")
                .setSubject(GOOGLE, "admin")
                // some other subject has WRITE on policy:/ resource
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .setRevision(1)
                .build();
        final Thing thing = newThing().build();

        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy.toJson(FieldType.all()), DittoHeaders.empty());

        new TestKit(system) {{
            final CreateThing createThing = CreateThing.of(thing, null, policyId.toString(), dittoHeaders);
            supervisor.tell(createThing, getRef());

            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(THING_ID).build());

            // retrieve looks up the policy to copy:
            final RetrievePolicy retrievePolicy = policiesShardRegionProbe.expectMsgClass(RetrievePolicy.class);
            assertThat(retrievePolicy.getDittoHeaders().getIfMatch()).isEmpty();
            assertThat(retrievePolicy.getDittoHeaders().getIfNoneMatch()).isEmpty();
            policiesShardRegionProbe.reply(RetrievePolicyResponse.of(policyId, policy,
                    retrievePolicy.getDittoHeaders()));

            final PolicyId newPolicyId = PolicyId.of(THING_ID);

            if (lockoutExpected) {
                // this logic is done in PolicyCommandEnforcement which is in policies-service - simulate it here:
                policiesShardRegionProbe.expectMsgClass(CreatePolicy.class);
                policiesShardRegionProbe.reply(PolicyNotAccessibleException.newBuilder(newPolicyId)
                        .dittoHeaders(dittoHeaders)
                        .build());
            } else {
                // after that, a copy of the policy is created:
                policiesShardRegionProbe.expectMsgClass(CreatePolicy.class);
                policiesShardRegionProbe.reply(CreatePolicyResponse.of(newPolicyId, policy, headers()));
            }

            if (lockoutExpected) {
                expectMsgClass(expectedResponseClass);
            } else {
                final Thing thingWithNewPolicyId = thing.setPolicyId(newPolicyId);
                final CreateThing expectedCreateThing = addReadSubjectHeader(
                        CreateThing.of(thingWithNewPolicyId, null, dittoHeaders));
                thingPersistenceActorProbe.expectMsg(expectedCreateThing);

                final CreateThingResponse createThingResponse = CreateThingResponse.of(thingWithNewPolicyId, headers());
                thingPersistenceActorProbe.reply(createThingResponse);

                expectMsg(createThingResponse);
            }
        }};
    }

    @Test
    public void rejectCreateByInlinePolicyWithInvalidId() {
        final PolicyId policyId = PolicyId.of(THING_ID);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .build();
        final JsonObject invalidPolicyJson = policy.toJson()
                .setValue("policyId", "invalid-policy-id");
        final Thing thing = newThing().build();

        new TestKit(system) {{
            final CreateThing createThing = CreateThing.of(thing, invalidPolicyJson, headers());
            supervisor.tell(createThing, getRef());

            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(THING_ID).build());

            policiesShardRegionProbe.expectNoMessage();
            thingPersistenceActorProbe.expectNoMessage();

            expectMsgClass(PolicyIdInvalidException.class);
        }};
    }

    @Test
    public void rejectCreateByInvalidPolicy() {
        final PolicyId policyId = PolicyId.of(THING_ID);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
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
            final CreateThing createThing = CreateThing.of(thing, invalidPolicyJson, headers());
            supervisor.tell(createThing, getRef());

            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(THING_ID).build());

            policiesShardRegionProbe.expectNoMessage();
            thingPersistenceActorProbe.expectNoMessage();

            expectMsgClass(PolicyInvalidException.class);
        }};
    }

    @Test
    public void transformModifyThingToCreateThing() {
        final Thing thingInPayload = newThing().setId(THING_ID).build();
        final Policy policy = provideDefaultImplicitPolicy(THING_ID);

        new TestKit(system) {{
            final ModifyThing modifyThing = ModifyThing.of(THING_ID, thingInPayload, null, headers());
            supervisor.tell(modifyThing, getRef());

            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(THING_ID).build());

            policiesShardRegionProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionProbe.reply(CreatePolicyResponse.of(PolicyId.of(THING_ID), policy, headers()));

            thingPersistenceActorProbe.expectMsgClass(CreateThing.class);
        }};
    }

    @Test
    public void rejectModifyWithConditionAndNoReadPermissionForAttributes() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = newThingWithAttributeWithPolicyId(policyId);
        final JsonPointer attributePointer = JsonPointer.of("/attributes/testAttr");
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
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
            final DittoHeaders dittoHeaders = headers().toBuilder()
                    .condition("eq(attributes/testAttr,\"testString\")")
                    .build();
            final ThingCommand<?> modifyCommand = getModifyCommand(dittoHeaders);
            supervisor.tell(modifyCommand, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            thingPersistenceActorProbe.expectNoMessage();

            expectMsgClass(ThingConditionFailedException.class);
        }};
    }

    @Test
    public void testModifyWithInvalidCondition() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = TestSetup.newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build()
                .toJson(FieldType.all());
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithPolicy, DittoHeaders.empty());
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());

        new TestKit(system) {{
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            final DittoHeaders dittoHeaders = headers().toBuilder()
                    .condition("eq(attributes//testAttr,\"testString\")")
                    .build();

            final ThingCommand<?> modifyCommand = getModifyCommand(dittoHeaders);
            supervisor.tell(modifyCommand, getRef());

            thingPersistenceActorProbe.expectNoMessage();

            final ThingConditionInvalidException thingConditionInvalidException =
                    expectMsgClass(ThingConditionInvalidException.class);
            assertThat(thingConditionInvalidException.getErrorCode()).isEqualTo(
                    ThingConditionInvalidException.ERROR_CODE);
            assertThat(thingConditionInvalidException.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        }};
    }

    @SuppressWarnings("unchecked")
    private <C extends ThingCommand<?>> C addReadSubjectHeader(final C command, final SubjectId... subjectIds) {
        return (C) command.setDittoHeaders(command.getDittoHeaders().toBuilder()
                .readGrantedSubjects(Stream.of(subjectIds)
                        .map(AuthorizationSubject::newInstance)
                        .toList()
                )
                .build()
        );
    }

    private static Policy provideDefaultImplicitPolicy(final CharSequence policyId) {
        return PoliciesModelFactory.newPolicyBuilder(PolicyId.of(policyId))
                .forLabel("DEFAULT")
                .setSubject(Subject.newInstance(SubjectId.newInstance(TestSetup.SUBJECT.getId())))
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE, Permission.READ))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE, Permission.READ))
                .setGrantedPermissions(PoliciesResourceType.messageResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE, Permission.READ))
                .setRevision(1)
                .build();
    }

    private DittoHeaders headers() {
        return DittoHeaders.newBuilder()
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        TestSetup.SUBJECT,
                        AuthorizationSubject.newInstance(String.format("%s:%s", GOOGLE, TestSetup.SUBJECT_ID))))
                .putHeader(DittoHeaderDefinition.ORIGINATOR.getKey(), TestSetup.SUBJECT.getId())
                .correlationId(testName.getMethodName())
                .schemaVersion(JsonSchemaVersion.V_2)
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

    private ThingCommand<?> getReadCommand() {
        return RetrieveThing.of(THING_ID, headers());
    }

    private ThingCommand<?> getModifyCommand() {
        return getModifyCommand(headers());
    }

    private static ThingCommand<?> getModifyCommand(final DittoHeaders dittoHeaders) {
        return ModifyFeature.of(THING_ID, Feature.newBuilder().withId("x").build(), dittoHeaders);
    }

    private RetrieveThing getRetrieveThing(final Consumer<DittoHeadersBuilder<?, ?>> headerModifier) {
        final DittoHeadersBuilder<?, ?> builder = headers().toBuilder();
        headerModifier.accept(builder);
        return RetrieveThing.of(THING_ID, builder.build());
    }

}
