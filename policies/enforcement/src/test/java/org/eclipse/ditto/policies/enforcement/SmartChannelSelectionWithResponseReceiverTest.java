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
import static org.eclipse.ditto.connectivity.model.Topic.LIVE_COMMANDS;
import static org.eclipse.ditto.policies.enforcement.TestSetup.THING_SUDO;
import static org.eclipse.ditto.policies.enforcement.TestSetup.newThingWithPolicyId;
import static org.eclipse.ditto.policies.model.PoliciesResourceType.THING;
import static org.eclipse.ditto.policies.model.SubjectIssuer.GOOGLE;

import java.util.UUID;
import java.util.function.Consumer;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.internal.models.signal.SignalInformationPoint;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
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
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.concierge.api.enforcement.ThingCommandEnforcement} in context of an {@link org.eclipse.ditto.concierge.api.enforcement.EnforcerActor} for commands requiring smart channel
 * selection where live responses come from a different channel than where the command is published.
 */
public final class SmartChannelSelectionWithResponseReceiverTest {

    @Rule
    public final TestName testName = new TestName();

    private ActorSystem system;
    private MockEntitiesActor mockEntitiesActorInstance;
    private ActorRef mockEntitiesActor;
    private JsonObject thing;

    @Before
    public void init() {
        system = ActorSystem.create("test", ConfigFactory.load("test"));
        final TestActorRef<MockEntitiesActor> testActorRef =
                new TestActorRef<>(system, MockEntitiesActor.props(), system.guardian(), UUID.randomUUID().toString());
        mockEntitiesActorInstance = testActorRef.underlyingActor();
        mockEntitiesActor = testActorRef;
        final PolicyId policyId = PolicyId.of("policy:id");
        thing = newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build()
                .toJson(FieldType.all());
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thing, DittoHeaders.empty());
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());

        mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
        mockEntitiesActorInstance.setReply(TestSetup.POLICY_SUDO, sudoRetrievePolicyResponse);
    }

    @After
    public void shutdown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void matchLiveChannelCondition() {
        new TestKit(system) {{
            final ActorRef underTest = newEnforcerActor(getRef());
            final var retrieveThing = getRetrieveThing(headers -> headers.liveChannelCondition("exists(thingId)"));
            mockEntitiesActorInstance.setReply(THING, getRetrieveThingResponse(retrieveThing, b -> {}));
            underTest.tell(retrieveThing, getRef());
            final var publish = TestSetup.fishForMsgClass(this, DistributedPubSubMediator.Publish.class);
            Assertions.assertThat(publish.topic()).isEqualTo(LIVE_COMMANDS.getPubSubTopic());
            Assertions.assertThat(publish.message()).isInstanceOf(RetrieveThing.class);
            assertLiveChannel(RetrieveThing.class, publish.message());
            underTest.tell(getRetrieveThingResponse(retrieveThing, b -> b.channel("live")), ActorRef.noSender());
            assertLiveChannel(expectMsgClass(RetrieveThingResponse.class));
        }};
    }

    @Test
    public void liveChannelErrorWithTwinFallback() {
        new TestKit(system) {{
            final ActorRef underTest = newEnforcerActor(getRef());
            final var retrieveThing = getRetrieveThing(headers -> headers.liveChannelCondition("exists(thingId)")
                    .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin"));
            final var twinResponse = getRetrieveThingResponse(retrieveThing, b -> {});
            mockEntitiesActorInstance.setReply(THING, twinResponse);
            underTest.tell(retrieveThing, getRef());
            TestSetup.fishForMsgClass(this, DistributedPubSubMediator.Publish.class);
            underTest.tell(ThingErrorResponse.of(retrieveThing.getEntityId(),
                    ThingIdInvalidException.newBuilder(retrieveThing.getEntityId())
                            .dittoHeaders(retrieveThing.getDittoHeaders().toBuilder().channel("live").build())
                            .build()), ActorRef.noSender());
            final var receivedError = expectMsgClass(ThingIdInvalidException.class);
            assertLiveChannel(receivedError);
        }};
    }

    @Test
    public void liveCommandErrorWithTwinFallback() {
        new TestKit(system) {{
            final ActorRef underTest = newEnforcerActor(getRef());
            final var retrieveThing = getRetrieveThing(headers -> headers.channel("live")
                    .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin"));
            final var twinResponse = getRetrieveThingResponse(retrieveThing, b -> {});
            mockEntitiesActorInstance.setReply(THING, twinResponse);
            underTest.tell(retrieveThing, getRef());
            TestSetup.fishForMsgClass(this, DistributedPubSubMediator.Publish.class);
            underTest.tell(ThingErrorResponse.of(retrieveThing.getEntityId(),
                    ThingIdInvalidException.newBuilder(retrieveThing.getEntityId())
                            .dittoHeaders(retrieveThing.getDittoHeaders().toBuilder().channel("live").build())
                            .build()), ActorRef.noSender());
            final var receivedError = expectMsgClass(ThingIdInvalidException.class);
            assertLiveChannel(receivedError);
        }};
    }

    private ActorRef newEnforcerActor(final ActorRef testActorRef) {
        return TestSetup.newEnforcerActor(system, testActorRef, mockEntitiesActor);
    }

    private DittoHeaders headers() {
        return DittoHeaders.newBuilder()
                .authorizationContext(
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, TestSetup.SUBJECT,
                                AuthorizationSubject.newInstance(String.format("%s:%s", GOOGLE, TestSetup.SUBJECT_ID))))
                .correlationId(testName.getMethodName())
                .schemaVersion(JsonSchemaVersion.V_2)
                .build();
    }

    private RetrieveThing getRetrieveThing(final Consumer<DittoHeadersBuilder<?, ?>> headerModifier) {
        final DittoHeadersBuilder<?, ?> builder = headers().toBuilder();
        headerModifier.accept(builder);
        return RetrieveThing.of(TestSetup.THING_ID, builder.build());
    }

    private RetrieveThingResponse getRetrieveThingResponse(final RetrieveThing retrieveThing,
            final Consumer<DittoHeadersBuilder<?, ?>> headerModifier) {
        final var builder = retrieveThing.getDittoHeaders()
                .toBuilder()
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(), "true");
        headerModifier.accept(builder);
        return RetrieveThingResponse.of(retrieveThing.getEntityId(), thing, builder.build());
    }

    private void assertLiveChannel(final Class<? extends WithDittoHeaders> clazz, final Object message) {
        assertThat(message).isInstanceOf(clazz);
        Assertions.assertThat(SignalInformationPoint.isChannelLive(clazz.cast(message)))
                .describedAs("Expect live channel: " + message)
                .isTrue();
    }

    private void assertLiveChannel(final WithDittoHeaders signal) {
        assertLiveChannel(WithDittoHeaders.class, signal);
    }
}
