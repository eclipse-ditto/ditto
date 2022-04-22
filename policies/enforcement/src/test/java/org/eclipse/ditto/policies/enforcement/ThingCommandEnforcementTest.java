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
package org.eclipse.ditto.policies.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.base.model.json.JsonSchemaVersion.V_2;
import static org.eclipse.ditto.policies.enforcement.TestSetup.newThingWithPolicyId;
import static org.eclipse.ditto.policies.model.SubjectIssuer.GOOGLE;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.internal.models.signal.SignalInformationPoint;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
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

/**
 * Tests {@link org.eclipse.ditto.concierge.api.enforcement.ThingCommandEnforcement} in context of an {@link org.eclipse.ditto.concierge.api.enforcement.EnforcerActor}.
 */
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
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(TestSetup.POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(getReadCommand(), getRef());
            TestSetup.fishForMsgClass(this, ThingNotAccessibleException.class);

            underTest.tell(getModifyCommand(), getRef());
            expectMsgClass(FeatureNotModifiableException.class);
        }};
    }

    @Test
    public void rejectQueryByThingNotAccessibleException() {
        final DittoRuntimeException error = ThingNotAccessibleException.newBuilder(TestSetup.THING_ID).build();

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO, error);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(getReadCommand(), getRef());
            fishForMessage(Duration.create(500, TimeUnit.MILLISECONDS), "error", msg -> msg.equals(error));
        }};
    }

    @Test
    public void rejectUpdateByThingNotAccessibleException() {
        final DittoRuntimeException error = ThingNotAccessibleException.newBuilder(TestSetup.THING_ID).build();
        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO, error);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(getModifyCommand(), getRef());
            TestSetup.fishForMsgClass(this, ThingNotAccessibleException.class);
        }};
    }

    @Test
    public void rejectQueryByPolicyNotAccessibleException() {
        final PolicyId policyId = PolicyId.of("not:accessible");
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(newThingWithPolicyId(policyId), DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(TestSetup.POLICY_SUDO,
                    PolicyNotAccessibleException.newBuilder(policyId).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(getReadCommand(), getRef());
            final DittoRuntimeException error = TestSetup.fishForMsgClass(this, ThingNotAccessibleException.class);
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
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(TestSetup.POLICY_SUDO,
                    PolicyNotAccessibleException.newBuilder(policyId).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(getModifyCommand(), getRef());
            final DittoRuntimeException error = TestSetup.fishForMsgClass(this, ThingNotModifiableException.class);
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
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO,
                    ThingNotAccessibleException.newBuilder(TestSetup.THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, policy.toJson(), headers());
            underTest.tell(createThing, getRef());
            TestSetup.fishForMsgClass(this, ThingNotModifiableException.class);
        }};

    }

    @Test
    public void acceptByPolicy() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
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
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(TestSetup.POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());

            final ThingCommand<?> write = getModifyCommand();
            mockEntitiesActorInstance.setReply(write);
            underTest.tell(write, getRef());
            assertThat((CharSequence) TestSetup.fishForMsgClass(this, write.getClass()).getEntityId())
                    .isEqualTo(write.getEntityId());

            final ThingCommand<?> read = getReadCommand();
            final RetrieveThingResponse retrieveThingResponse =
                    RetrieveThingResponse.of(TestSetup.THING_ID, JsonFactory.newObject(), DittoHeaders.empty());
            mockEntitiesActorInstance.setReply(retrieveThingResponse);
            underTest.tell(read, getRef());
            assertThat((CharSequence) expectMsgClass(retrieveThingResponse.getClass()).getEntityId())
                    .isEqualTo(read.getEntityId());
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
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(TestSetup.POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());

            final ThingCommand<?> modifyCommand = getModifyCommand();
            mockEntitiesActorInstance.setReply(modifyCommand);
            underTest.tell(modifyCommand, getRef());
            assertThat((CharSequence) TestSetup.fishForMsgClass(this, modifyCommand.getClass()).getEntityId())
                    .isEqualTo(modifyCommand.getEntityId());

            final RetrieveThingResponse retrieveThingResponseWithAttr =
                    RetrieveThingResponse.of(TestSetup.THING_ID, thingWithPolicy, headers());

            final JsonObject jsonObjectWithoutAttr = thingWithPolicy.remove(attributePointer);
            final RetrieveThingResponse retrieveThingResponseWithoutAttr =
                    RetrieveThingResponse.of(TestSetup.THING_ID, jsonObjectWithoutAttr, headers());

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
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(TestSetup.POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());

            // WHEN: Condition is set on a readable feature
            final var conditionalRetrieveThing1 =
                    getRetrieveThing(builder -> builder.condition("exists(features/grantedFeature)"));
            final RetrieveThingResponse retrieveThingResponse =
                    RetrieveThingResponse.of(TestSetup.THING_ID, thing, headers());
            mockEntitiesActorInstance.setReply(retrieveThingResponse);
            underTest.tell(conditionalRetrieveThing1, getRef());

            // THEN: The command is authorized and response is forwarded
            final var response1 = TestSetup.fishForMsgClass(this, RetrieveThingResponse.class);
            Assertions.assertThat(response1.getThing().toJson().get(revokedFeaturePointer))
                    .describedAs("Revoked feature should be filtered out: " + response1.getThing())
                    .isEmpty();

            // WHEN: Condition is set on an unreadable feature
            final var conditionalRetrieveThing2 =
                    getRetrieveThing(builder -> builder.condition("exists(features/revokedFeature)"));
            underTest.tell(conditionalRetrieveThing2, getRef());

            // THEN: The command is rejected
            expectMsgClass(ThingConditionFailedException.class);

            // WHEN: Live channel condition is set on an unreadable feature
            final var conditionalRetrieveThing3 = getRetrieveThing(builder ->
                    builder.liveChannelCondition("exists(features/revokedFeature)").channel("live"));
            underTest.tell(conditionalRetrieveThing3, getRef());

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
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(TestSetup.POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());

            // WHEN: Live channel condition is set on an unreadable feature
            final DittoHeaders dittoHeadersWithLiveChannelCondition = DittoHeaders.newBuilder(headers())
                    .liveChannelCondition("exists(thingId)")
                    .build();

            final ThingCommand<?> modifyCommand = getModifyCommand(dittoHeadersWithLiveChannelCondition);
            mockEntitiesActorInstance.setReply(modifyCommand);
            underTest.tell(modifyCommand, getRef());

            // THEN: The command is rejected
            final LiveChannelConditionNotAllowedException response =
                    TestSetup.fishForMsgClass(this, LiveChannelConditionNotAllowedException.class);

            Assertions.assertThat(SignalInformationPoint.getCorrelationId(response))
                    .isEqualTo(SignalInformationPoint.getCorrelationId(modifyCommand));
        }};
    }

    @Test
    public void acceptCreateByInlinePolicy() {
        final PolicyId policyId = PolicyId.of(TestSetup.THING_ID);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .build();
        final Thing thing = newThing().build();

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO,
                    ThingNotAccessibleException.newBuilder(TestSetup.THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, policy.toJson(), headers());
            mockEntitiesActorInstance.setReply(CreateThingResponse.of(thing, headers()))
                    .setReply(CreatePolicyResponse.of(policyId, policy, headers()));
            underTest.tell(createThing, getRef());
            final CreateThingResponse expectedCreateThingResponse =
                    TestSetup.fishForMsgClass(this, CreateThingResponse.class);
            assertThat(expectedCreateThingResponse.getThingCreated().orElse(null)).isEqualTo(thing);
        }};
    }

    @Test
    public void acceptCreateByImplicitPolicyAndInvalidateCache() {
        final Thing thing = newThing().build();
        final PolicyId policyId = PolicyId.of(TestSetup.THING_ID);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId).build();
        final AtomicInteger sudoRetrieveThingCounter = new AtomicInteger(0);
        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(CreateThingResponse.of(thing, headers()))
                    .setReply(CreatePolicyResponse.of(policyId, policy, headers()))
                    .setHandler(TestSetup.THING_SUDO, sudo -> {
                        sudoRetrieveThingCounter.getAndIncrement();
                        return ThingNotAccessibleException.newBuilder(TestSetup.THING_ID).build();
                    });

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, null, headers());

            // first Thing command triggers cache load
            underTest.tell(createThing, getRef());

            // cache should be invalidated before response is sent
            TestSetup.fishForMsgClass(this, CreateThingResponse.class);

            // second Thing command should trigger cache load again
            underTest.tell(createThing, getRef());
            TestSetup.fishForMsgClass(this, CreateThingResponse.class);

            // verify cache is loaded twice
            assertThat(sudoRetrieveThingCounter.get()).isEqualTo(2);
        }};
    }

    @Test
    public void testParallelEnforcementTaskScheduling() {
        final TestProbe pubSubProbe = TestProbe.apply("pubSubMediator", system);
        final TestProbe entitiesProbe = TestProbe.apply("entities", system);

        final Thing thing = newThing().build();
        final PolicyId policyId = PolicyId.of(TestSetup.THING_ID);
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
                ThingNotAccessibleException.newBuilder(TestSetup.THING_ID).build();
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(
                        JsonObject.newBuilder()
                                .set(Thing.JsonFields.ID, TestSetup.THING_ID.toString())
                                .set(Thing.JsonFields.POLICY_ID, policyId.toString())
                                .set(Thing.JsonFields.REVISION, 1L)
                                .build(),
                        headers());
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy.toJson(FieldType.all()), headers());
        final CreateThing createThing = CreateThing.of(thing, null, headers());
        final CreateThingResponse createThingResponse = CreateThingResponse.of(thing, headers());
        final RetrieveThing retrieveThing = RetrieveThing.of(TestSetup.THING_ID, headers());
        final RetrieveThingResponse retrieveThingResponse =
                RetrieveThingResponse.of(TestSetup.THING_ID, JsonObject.empty(), headers());
        final ModifyPolicyId modifyPolicyId = ModifyPolicyId.of(TestSetup.THING_ID, policyId, headers());
        final ModifyPolicyIdResponse modifyPolicyIdResponse =
                ModifyPolicyIdResponse.modified(TestSetup.THING_ID, headers());
        final ModifyAttribute modifyAttribute =
                ModifyAttribute.of(TestSetup.THING_ID, JsonPointer.of("x"), JsonValue.of(5), headers());
        final ModifyAttributeResponse modifyAttributeResponse =
                ModifyAttributeResponse.created(TestSetup.THING_ID, JsonPointer.of("x"), JsonValue.of(5), headers());
        final RetrieveAttribute retrieveAttribute =
                RetrieveAttribute.of(TestSetup.THING_ID, JsonPointer.of("x"), headers());
        final RetrieveAttributeResponse retrieveAttributeResponse =
                RetrieveAttributeResponse.of(TestSetup.THING_ID, JsonPointer.of("x"), JsonValue.of(5), headers());

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
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.WRITE))
                .build();
        final Thing thing = newThing().build();

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO,
                    ThingNotAccessibleException.newBuilder(TestSetup.THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, policy.toJson(), headers());
            mockEntitiesActorInstance.setReply(CreateThingResponse.of(thing, headers()))
                    .setReply(CreatePolicyResponse.of(PolicyId.of(TestSetup.THING_ID), policy, headers()));
            underTest.tell(createThing, getRef());
            final CreateThingResponse expectedCreateThingResponse =
                    TestSetup.fishForMsgClass(this, CreateThingResponse.class);
            assertThat(expectedCreateThingResponse.getThingCreated().orElse(null)).isEqualTo(thing);
        }};

    }

    @Test
    public void testCreateByCopyFromPolicyWithAllowPolicyLockout() {
        testCreateByCopyFromPolicy(headers().toBuilder().allowPolicyLockout(true).build(),
                CreateThingResponse.class);
    }

    @Test
    public void testCreateByCopyFromPolicyWithPolicyLockout() {
        testCreateByCopyFromPolicy(headers(), ThingNotModifiableException.class);
    }

    @Test
    public void testCreateByCopyFromPolicyWithIfNoneMatch() {
        // the "if-none-match" headers must be removed when retrieving the policy to copy
        // if they are not removed, a PolicyPreconditionNotModifiedException would be returned
        testCreateByCopyFromPolicy(headers().toBuilder()
                        .allowPolicyLockout(true)
                        .ifNoneMatch(EntityTagMatchers.fromList(Collections.singletonList(EntityTagMatcher.asterisk())))
                        .build(),
                CreateThingResponse.class);
    }

    private void testCreateByCopyFromPolicy(final DittoHeaders dittoHeaders, final Class<?> expectedResponseClass) {
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
                .build();
        final Thing thing = newThing().build();

        new TestKit(system) {{
            mockEntitiesActorInstance
                    .setReply(TestSetup.THING_SUDO, ThingNotAccessibleException.newBuilder(TestSetup.THING_ID).build())
                    .setReply(CreatePolicyResponse.of(policyId, policy, headers()));

            final TestProbe conciergeProbe = new TestProbe(system, TestSetup.createUniqueName());

            final ActorRef underTest = newEnforcerActor(getRef(), conciergeProbe.ref());
            final CreateThing createThing = CreateThing.of(thing, null, policyId.toString(), dittoHeaders);

            mockEntitiesActorInstance.setReply(CreateThingResponse.of(thing, headers()));

            underTest.tell(createThing, getRef());

            final RetrievePolicy retrievePolicy = conciergeProbe.expectMsgClass(RetrievePolicy.class);
            assertThat(retrievePolicy.getDittoHeaders().getIfMatch()).isEmpty();
            assertThat(retrievePolicy.getDittoHeaders().getIfNoneMatch()).isEmpty();
            conciergeProbe.lastSender().tell(RetrievePolicyResponse.of(policyId, policy,
                    retrievePolicy.getDittoHeaders()), ActorRef.noSender());

            TestSetup.fishForMsgClass(this, expectedResponseClass);
        }};
    }

    @Test
    public void rejectCreateByInlinePolicyWithInvalidId() {
        final PolicyId policyId = PolicyId.of(TestSetup.THING_ID);
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
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO,
                    ThingNotAccessibleException.newBuilder(TestSetup.THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, invalidPolicyJson, headers());
            mockEntitiesActorInstance.setReply(CreateThingResponse.of(thing, headers()))
                    .setReply(CreatePolicyResponse.of(policyId, policy, headers()));
            underTest.tell(createThing, getRef());

            // result may not be an instance of PolicyIdInvalidException but should have the same error code.
            final DittoRuntimeException response = TestSetup.fishForMsgClass(this, DittoRuntimeException.class);
            assertThat(response.getErrorCode()).isEqualTo(PolicyIdInvalidException.ERROR_CODE);
        }};
    }

    @Test
    public void rejectCreateByInvalidPolicy() {
        final PolicyId policyId = PolicyId.of(TestSetup.THING_ID);
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
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO,
                    ThingNotAccessibleException.newBuilder(TestSetup.THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, invalidPolicyJson, headers());
            mockEntitiesActorInstance.setReply(CreateThingResponse.of(thing, headers()))
                    .setReply(CreatePolicyResponse.of(policyId, policy, headers()));
            underTest.tell(createThing, getRef());

            // result may not be an instance of PolicyInvalidException but should have the same error code.
            final DittoRuntimeException response = TestSetup.fishForMsgClass(this, DittoRuntimeException.class);
            assertThat(response.getErrorCode()).isEqualTo(PolicyInvalidException.ERROR_CODE);
        }};

    }

    @Test
    public void transformModifyThingToCreateThing() {
        final Thing thingInPayload = newThing().setId(TestSetup.THING_ID).build();

        new TestKit(system) {{
            final ActorRef underTest = TestSetup.newEnforcerActor(system, getRef(), getRef());
            final ModifyThing modifyThing = ModifyThing.of(TestSetup.THING_ID, thingInPayload, null, headers());
            underTest.tell(modifyThing, getRef());

            TestSetup.fishForMsgClass(this, SudoRetrieveThing.class);
            reply(ThingNotAccessibleException.newBuilder(TestSetup.THING_ID).build());

            final CreatePolicy createPolicy = TestSetup.fishForMsgClass(this, CreatePolicy.class);
            reply(CreatePolicyResponse.of(createPolicy.getEntityId(), createPolicy.getPolicy(),
                    createPolicy.getDittoHeaders()));

            TestSetup.fishForMsgClass(this, CreateThing.class);
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
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(TestSetup.POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());

            final DittoHeaders dittoHeaders = headers().toBuilder()
                    .condition("eq(attributes/testAttr,\"testString\")")
                    .build();

            final ThingCommand<?> modifyCommand = getModifyCommand(dittoHeaders);
            mockEntitiesActorInstance.setReply(modifyCommand);
            underTest.tell(modifyCommand, getRef());
            final DittoRuntimeException response = TestSetup.fishForMsgClass(this, DittoRuntimeException.class);
            assertThat(response.getErrorCode()).isEqualTo(ThingConditionFailedException.ERROR_CODE);
            assertThat(response.getHttpStatus()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        }};
    }

    @Test
    public void testModifyWithInvalidCondition() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
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
            mockEntitiesActorInstance.setReply(TestSetup.THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(TestSetup.POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());

            final DittoHeaders dittoHeaders = headers().toBuilder()
                    .condition("eq(attributes//testAttr,\"testString\")")
                    .build();

            final ThingCommand<?> modifyCommand = getModifyCommand(dittoHeaders);
            mockEntitiesActorInstance.setReply(modifyCommand);
            underTest.tell(modifyCommand, getRef());
            final DittoRuntimeException response = TestSetup.fishForMsgClass(this, DittoRuntimeException.class);
            assertThat(response.getErrorCode()).isEqualTo(ThingConditionInvalidException.ERROR_CODE);
            assertThat(response.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        }};
    }

    private ActorRef newEnforcerActor(final ActorRef testActorRef) {
        return TestSetup.newEnforcerActor(system, testActorRef, mockEntitiesActor);
    }

    private ActorRef newEnforcerActor(final ActorRef testActorRef, final ActorRef conciergeForwarderRef) {
        return new TestSetup.EnforcerActorBuilder(system, testActorRef, mockEntitiesActor, mockEntitiesActor)
                .setConciergeForwarder(conciergeForwarderRef).build();
    }

    private DittoHeaders headers() {
        return DittoHeaders.newBuilder()
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        TestSetup.SUBJECT,
                        AuthorizationSubject.newInstance(String.format("%s:%s", GOOGLE, TestSetup.SUBJECT_ID))))
                .correlationId(testName.getMethodName())
                .schemaVersion(JsonSchemaVersion.V_2)
                .build();
    }

    private static ThingBuilder.FromScratch newThing() {
        return ThingsModelFactory.newThingBuilder()
                .setId(TestSetup.THING_ID)
                .setRevision(1L);
    }

    private static JsonObject newThingWithAttributeWithPolicyId(final CharSequence policyId) {
        return ThingsModelFactory.newThingBuilder()
                .setId(TestSetup.THING_ID)
                .setAttribute(JsonPointer.of("/testAttr"), JsonValue.of("testString"))
                .setRevision(1L)
                .setPolicyId(PolicyId.of(policyId))
                .build()
                .toJson(V_2, FieldType.all());
    }

    private ThingCommand<?> getReadCommand() {
        return RetrieveThing.of(TestSetup.THING_ID, headers());
    }

    private ThingCommand<?> getModifyCommand() {
        return getModifyCommand(headers());
    }

    private static ThingCommand<?> getModifyCommand(final DittoHeaders dittoHeaders) {
        return ModifyFeature.of(TestSetup.THING_ID, Feature.newBuilder().withId("x").build(), dittoHeaders);
    }

    private RetrieveThing getRetrieveThing(final Consumer<DittoHeadersBuilder<?, ?>> headerModifier) {
        final DittoHeadersBuilder<?, ?> builder = headers().toBuilder();
        headerModifier.accept(builder);
        return RetrieveThing.of(TestSetup.THING_ID, builder.build());
    }

}
