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
import static org.eclipse.ditto.policies.enforcement.TestSetup.FEATURE_ID;
import static org.eclipse.ditto.policies.enforcement.TestSetup.FEATURE_PROPERTY_2;
import static org.eclipse.ditto.policies.enforcement.TestSetup.SUBJECT;
import static org.eclipse.ditto.policies.enforcement.TestSetup.SUBJECT_ID;
import static org.eclipse.ditto.policies.enforcement.TestSetup.THING;
import static org.eclipse.ditto.policies.enforcement.TestSetup.newThingWithPolicyId;
import static org.eclipse.ditto.policies.model.SubjectIssuer.GOOGLE;

import java.util.UUID;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageBuilder;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageSendNotAllowedException;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.EventSendNotAllowedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.concierge.api.enforcement.LiveSignalEnforcement} in context of an {@link org.eclipse.ditto.concierge.api.enforcement.EnforcerActor}.
 */
@SuppressWarnings({"squid:S3599", "squid:S1171"})
public final class LiveSignalEnforcementTest {

    private ActorSystem system;
    private MockEntitiesActor mockEntitiesActorInstance;
    private ActorRef mockEntitiesActor;
    private TestProbe pubSubMediatorProbe;

    @Before
    public void init() {
        system = ActorSystem.create("test", ConfigFactory.load("test"));
        final TestActorRef<MockEntitiesActor> testActorRef =
                new TestActorRef<>(system, MockEntitiesActor.props(), system.guardian(), UUID.randomUUID().toString());
        mockEntitiesActorInstance = testActorRef.underlyingActor();
        mockEntitiesActor = testActorRef;
        pubSubMediatorProbe = TestProbe.apply("pubSubMediator", system);
    }

    @After
    public void shutdown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void rejectMessageCommandByPolicy() {
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
            underTest.tell(thingMessageCommand("abc"), getRef());
            TestSetup.fishForMsgClass(this, MessageSendNotAllowedException.class);
        }};
    }

    @Test
    public void rejectLiveThingCommandByPolicy() {
        final PolicyId policyId = PolicyId.of("empty", "policy");
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
            underTest.tell(getRetrieveThingCommand(headers()), getRef());
            TestSetup.fishForMsgClass(this, ThingNotAccessibleException.class);

            underTest.tell(getModifyFeatureCommand(headers()), getRef());
            expectMsgClass(FeatureNotModifiableException.class);
        }};
    }

    @Test
    public void acceptLiveThingCommandByPolicy() {
        final PolicyId policyId = PolicyId.of("policy", "id");
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

            final ThingCommand<?> write = getModifyFeatureCommand(headers());
            mockEntitiesActorInstance.setReply(write);
            underTest.tell(write, getRef());
            final DistributedPubSubMediator.Publish publish =
                    pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
            assertThat(publish.msg()).isInstanceOf(ThingCommand.class);
            assertThat((CharSequence) ((ThingCommand<?>) publish.msg()).getEntityId()).isEqualTo(write.getEntityId());

            final ThingCommand<?> read = getRetrieveThingCommand(headers());
            final RetrieveThingResponse retrieveThingResponse =
                    RetrieveThingResponse.of(TestSetup.THING_ID, JsonFactory.newObject(), DittoHeaders.empty());
            mockEntitiesActorInstance.setReply(retrieveThingResponse);
            underTest.tell(read, getRef());
            final DistributedPubSubMediator.Publish publishRead =
                    pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);

            assertThat(publishRead.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
            assertThat(publishRead.msg()).isInstanceOf(ThingCommand.class);
            assertThat((CharSequence) ((ThingCommand<?>) publishRead.msg()).getEntityId()).isEqualTo(
                    read.getEntityId());
        }};
    }

    @Test
    public void retrieveLiveThingCommandAndResponseByPolicy() {
        final PolicyId policyId = PolicyId.of("policy", "id");
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setRevokedPermissions(PoliciesResourceType.thingResource("/features/x/properties/key2"),
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

            final DittoHeaders headers = headers();
            final ThingCommand<?> read = getRetrieveThingCommand(headers);

            underTest.tell(read, getRef());
            final DistributedPubSubMediator.Publish publishRead =
                    pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publishRead.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
            assertThat(publishRead.msg()).isInstanceOf(ThingCommand.class);
            assertThat((CharSequence) ((ThingCommand<?>) publishRead.msg()).getEntityId()).isEqualTo(
                    read.getEntityId());

            // the response auth ctx shall be ignored for filtering live retrieve responses,
            // the auth ctx of the requester is the right one.
            final var responseHeaders = headers.toBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(
                            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                            AuthorizationSubject.newInstance("myIssuer:mySubject")))
                    .build();

            final ThingCommandResponse<?> readResponse = getRetrieveThingResponse(responseHeaders);
            final Thing expectedThing = THING.toBuilder()
                    .removeFeatureProperty(FEATURE_ID, JsonPointer.of(FEATURE_PROPERTY_2))
                    .build();

            underTest.tell(readResponse, getRef());
            final RetrieveThingResponse retrieveThingResponse =
                    TestSetup.fishForMsgClass(this, RetrieveThingResponse.class);
            assertThat(retrieveThingResponse.getThing()).isEqualTo(expectedThing);
        }};
    }

    @Test
    public void correlationIdSameAfterResponseSuccessful() {
        final PolicyId policyId = PolicyId.of("policy", "id");
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setRevokedPermissions(PoliciesResourceType.thingResource("/features/x/properties/key2"),
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

            final DittoHeaders headers = headers();
            final ThingCommand<?> read = getRetrieveThingCommand(headers);

            underTest.tell(read, getRef());

            final var responseHeaders = headers.toBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(
                            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                            AuthorizationSubject.newInstance("myIssuer:mySubject")))
                    .build();

            final ThingCommandResponse<?> readResponse = getRetrieveThingResponse(responseHeaders);

            // Second message right after the response for the first was sent, should have the same correlation-id (Not suffixed).
            underTest.tell(readResponse, getRef());
            final RetrieveThingResponse retrieveThingResponse =
                    TestSetup.fishForMsgClass(this, RetrieveThingResponse.class);
            assertThat(retrieveThingResponse.getDittoHeaders().getCorrelationId()).isEqualTo(
                    read.getDittoHeaders().getCorrelationId());

            underTest.tell(read, getRef());

            underTest.tell(readResponse, getRef());
            final RetrieveThingResponse retrieveThingResponse2 =
                    TestSetup.fishForMsgClass(this, RetrieveThingResponse.class);
            assertThat(retrieveThingResponse2.getDittoHeaders().getCorrelationId()).isEqualTo(
                    read.getDittoHeaders().getCorrelationId());
        }};
    }

    @Test
    public void correlationIdDifferentInCaseOfConflict() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.messageResource(JsonPointer.empty()),
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

            final MessageCommand<?, ?> message = thingMessageCommand("abc");

            underTest.tell(message, getRef());
            final DistributedPubSubMediator.Publish firstPublishRead =
                    pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(firstPublishRead.topic()).isEqualTo(StreamingType.MESSAGES.getDistributedPubSubTopic());
            assertThat(firstPublishRead.msg()).isInstanceOf(MessageCommand.class);
            assertThat((CharSequence) ((WithEntityId) firstPublishRead.msg()).getEntityId()).isEqualTo(
                    message.getEntityId());
            assertThat((CharSequence) ((WithDittoHeaders) firstPublishRead.msg()).getDittoHeaders()
                    .getCorrelationId()
                    .orElseThrow()).isEqualTo(
                    message.getDittoHeaders().getCorrelationId().orElseThrow());

            underTest.tell(message, getRef());
            final DistributedPubSubMediator.Publish secondPublishRead =
                    pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(secondPublishRead.topic()).isEqualTo(StreamingType.MESSAGES.getDistributedPubSubTopic());
            assertThat(secondPublishRead.msg()).isInstanceOf(MessageCommand.class);
            assertThat((CharSequence) ((WithEntityId) secondPublishRead.msg()).getEntityId()).isEqualTo(
                    message.getEntityId());
            // Assure second command has suffixed correlation-id, because of conflict with first command.
            assertThat((CharSequence) ((WithDittoHeaders) secondPublishRead.msg()).getDittoHeaders()
                    .getCorrelationId()
                    .orElseThrow()).startsWith(
                    message.getDittoHeaders().getCorrelationId().orElseThrow());
            assertThat((CharSequence) ((WithDittoHeaders) secondPublishRead.msg()).getDittoHeaders()
                    .getCorrelationId()
                    .orElseThrow()).isNotEqualTo(
                    message.getDittoHeaders().getCorrelationId().orElseThrow());

        }};
    }

    @Test
    public void acceptMessageCommandByPolicy() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.messageResource(JsonPointer.empty()),
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

            final MessageCommand<?, ?> msgCommand = thingMessageCommand("abc");
            mockEntitiesActorInstance.setReply(msgCommand);
            underTest.tell(msgCommand, getRef());
            final DistributedPubSubMediator.Publish publish =
                    pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish.topic()).isEqualTo(StreamingType.MESSAGES.getDistributedPubSubTopic());
            assertThat(publish.msg()).isInstanceOf(MessageCommand.class);
            assertThat((CharSequence) ((MessageCommand<?, ?>) publish.msg()).getEntityId())
                    .isEqualTo(msgCommand.getEntityId());
        }};
    }

    @Test
    public void acceptFeatureMessageCommandByPolicy() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                .setGrantedPermissions(
                        PoliciesResourceType.messageResource("/features/foo/inbox/messages/my-subject"),
                        Permissions.newInstance(Permission.WRITE))
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

            final MessageCommand<?, ?> msgCommand = featureMessageCommand();
            mockEntitiesActorInstance.setReply(msgCommand);
            underTest.tell(msgCommand, getRef());
            final DistributedPubSubMediator.Publish publish =
                    pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish.topic()).isEqualTo(StreamingType.MESSAGES.getDistributedPubSubTopic());
            assertThat(publish.msg()).isInstanceOf(MessageCommand.class);
            assertThat((CharSequence) ((MessageCommand<?, ?>) publish.msg()).getEntityId())
                    .isEqualTo(msgCommand.getEntityId());
        }};
    }

    @Test
    public void rejectLiveEventByPolicy() {
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
            underTest.tell(liveEventGranted(), getRef());
            TestSetup.fishForMsgClass(this, EventSendNotAllowedException.class);
        }};
    }

    @Test
    public void acceptLiveEventByPolicyWithRestrictedPermissions() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes"),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes/xyz/abc"),
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

            final ThingEvent<?> liveEventGranted = liveEventGranted();

            mockEntitiesActorInstance.setReply(liveEventGranted);
            underTest.tell(liveEventGranted, getRef());

            final DistributedPubSubMediator.Publish publish =
                    pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish.topic()).isEqualTo(StreamingType.LIVE_EVENTS.getDistributedPubSubTopic());
            assertThat(publish.msg()).isInstanceOf(Event.class);
            assertThat((CharSequence) ((ThingEvent<?>) publish.msg()).getEntityId()).isEqualTo(
                    liveEventGranted.getEntityId());

            final ThingEvent<?> liveEventRevoked = liveEventRevoked();

            underTest.tell(liveEventRevoked, getRef());
            TestSetup.fishForMsgClass(this, EventSendNotAllowedException.class);
        }};
    }

    private ActorRef newEnforcerActor(final ActorRef testActorRef) {
        return TestSetup.newEnforcerActor(system, testActorRef, mockEntitiesActor, mockEntitiesActor,
                pubSubMediatorProbe.ref(), null, null);
    }

    private static DittoHeaders headers() {
        return DittoHeaders.newBuilder()
                .authorizationContext(
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, SUBJECT,
                                AuthorizationSubject.newInstance(String.format("%s:%s", GOOGLE, SUBJECT_ID))))
                .channel("live")
                .schemaVersion(JsonSchemaVersion.V_2)
                .randomCorrelationId()
                .build();
    }

    private static ThingCommand<?> getRetrieveThingCommand(final DittoHeaders headers) {
        return RetrieveThing.of(TestSetup.THING_ID, headers);
    }

    private static ThingCommandResponse<?> getRetrieveThingResponse(final DittoHeaders headers) {
        return RetrieveThingResponse.of(TestSetup.THING_ID, THING, null, null, headers);
    }

    private static ThingCommand<?> getModifyFeatureCommand(final DittoHeaders headers) {
        return ModifyFeature.of(TestSetup.THING_ID, TestSetup.FEATURE, headers);
    }

    private static MessageCommand<?, ?> thingMessageCommand(final String correlationId) {
        final Message<Object> message = Message.newBuilder(
                MessageBuilder.newHeadersBuilder(MessageDirection.TO, TestSetup.THING_ID, "my-subject")
                        .contentType("text/plain")
                        .correlationId(correlationId)
                        .build())
                .payload("Hello you!")
                .build();
        return SendThingMessage.of(TestSetup.THING_ID, message, headers());
    }

    private static ThingEvent<?> liveEventGranted() {
        return AttributeModified.of(TestSetup.THING_ID, JsonPointer.of("foo"), JsonValue.of("bar"), 1L,
                null, headers(), null);
    }

    private static ThingEvent<?> liveEventRevoked() {
        return AttributeModified.of(TestSetup.THING_ID, JsonPointer.of("xyz"), JsonValue.of("abc"), 1L,
                null, headers(), null);
    }

    private static MessageCommand<?, ?> featureMessageCommand() {
        final Message<?> message = Message.newBuilder(
                MessageBuilder.newHeadersBuilder(MessageDirection.TO, TestSetup.THING_ID, "my-subject")
                        .contentType("text/plain")
                        .featureId("foo")
                        .build())
                .payload("Hello you!")
                .build();
        return SendFeatureMessage.of(TestSetup.THING_ID, "foo", message, headers());
    }

}
