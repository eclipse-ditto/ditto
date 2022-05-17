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
import static org.eclipse.ditto.policies.model.SubjectIssuer.GOOGLE;
import static org.eclipse.ditto.things.service.enforcement.TestSetup.THING_ID;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
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
import org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor;
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
import scala.PartialFunction;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link LiveSignalEnforcement} in context of an {@code EnforcerActor}.
 */
@SuppressWarnings({"squid:S3599", "squid:S1171"})
public final class LiveSignalEnforcementTest {

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
    public void rejectMessageCommandByPolicy() {
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
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            supervisor.tell(thingMessageCommand("abc"), getRef());
            TestSetup.fishForMsgClass(this, MessageSendNotAllowedException.class);
        }};
    }

    @Test
    public void rejectLiveThingCommandByPolicy() {
        final PolicyId policyId = PolicyId.of("empty", "policy");
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
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            supervisor.tell(getRetrieveThingCommand(liveHeaders()), getRef());
            TestSetup.fishForMsgClass(this, ThingNotAccessibleException.class);

            supervisor.tell(getModifyFeatureCommand(liveHeaders()), getRef());
            expectMsgClass(FeatureNotModifiableException.class);
        }};
    }

    @Test
    public void acceptLiveThingCommandByPolicy() {
        final PolicyId policyId = PolicyId.of("policy", "id");
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

            final ThingCommand<?> write = getModifyFeatureCommand(liveHeaders());
            supervisor.tell(write, getRef());

            final DistributedPubSubMediator.Publish publishLiveCommand =
                    (DistributedPubSubMediator.Publish) pubSubMediatorProbe.fishForMessage(
                            FiniteDuration.apply(5, "s"),
                            "publish live command",
                            PartialFunction.fromFunction(msg ->
                                    msg instanceof DistributedPubSubMediator.Publish publish &&
                                            publish.topic().equals(
                                                    StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic())
                            )
                    );

            assertThat(publishLiveCommand.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
            assertThat(publishLiveCommand.msg()).isInstanceOf(ThingCommand.class);
            assertThat((CharSequence) ((ThingCommand<?>) publishLiveCommand.msg()).getEntityId()).isEqualTo(write.getEntityId());

            final ThingCommand<?> read = getRetrieveThingCommand(liveHeaders());
                    RetrieveThingResponse.of(TestSetup.THING_ID, JsonFactory.newObject(), DittoHeaders.empty());
            supervisor.tell(read, getRef());
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
        final JsonObject thingWithPolicy = TestSetup.newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
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
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            final DittoHeaders headers = liveHeaders();
            final ThingCommand<?> read = getRetrieveThingCommand(headers);

            supervisor.tell(read, getRef());
            final DistributedPubSubMediator.Publish publishRead =
                    (DistributedPubSubMediator.Publish) pubSubMediatorProbe.fishForMessage(
                            FiniteDuration.apply(5, "s"),
                            "publish live read command",
                            PartialFunction.fromFunction(msg ->
                                    msg instanceof DistributedPubSubMediator.Publish publish &&
                                            publish.topic().equals(
                                                    StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic())
                            )
                    );
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
            final Thing expectedThing = TestSetup.THING.toBuilder()
                    .removeFeatureProperty(TestSetup.FEATURE_ID, JsonPointer.of(TestSetup.FEATURE_PROPERTY_2))
                    .build();

            supervisor.tell(readResponse, getRef());
            final RetrieveThingResponse retrieveThingResponse =
                    TestSetup.fishForMsgClass(this, RetrieveThingResponse.class);
            assertThat(retrieveThingResponse.getThing()).isEqualTo(expectedThing);
        }};
    }

    @Test
    public void correlationIdSameAfterResponseSuccessful() {
        final PolicyId policyId = PolicyId.of("policy", "id");
        final JsonObject thingWithPolicy = TestSetup.newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
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
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            final DittoHeaders headers = liveHeaders();
            final ThingCommand<?> read = getRetrieveThingCommand(headers);

            supervisor.tell(read, getRef());

            final DistributedPubSubMediator.Publish publishRead =
                    (DistributedPubSubMediator.Publish) pubSubMediatorProbe.fishForMessage(
                            FiniteDuration.apply(5, "s"),
                            "publish live read command",
                            PartialFunction.fromFunction(msg ->
                                    msg instanceof DistributedPubSubMediator.Publish publish &&
                                            publish.topic().equals(
                                                    StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic())
                            )
                    );
            assertThat(publishRead.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
            assertThat(publishRead.msg()).isInstanceOf(ThingCommand.class);
            assertThat((CharSequence) ((ThingCommand<?>) publishRead.msg()).getEntityId()).isEqualTo(
                    read.getEntityId());

            final var responseHeaders = headers.toBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(
                            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                            AuthorizationSubject.newInstance("myIssuer:mySubject")))
                    .build();

            final ThingCommandResponse<?> readResponse = getRetrieveThingResponse(responseHeaders);

            // Second message right after the response for the first was sent, should have the same correlation-id (Not suffixed).
            supervisor.tell(readResponse, getRef());
            final RetrieveThingResponse retrieveThingResponse =
                    TestSetup.fishForMsgClass(this, RetrieveThingResponse.class);
            assertThat(retrieveThingResponse.getDittoHeaders().getCorrelationId()).isEqualTo(
                    read.getDittoHeaders().getCorrelationId());

            supervisor.tell(read, getRef());

            supervisor.tell(readResponse, getRef());
            final RetrieveThingResponse retrieveThingResponse2 =
                    TestSetup.fishForMsgClass(this, RetrieveThingResponse.class);
            assertThat(retrieveThingResponse2.getDittoHeaders().getCorrelationId()).isEqualTo(
                    read.getDittoHeaders().getCorrelationId());
        }};
    }

    @Test
    public void correlationIdDifferentInCaseOfConflict() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = TestSetup.newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.messageResource(JsonPointer.empty()),
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

            final MessageCommand<?, ?> message = thingMessageCommand("abc");

            supervisor.tell(message, getRef());
            final DistributedPubSubMediator.Publish firstPublishRead =
                    (DistributedPubSubMediator.Publish) pubSubMediatorProbe.fishForMessage(
                            FiniteDuration.apply(5, "s"),
                            "publish message command",
                            PartialFunction.fromFunction(msg ->
                                    msg instanceof DistributedPubSubMediator.Publish publish &&
                                            publish.topic().equals(
                                                    StreamingType.MESSAGES.getDistributedPubSubTopic())
                            )
                    );


            assertThat(firstPublishRead.topic()).isEqualTo(StreamingType.MESSAGES.getDistributedPubSubTopic());
            assertThat(firstPublishRead.msg()).isInstanceOf(MessageCommand.class);
            assertThat((CharSequence) ((WithEntityId) firstPublishRead.msg()).getEntityId()).isEqualTo(
                    message.getEntityId());
            assertThat((CharSequence) ((WithDittoHeaders) firstPublishRead.msg()).getDittoHeaders()
                    .getCorrelationId()
                    .orElseThrow()).isEqualTo(
                    message.getDittoHeaders().getCorrelationId().orElseThrow());

            supervisor.tell(message, getRef());
            final DistributedPubSubMediator.Publish secondPublishRead =
                    (DistributedPubSubMediator.Publish) pubSubMediatorProbe.fishForMessage(
                            FiniteDuration.apply(5, "s"),
                            "publish message command",
                            PartialFunction.fromFunction(msg ->
                                    msg instanceof DistributedPubSubMediator.Publish publish &&
                                            publish.topic().equals(
                                                    StreamingType.MESSAGES.getDistributedPubSubTopic())
                            )
                    );
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
        final JsonObject thingWithPolicy = TestSetup.newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.messageResource(JsonPointer.empty()),
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

            final MessageCommand<?, ?> msgCommand = thingMessageCommand("abc");
            supervisor.tell(msgCommand, getRef());
            final DistributedPubSubMediator.Publish publishMessageCommand =
                    (DistributedPubSubMediator.Publish) pubSubMediatorProbe.fishForMessage(
                            FiniteDuration.apply(5, "s"),
                            "publish message command",
                            PartialFunction.fromFunction(msg ->
                                    msg instanceof DistributedPubSubMediator.Publish publish &&
                                            publish.topic().equals(
                                                    StreamingType.MESSAGES.getDistributedPubSubTopic())
                            )
                    );
            assertThat(publishMessageCommand.topic()).isEqualTo(StreamingType.MESSAGES.getDistributedPubSubTopic());
            assertThat(publishMessageCommand.msg()).isInstanceOf(MessageCommand.class);
            assertThat((CharSequence) ((MessageCommand<?, ?>) publishMessageCommand.msg()).getEntityId())
                    .isEqualTo(msgCommand.getEntityId());
        }};
    }

    @Test
    public void acceptFeatureMessageCommandByPolicy() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = TestSetup.newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
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
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            final MessageCommand<?, ?> msgCommand = featureMessageCommand();
            supervisor.tell(msgCommand, getRef());
            final DistributedPubSubMediator.Publish publishMessageCommand =
                    (DistributedPubSubMediator.Publish) pubSubMediatorProbe.fishForMessage(
                            FiniteDuration.apply(5, "s"),
                            "publish message command",
                            PartialFunction.fromFunction(msg ->
                                    msg instanceof DistributedPubSubMediator.Publish publish &&
                                            publish.topic().equals(
                                                    StreamingType.MESSAGES.getDistributedPubSubTopic())
                            )
                    );
            assertThat(publishMessageCommand.topic()).isEqualTo(StreamingType.MESSAGES.getDistributedPubSubTopic());
            assertThat(publishMessageCommand.msg()).isInstanceOf(MessageCommand.class);
            assertThat((CharSequence) ((MessageCommand<?, ?>) publishMessageCommand.msg()).getEntityId())
                    .isEqualTo(msgCommand.getEntityId());
        }};
    }

    @Test
    public void rejectLiveEventByPolicy() {
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
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            supervisor.tell(liveEventGranted(), getRef());
            TestSetup.fishForMsgClass(this, EventSendNotAllowedException.class);
        }};
    }

    @Test
    public void acceptLiveEventByPolicyWithRestrictedPermissions() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = TestSetup.newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
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
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrievePolicy(policyId, sudoRetrievePolicyResponse);

            final ThingEvent<?> liveEventGranted = liveEventGranted();
            supervisor.tell(liveEventGranted, getRef());

            final DistributedPubSubMediator.Publish publishLiveEvent =
                    (DistributedPubSubMediator.Publish) pubSubMediatorProbe.fishForMessage(
                            FiniteDuration.apply(5, "s"),
                            "publish live event",
                            PartialFunction.fromFunction(msg ->
                                    msg instanceof DistributedPubSubMediator.Publish publish &&
                                            publish.topic().equals(
                                                    StreamingType.LIVE_EVENTS.getDistributedPubSubTopic())
                            )
                    );
            assertThat(publishLiveEvent.topic()).isEqualTo(StreamingType.LIVE_EVENTS.getDistributedPubSubTopic());
            assertThat(publishLiveEvent.msg()).isInstanceOf(Event.class);
            assertThat((CharSequence) ((ThingEvent<?>) publishLiveEvent.msg()).getEntityId()).isEqualTo(
                    liveEventGranted.getEntityId());

            final ThingEvent<?> liveEventRevoked = liveEventRevoked();

            supervisor.tell(liveEventRevoked, getRef());
            TestSetup.fishForMsgClass(this, EventSendNotAllowedException.class);
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

    private static DittoHeaders liveHeaders() {
        return DittoHeaders.newBuilder()
                .authorizationContext(
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, TestSetup.SUBJECT,
                                AuthorizationSubject.newInstance(String.format("%s:%s", GOOGLE, TestSetup.SUBJECT_ID))))
                .channel("live")
                .schemaVersion(JsonSchemaVersion.V_2)
                .randomCorrelationId()
                .build();
    }

    private static ThingCommand<?> getRetrieveThingCommand(final DittoHeaders headers) {
        return RetrieveThing.of(TestSetup.THING_ID, headers);
    }

    private static ThingCommandResponse<?> getRetrieveThingResponse(final DittoHeaders headers) {
        return RetrieveThingResponse.of(TestSetup.THING_ID, TestSetup.THING, null, null, headers);
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
        return SendThingMessage.of(TestSetup.THING_ID, message, liveHeaders());
    }

    private static ThingEvent<?> liveEventGranted() {
        return AttributeModified.of(TestSetup.THING_ID, JsonPointer.of("foo"), JsonValue.of("bar"), 1L,
                null, liveHeaders(), null);
    }

    private static ThingEvent<?> liveEventRevoked() {
        return AttributeModified.of(TestSetup.THING_ID, JsonPointer.of("xyz"), JsonValue.of("abc"), 1L,
                null, liveHeaders(), null);
    }

    private static MessageCommand<?, ?> featureMessageCommand() {
        final Message<?> message = Message.newBuilder(
                MessageBuilder.newHeadersBuilder(MessageDirection.TO, TestSetup.THING_ID, "my-subject")
                        .contentType("text/plain")
                        .featureId("foo")
                        .build())
                .payload("Hello you!")
                .build();
        return SendFeatureMessage.of(TestSetup.THING_ID, "foo", message, liveHeaders());
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
}
