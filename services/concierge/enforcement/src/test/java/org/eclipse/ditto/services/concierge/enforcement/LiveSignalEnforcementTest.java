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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_1;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;
import static org.eclipse.ditto.model.policies.SubjectIssuer.GOOGLE;
import static org.eclipse.ditto.model.things.Permission.ADMINISTRATE;
import static org.eclipse.ditto.model.things.Permission.READ;
import static org.eclipse.ditto.model.things.Permission.WRITE;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.POLICY_SUDO;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.SUBJECT;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.THING_ID;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.THING_SUDO;

import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageBuilder;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageSendNotAllowedException;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessage;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
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
        system = ActorSystem.create();
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
    public void rejectLiveThingCommandByAcl() {
        final JsonObject thingWithEmptyAcl = newThing()
                .setPermissions(AccessControlList.newBuilder().build())
                .build()
                .toJson(V_1, FieldType.all());
        final SudoRetrieveThingResponse response =
                SudoRetrieveThingResponse.of(thingWithEmptyAcl, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, response);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(readCommand(), getRef());
            expectMsgClass(ThingNotAccessibleException.class);

            underTest.tell(writeCommand(), getRef());
            expectMsgClass(FeatureNotModifiableException.class);
        }};
    }

    @Test
    public void rejectLiveThingCommandByPolicy() {
        final String policyId = "empty:policy";
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
            expectMsgClass(ThingNotAccessibleException.class);

            underTest.tell(writeCommand(), getRef());
            expectMsgClass(FeatureNotModifiableException.class);
        }};
    }

    @Test
    public void acceptLiveThingCommandByAcl() {
        final JsonObject thingWithAcl = newThing()
                .setPermissions(
                        AclEntry.newInstance(SUBJECT, READ, WRITE, ADMINISTRATE))
                .build()
                .toJson(V_1, FieldType.all());
        final SudoRetrieveThingResponse response =
                SudoRetrieveThingResponse.of(thingWithAcl, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, response);

            final ActorRef underTest = newEnforcerActor(getRef());
            final ThingCommand read = readCommand();
            mockEntitiesActorInstance.setReply(read);
            underTest.tell(read, getRef());
            final DistributedPubSubMediator.Publish publish = expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
            assertThat(publish.msg()).isInstanceOf(ThingCommand.class);
            assertThat(((ThingCommand) publish.msg()).getId()).isEqualTo(read.getId());

            final ThingCommand write = writeCommand();
            mockEntitiesActorInstance.setReply(write);
            underTest.tell(write, getRef());
            final DistributedPubSubMediator.Publish publishWrite =
                    expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publishWrite.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
            assertThat(publishWrite.msg()).isInstanceOf(ThingCommand.class);
            assertThat(((ThingCommand) publishWrite.msg()).getId()).isEqualTo(write.getId());
        }};
    }

    @Test
    public void acceptLiveThingCommandByPolicy() {
        final String policyId = "policy:id";
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT.getId())
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        READ.name(),
                        WRITE.name())
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

            final ThingCommand write = writeCommand();
            mockEntitiesActorInstance.setReply(write);
            underTest.tell(write, getRef());
            final DistributedPubSubMediator.Publish publish = expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
            assertThat(publish.msg()).isInstanceOf(ThingCommand.class);
            assertThat(((ThingCommand) publish.msg()).getId()).isEqualTo(write.getId());

            final ThingCommand read = readCommand();
            final RetrieveThingResponse retrieveThingResponse =
                    RetrieveThingResponse.of(THING_ID, JsonFactory.newObject(), DittoHeaders.empty());
            mockEntitiesActorInstance.setReply(retrieveThingResponse);
            underTest.tell(read, getRef());
            final DistributedPubSubMediator.Publish publishRead =
                    expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publishRead.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
            assertThat(publishRead.msg()).isInstanceOf(ThingCommand.class);
            assertThat(((ThingCommand) publishRead.msg()).getId()).isEqualTo(read.getId());
        }};
    }

    @Test
    public void rejectMessageCommandByAcl() {
        final JsonObject thingWithEmptyAcl = newThing()
                .setPermissions(AccessControlList.newBuilder().build())
                .build()
                .toJson(V_1, FieldType.all());
        final SudoRetrieveThingResponse response =
                SudoRetrieveThingResponse.of(thingWithEmptyAcl, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, response);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(thingMessageCommand(), getRef());
            expectMsgClass(MessageSendNotAllowedException.class);
        }};
    }

    @Test
    public void rejectMessageCommandByPolicy() {
        final String policyId = "empty:policy";
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
            expectMsgClass(MessageSendNotAllowedException.class);
        }};
    }

    @Test
    public void acceptMessageCommandByAcl() {
        final JsonObject thingWithAcl = newThing()
                .setPermissions(
                        AclEntry.newInstance(SUBJECT, READ, WRITE, ADMINISTRATE))
                .build()
                .toJson(V_1, FieldType.all());
        final SudoRetrieveThingResponse response =
                SudoRetrieveThingResponse.of(thingWithAcl, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, response);

            final ActorRef underTest = newEnforcerActor(getRef());
            final MessageCommand msgCommand = thingMessageCommand();
            mockEntitiesActorInstance.setReply(msgCommand);
            underTest.tell(msgCommand, getRef());
            final DistributedPubSubMediator.Publish publish = expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish.topic()).isEqualTo(StreamingType.MESSAGES.getDistributedPubSubTopic());
            assertThat(publish.msg()).isInstanceOf(MessageCommand.class);
            assertThat(((MessageCommand) publish.msg()).getId()).isEqualTo(msgCommand.getId());
        }};
    }

    @Test
    public void acceptMessageCommandByPolicy() {
        final String policyId = "policy:id";
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT.getId())
                .setGrantedPermissions(PoliciesResourceType.messageResource(JsonPointer.empty()),
                        READ.name(),
                        WRITE.name())
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

            final MessageCommand msgCommand = thingMessageCommand();
            mockEntitiesActorInstance.setReply(msgCommand);
            underTest.tell(msgCommand, getRef());
            final DistributedPubSubMediator.Publish publish = expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish.topic()).isEqualTo(StreamingType.MESSAGES.getDistributedPubSubTopic());
            assertThat(publish.msg()).isInstanceOf(MessageCommand.class);
            assertThat(((MessageCommand) publish.msg()).getId()).isEqualTo(msgCommand.getId());
        }};
    }

    @Test
    public void acceptFeatureMessageCommandByPolicy() {
        final String policyId = "policy:id";
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT.getId())
                .setGrantedPermissions(PoliciesResourceType.messageResource("/features/foo/inbox/messages/my-subject"),
                        WRITE.name())
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

            final MessageCommand msgCommand = featureMessageCommand();
            mockEntitiesActorInstance.setReply(msgCommand);
            underTest.tell(msgCommand, getRef());
            final DistributedPubSubMediator.Publish publish = expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish.topic()).isEqualTo(StreamingType.MESSAGES.getDistributedPubSubTopic());
            assertThat(publish.msg()).isInstanceOf(MessageCommand.class);
            assertThat(((MessageCommand) publish.msg()).getId()).isEqualTo(msgCommand.getId());
        }};
    }

    @Test
    public void rejectLiveEventByAcl() {
        final JsonObject thingWithEmptyAcl = newThing()
                .setPermissions(AccessControlList.newBuilder().build())
                .build()
                .toJson(V_1, FieldType.all());
        final SudoRetrieveThingResponse response =
                SudoRetrieveThingResponse.of(thingWithEmptyAcl, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, response);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(liveEvent(), getRef());
            expectMsgClass(EventSendNotAllowedException.class);
        }};
    }

    @Test
    public void rejectLiveEventByPolicy() {
        final String policyId = "empty:policy";
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
            expectMsgClass(EventSendNotAllowedException.class);
        }};
    }

    @Test
    public void acceptLiveEventByAcl() {
        final JsonObject thingWithAcl = newThing()
                .setPermissions(
                        AclEntry.newInstance(SUBJECT, READ, WRITE, ADMINISTRATE))
                .build()
                .toJson(V_1, FieldType.all());
        final SudoRetrieveThingResponse response =
                SudoRetrieveThingResponse.of(thingWithAcl, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, response);

            final ActorRef underTest = newEnforcerActor(getRef());
            final ThingEvent liveEvent = liveEvent();
            mockEntitiesActorInstance.setReply(liveEvent);
            underTest.tell(liveEvent, getRef());
            final DistributedPubSubMediator.Publish publish = expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish.topic()).isEqualTo(StreamingType.LIVE_EVENTS.getDistributedPubSubTopic());
            assertThat(publish.msg()).isInstanceOf(Event.class);
            assertThat(((Event) publish.msg()).getId()).isEqualTo(liveEvent.getId());
        }};
    }

    @Test
    public void acceptLiveEventByPolicy() {
        final String policyId = "policy:id";
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT.getId())
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        READ.name(),
                        WRITE.name())
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

            final ThingEvent liveEvent = liveEvent();
            mockEntitiesActorInstance.setReply(liveEvent);
            underTest.tell(liveEvent, getRef());
            final DistributedPubSubMediator.Publish publish = expectMsgClass(DistributedPubSubMediator.Publish.class);
            assertThat(publish.topic()).isEqualTo(StreamingType.LIVE_EVENTS.getDistributedPubSubTopic());
            assertThat(publish.msg()).isInstanceOf(Event.class);
            assertThat(((Event) publish.msg()).getId()).isEqualTo(liveEvent.getId());
        }};
    }

    private ActorRef newEnforcerActor(final ActorRef testActorRef) {
        return TestSetup.newEnforcerActor(system, testActorRef, mockEntitiesActor);
    }

    private static JsonObject newThingWithPolicyId(final String policyId) {
        return newThing()
                .setPolicyId(policyId)
                .build()
                .toJson(V_2, FieldType.all());
    }

    private static DittoHeaders headers(final JsonSchemaVersion schemaVersion) {
        return DittoHeaders.newBuilder()
                .authorizationSubjects(SUBJECT.getId(), String.format("%s:%s", GOOGLE, SUBJECT))
                .channel("live")
                .schemaVersion(schemaVersion)
                .correlationId(UUID.randomUUID().toString())
                .build();
    }

    private static ThingBuilder.FromScratch newThing() {
        return ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(1L);
    }

    private static ThingCommand readCommand() {
        return RetrieveThing.of(THING_ID, headers(V_2));
    }

    private static ThingCommand writeCommand() {
        return ModifyFeature.of(THING_ID, Feature.newBuilder().withId("x").build(), headers(V_2));
    }

    private static MessageCommand thingMessageCommand() {
        final Message<?> message = Message.newBuilder(
                MessageBuilder.newHeadersBuilder(MessageDirection.TO, THING_ID, "my-subject")
                        .contentType("text/plain")
                        .build())
                .payload("Hello you!")
                .build();
        return SendThingMessage.of(THING_ID, message, headers(V_2));
    }

    private static MessageCommand featureMessageCommand() {
        final Message<?> message = Message.newBuilder(
                MessageBuilder.newHeadersBuilder(MessageDirection.TO, THING_ID, "my-subject")
                        .contentType("text/plain")
                        .featureId("foo")
                        .build())
                .payload("Hello you!")
                .build();
        return SendFeatureMessage.of(THING_ID, "foo", message, headers(V_2));
    }

    private static ThingEvent liveEvent() {
        return AttributeModified.of(THING_ID, JsonPointer.of("foo"), JsonValue.of("bar"), 1L, headers(V_2));
    }

}
