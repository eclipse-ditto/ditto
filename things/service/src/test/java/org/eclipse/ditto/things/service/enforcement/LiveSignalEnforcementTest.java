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
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
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
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
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
import org.junit.ClassRule;
import org.junit.Test;

import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.javadsl.TestKit;
import scala.PartialFunction;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor} and its
 * {@link org.eclipse.ditto.things.service.enforcement.ThingEnforcement} for "live" related commands enforced by
 * {@link org.eclipse.ditto.things.service.enforcement.LiveSignalEnforcement}.
 */
@SuppressWarnings({"squid:S3599", "squid:S1171"})
public final class LiveSignalEnforcementTest extends AbstractThingEnforcementTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    @Test
    public void rejectMessageCommandByPolicy() {
        final PolicyId policyId = PolicyId.of("empty:policy");
        final JsonObject thingWithEmptyPolicy = TestSetup.newThingWithPolicyId(policyId);
        final Policy emptyPolicy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .build();
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithEmptyPolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(emptyPolicy))));

        new TestKit(system) {{
            supervisor.tell(thingMessageCommand("abc"), getRef());
            expectAndAnswerSudoRetrieveThingWithSpecificTimeout(sudoRetrieveThingResponse, FiniteDuration.apply(15,
                    TimeUnit.SECONDS));
            TestSetup.fishForMsgClass(this, MessageSendNotAllowedException.class);
        }};
    }

    @Test
    public void rejectLiveThingCommandByPolicy() {
        final PolicyId policyId = PolicyId.of("empty", "policy");
        final JsonObject thingWithEmptyPolicy = TestSetup.newThingWithPolicyId(policyId);
        final Policy emptyPolicy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .build();
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithEmptyPolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(emptyPolicy))));

        new TestKit(system) {{
            supervisor.tell(getRetrieveThingCommand(liveHeaders()), getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            TestSetup.fishForMsgClass(this, ThingNotAccessibleException.class);

            supervisor.tell(getModifyFeatureCommand(liveHeaders()), getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectMsgClass(FeatureNotModifiableException.class);
        }};
    }

    @Test
    public void acceptLiveThingCommandByPolicy() {
        final PolicyId policyId = PolicyId.of("policy", "id");
        final JsonObject thingWithPolicy = TestSetup.newThingWithPolicyId(policyId);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithPolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

        new TestKit(system) {{
            final ThingCommand<?> write = getModifyFeatureCommand(liveHeaders());
            supervisor.tell(write, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            expectPubsubLiveCommandPublish("publish live command", write.getEntityId());

            final ThingCommand<?> read = getRetrieveThingCommand(liveHeaders());
            RetrieveThingResponse.of(TestSetup.THING_ID, JsonFactory.newObject(), DittoHeaders.empty());
            supervisor.tell(read, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
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
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setRevokedPermissions(PoliciesResourceType.thingResource("/features/x/properties/key2"),
                        Permissions.newInstance(Permission.READ))
                .build();
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithPolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

        new TestKit(system) {{
            final DittoHeaders headers = liveHeaders();
            final ThingCommand<?> read = getRetrieveThingCommand(headers);

            supervisor.tell(read, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectPubsubLiveCommandPublish("publish live read command", read.getEntityId());

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
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
            assertThat(retrieveThingResponse.getThing()).isEqualTo(expectedThing);
        }};
    }

    @Test
    public void correlationIdSameAfterResponseSuccessful() {
        final PolicyId policyId = PolicyId.of("policy", "id");
        final JsonObject thingWithPolicy = TestSetup.newThingWithPolicyId(policyId);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setRevokedPermissions(PoliciesResourceType.thingResource("/features/x/properties/key2"),
                        Permissions.newInstance(Permission.READ))
                .build();
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithPolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

        new TestKit(system) {{
            final DittoHeaders headers = liveHeaders();
            final ThingCommand<?> read = getRetrieveThingCommand(headers);

            supervisor.tell(read, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            expectPubsubLiveCommandPublish("publish live read command", read.getEntityId());

            final var responseHeaders = headers.toBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(
                            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                            AuthorizationSubject.newInstance("myIssuer:mySubject")))
                    .build();

            final ThingCommandResponse<?> readResponse = getRetrieveThingResponse(responseHeaders);

            // Second message right after the response for the first was sent, should have the same correlation-id (Not suffixed).
            supervisor.tell(readResponse, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
            assertThat(retrieveThingResponse.getDittoHeaders().getCorrelationId()).isEqualTo(
                    read.getDittoHeaders().getCorrelationId());

            final DittoHeaders headers2 = headers.toBuilder()
                    .correlationId(headers.getCorrelationId().get() + "2")
                    .build();
            final ThingCommand<?> read2 = getRetrieveThingCommand(headers2);
            final var responseHeaders2 = headers2.toBuilder()
                    .authorizationContext(AuthorizationContext.newInstance(
                            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                            AuthorizationSubject.newInstance("myIssuer:mySubject")))
                    .build();

            final ThingCommandResponse<?> readResponse2 = getRetrieveThingResponse(responseHeaders2);

            supervisor.tell(read2, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            expectPubsubLiveCommandPublish("publish live read command", read2.getEntityId());

            supervisor.tell(readResponse2, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            final RetrieveThingResponse retrieveThingResponse2 = expectMsgClass(RetrieveThingResponse.class);
            assertThat(retrieveThingResponse2.getDittoHeaders().getCorrelationId()).isEqualTo(
                    read2.getDittoHeaders().getCorrelationId());
        }};
    }

    @Test
    public void correlationIdDifferentInCaseOfConflict() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = TestSetup.newThingWithPolicyId(policyId);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.messageResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithPolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

        new TestKit(system) {{

            final MessageCommand<?, ?> message = thingMessageCommand("abc");

            supervisor.tell(message, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            final var firstPublishRead = expectPubsubMessagePublish(message.getEntityId());
            assertThat((CharSequence) ((WithDittoHeaders) firstPublishRead.msg()).getDittoHeaders()
                    .getCorrelationId()
                    .orElseThrow()).isEqualTo(
                    message.getDittoHeaders().getCorrelationId().orElseThrow());

            supervisor.tell(message, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            final var secondPublishRead = expectPubsubMessagePublish(message.getEntityId());
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
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.messageResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithPolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

        new TestKit(system) {{
            final MessageCommand<?, ?> msgCommand = thingMessageCommand("abc");
            supervisor.tell(msgCommand, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            expectPubsubMessagePublish(msgCommand.getEntityId());
        }};
    }

    @Test
    public void acceptFeatureMessageCommandByPolicy() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = TestSetup.newThingWithPolicyId(policyId);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(
                        PoliciesResourceType.messageResource("/features/foo/inbox/messages/my-subject"),
                        Permissions.newInstance(Permission.WRITE))
                .build();
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithPolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

        new TestKit(system) {{
            final MessageCommand<?, ?> msgCommand = featureMessageCommand();
            supervisor.tell(msgCommand, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            expectPubsubMessagePublish(msgCommand.getEntityId());
        }};
    }

    @Test
    public void rejectLiveEventByPolicy() {
        final PolicyId policyId = PolicyId.of("empty:policy");
        final JsonObject thingWithEmptyPolicy = TestSetup.newThingWithPolicyId(policyId);
        final Policy emptyPolicy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .build();
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithEmptyPolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(emptyPolicy))));
        new TestKit(system) {{
            supervisor.tell(liveEventGranted(), getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);
            TestSetup.fishForMsgClass(this, EventSendNotAllowedException.class);
        }};
    }

    @Test
    public void acceptLiveEventByPolicyWithRestrictedPermissions() {
        final PolicyId policyId = PolicyId.of("policy:id");
        final JsonObject thingWithPolicy = TestSetup.newThingWithPolicyId(policyId);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes"),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes/xyz/abc"),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithPolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(policy))));

        new TestKit(system) {{
            final ThingEvent<?> liveEventGranted = liveEventGranted();
            supervisor.tell(liveEventGranted, getRef());

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

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

            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            TestSetup.fishForMsgClass(this, EventSendNotAllowedException.class);
        }};
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

    private void expectPubsubLiveCommandPublish(final String fishingHint, final ThingId write) {
        final DistributedPubSubMediator.Publish publishLiveCommand =
                (DistributedPubSubMediator.Publish) pubSubMediatorProbe.fishForMessage(
                        FiniteDuration.apply(5, "s"),
                        fishingHint,
                        PartialFunction.fromFunction(msg ->
                                msg instanceof DistributedPubSubMediator.Publish publish &&
                                        publish.topic().equals(
                                                StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic())
                        )
                );

        assertThat(publishLiveCommand.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
        assertThat(publishLiveCommand.msg()).isInstanceOf(ThingCommand.class);
        assertThat((CharSequence) ((ThingCommand<?>) publishLiveCommand.msg()).getEntityId()).isEqualTo(write);
    }

    private DistributedPubSubMediator.Publish expectPubsubMessagePublish(final ThingId expectedThingId) {
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
                .isEqualTo(expectedThingId);
        return publishMessageCommand;
    }
}
