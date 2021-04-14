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
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;
import static org.eclipse.ditto.model.policies.SubjectIssuer.GOOGLE;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.POLICY_SUDO;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.SUBJECT;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.SUBJECT_ID;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.THING_ID;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.THING_SUDO;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.fishForMsgClass;

import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageBuilder;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageSendNotAllowedException;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.models.things.Permission;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.utils.pubsub.StreamingType;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessage;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
import org.eclipse.ditto.signals.commands.messages.SendThingMessageResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.EventSendNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;

@SuppressWarnings({"squid:S3599", "squid:S1171"})
public final class LiveSignalEnforcementTest {

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
            mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(readCommand(), getRef());
            fishForMsgClass(this, ThingNotAccessibleException.class);

            underTest.tell(writeCommand(), getRef());
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

            final ThingCommand<?> write = writeCommand();
            mockEntitiesActorInstance.setReply(write);
            underTest.tell(write, getRef());
            final DistributedPubSubMediator.Publish publish =
                    fishForMsgClass(this, DistributedPubSubMediator.Publish.class);
            assertThat(publish.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
            assertThat(publish.msg()).isInstanceOf(ThingCommand.class);
            assertThat((CharSequence) ((ThingCommand<?>) publish.msg()).getEntityId()).isEqualTo(write.getEntityId());

            final ThingCommand<?> read = readCommand();
            final RetrieveThingResponse retrieveThingResponse =
                    RetrieveThingResponse.of(THING_ID, JsonFactory.newObject(), DittoHeaders.empty());
            mockEntitiesActorInstance.setReply(retrieveThingResponse);
            underTest.tell(read, getRef());
            final DistributedPubSubMediator.Publish publishRead =
                    expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publishRead.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
            assertThat(publishRead.msg()).isInstanceOf(ThingCommand.class);
            assertThat((CharSequence) ((ThingCommand<?>) publishRead.msg()).getEntityId()).isEqualTo(read.getEntityId());
        }};
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
            mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(thingMessageCommand(), getRef());
            fishForMsgClass(this, MessageSendNotAllowedException.class);
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
            mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());

            final MessageCommand<?, ?> msgCommand = thingMessageCommand();
            mockEntitiesActorInstance.setReply(msgCommand);
            underTest.tell(msgCommand, getRef());
            final DistributedPubSubMediator.Publish publish =
                    fishForMsgClass(this, DistributedPubSubMediator.Publish.class);
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
                .setGrantedPermissions(PoliciesResourceType.messageResource("/features/foo/inbox/messages/my-subject"),
                        Permissions.newInstance(Permission.WRITE))
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

            final MessageCommand<?, ?> msgCommand = featureMessageCommand();
            mockEntitiesActorInstance.setReply(msgCommand);
            underTest.tell(msgCommand, getRef());
            final DistributedPubSubMediator.Publish publish =
                    fishForMsgClass(this, DistributedPubSubMediator.Publish.class);
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
            mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(liveEvent(), getRef());
            fishForMsgClass(this, EventSendNotAllowedException.class);
        }};
    }

    @Test
    public void acceptLiveEventByPolicy() {
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

            final ThingEvent<?> liveEvent = liveEvent();
            mockEntitiesActorInstance.setReply(liveEvent);
            underTest.tell(liveEvent, getRef());

            final DistributedPubSubMediator.Publish publish =
                    fishForMsgClass(this, DistributedPubSubMediator.Publish.class);
            assertThat(publish.topic()).isEqualTo(StreamingType.LIVE_EVENTS.getDistributedPubSubTopic());
            assertThat(publish.msg()).isInstanceOf(Event.class);
            assertThat((CharSequence) ((ThingEvent<?>) publish.msg()).getEntityId()).isEqualTo(liveEvent.getEntityId());
        }};
    }

    private ActorRef newEnforcerActor(final ActorRef testActorRef) {
        return TestSetup.newEnforcerActor(system, testActorRef, mockEntitiesActor);
    }

    private static JsonObject newThingWithPolicyId(final PolicyId policyId) {
        return newThing()
                .setPolicyId(policyId)
                .build()
                .toJson(V_2, FieldType.all());
    }

    private static DittoHeaders headers() {
        return DittoHeaders.newBuilder()
                .authorizationContext(
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, SUBJECT,
                                AuthorizationSubject.newInstance(String.format("%s:%s", GOOGLE, SUBJECT_ID))))
                .channel("live")
                .schemaVersion(JsonSchemaVersion.V_2)
                .correlationId(UUID.randomUUID().toString())
                .build();
    }

    private static ThingBuilder.FromScratch newThing() {
        return ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(1L);
    }

    private static ThingCommand<?> readCommand() {
        return RetrieveThing.of(THING_ID, headers());
    }

    private static ThingCommand<?> writeCommand() {
        return ModifyFeature.of(THING_ID, Feature.newBuilder().withId("x").build(), headers());
    }

    private static MessageCommand<?, ?> thingMessageCommand() {
        final Message<Object> message = Message.newBuilder(
                MessageBuilder.newHeadersBuilder(MessageDirection.TO, THING_ID, "my-subject")
                        .contentType("text/plain")
                        .build())
                .payload("Hello you!")
                .build();
        return SendThingMessage.of(THING_ID, message, headers());
    }

    private static MessageCommandResponse<?, ?> thingMessageCommandResponse(final MessageCommand<?, ?> command) {
        return SendThingMessageResponse.of(command.getEntityId(), command.getMessage(),
                HttpStatus.VARIANT_ALSO_NEGOTIATES, command.getDittoHeaders());
    }

    private static MessageCommand<?, ?> featureMessageCommand() {
        final Message<?> message = Message.newBuilder(
                MessageBuilder.newHeadersBuilder(MessageDirection.TO, THING_ID, "my-subject")
                        .contentType("text/plain")
                        .featureId("foo")
                        .build())
                .payload("Hello you!")
                .build();
        return SendFeatureMessage.of(THING_ID, "foo", message, headers());
    }

    private static ThingEvent<?> liveEvent() {
        return AttributeModified.of(THING_ID, JsonPointer.of("foo"), JsonValue.of("bar"), 1L, null, headers(), null);
    }

}
