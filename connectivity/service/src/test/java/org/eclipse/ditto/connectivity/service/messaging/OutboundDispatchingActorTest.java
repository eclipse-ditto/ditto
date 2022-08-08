/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Set;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotDeclaredException;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.connectivity.api.InboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link OutboundDispatchingActor}.
 */
public final class OutboundDispatchingActorTest {

    private ActorSystem actorSystem;
    private TestProbe proxyActor;

    @Before
    public void init() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), TestConstants.CONFIG);
        proxyActor = TestProbe.apply("proxy", actorSystem);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void testHandleSignalWithAcknowledgementRequest() {
        new TestKit(actorSystem) {{
            final ConnectionId connectionId = ConnectionId.of("testHandleSignalWithAcknowledgementRequest");
            final Connection connectionWithTestAck = TestConstants.createConnection()
                    .toBuilder()
                    .id(connectionId)
                    .sources(TestConstants.createConnection().getSources().stream()
                            .map(source -> ConnectivityModelFactory.newSourceBuilder(source)
                                    .declaredAcknowledgementLabels(Set.of(getTestAck(connectionId)))
                                    .build())
                            .toList())
                    .build();
            final TestProbe probe = TestProbe.apply("probe", actorSystem);
            final ActorRef underTest = getOutboundDispatchingActor(connectionWithTestAck, probe.ref());

            final AcknowledgementLabel acknowledgementLabel = getTestAck(connectionId);
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .acknowledgementRequest(AcknowledgementRequest.of(acknowledgementLabel))
                    .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), getRef().path().toSerializationFormat())
                    .readGrantedSubjects(Collections.singleton(TestConstants.Authorization.SUBJECT))
                    .timeout("2s")
                    .randomCorrelationId()
                    .build();

            final AttributeModified attributeModified = AttributeModified.of(
                    TestConstants.Things.THING_ID, JsonPointer.of("hello"), JsonValue.of("world!"), 5L,
                    null, dittoHeaders, null);
            underTest.tell(attributeModified, getRef());

            final OutboundSignal unmappedOutboundSignal = probe.expectMsgClass(OutboundSignal.class);
            assertThat(unmappedOutboundSignal.getSource()).isEqualTo(attributeModified);

            final Acknowledgement acknowledgement =
                    Acknowledgement.of(acknowledgementLabel, TestConstants.Things.THING_ID, HttpStatus.OK,
                            dittoHeaders);
            underTest.tell(InboundSignal.of(acknowledgement), getRef());

            expectMsg(acknowledgement);
        }};
    }

    @Test
    public void testHandleSignalWithPlaceholderDeclaredAcknowledgement() {
        new TestKit(actorSystem) {{
            final ConnectionId connectionId = ConnectionId.of("testHandleSignalWithPlaceholderDeclaredAcknowledgement");
            final Connection connectionWithPlaceholderAck = TestConstants.createConnection()
                    .toBuilder()
                    .id(connectionId)
                    .sources(TestConstants.createConnection().getSources().stream()
                            .map(source -> ConnectivityModelFactory.newSourceBuilder(source)
                                    .declaredAcknowledgementLabels(Set.of(getPlaceholderAck()))
                                    .build())
                            .toList())
                    .build();
            final TestProbe probe = TestProbe.apply("probe", actorSystem);
            final ActorRef underTest = getOutboundDispatchingActor(connectionWithPlaceholderAck, probe.ref());

            final AcknowledgementLabel acknowledgementLabel = getTestAck(connectionId);
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .acknowledgementRequest(AcknowledgementRequest.of(acknowledgementLabel))
                    .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), getRef().path().toSerializationFormat())
                    .readGrantedSubjects(Collections.singleton(TestConstants.Authorization.SUBJECT))
                    .timeout("2s")
                    .randomCorrelationId()
                    .build();

            final AttributeModified attributeModified = AttributeModified.of(
                    TestConstants.Things.THING_ID, JsonPointer.of("hello"), JsonValue.of("world!"), 5L, null,
                    dittoHeaders, null);
            underTest.tell(attributeModified, getRef());

            final OutboundSignal unmappedOutboundSignal = probe.expectMsgClass(OutboundSignal.class);
            assertThat(unmappedOutboundSignal.getSource()).isEqualTo(attributeModified);

            final Acknowledgement acknowledgement =
                    Acknowledgement.of(acknowledgementLabel, TestConstants.Things.THING_ID, HttpStatus.OK,
                            dittoHeaders);
            underTest.tell(InboundSignal.of(acknowledgement), getRef());

            expectMsg(acknowledgement);
        }};
    }

    @Test
    public void testTargetIssuedAcknowledgement() {
        new TestKit(actorSystem) {{
            final ConnectionId connectionId = ConnectionId.of("testTargetIssuedAcknowledgement");
            final Connection connectionWithTargetIssuedAck = TestConstants.createConnection()
                    .toBuilder()
                    .id(connectionId)
                    .targets(TestConstants.createConnection().getTargets().stream()
                            .map(target -> {
                                if (target.getTopics().stream()
                                        .anyMatch(ft -> ft.getTopic().equals(Topic.LIVE_EVENTS))) {
                                    return ConnectivityModelFactory.newTargetBuilder(target)
                                            .issuedAcknowledgementLabel(getPlaceholderAck())
                                            .build();
                                } else {
                                    return target;
                                }
                            })
                            .toList())
                    .build();
            final TestProbe probe = TestProbe.apply("probe", actorSystem);
            final ActorRef underTest = getOutboundDispatchingActor(connectionWithTargetIssuedAck, probe.ref());

            final AcknowledgementLabel acknowledgementLabel = getTestAck(connectionId);
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .acknowledgementRequest(AcknowledgementRequest.of(acknowledgementLabel))
                    .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), getRef().path().toSerializationFormat())
                    .readGrantedSubjects(Collections.singleton(TestConstants.Authorization.SUBJECT))
                    .timeout("2s")
                    .randomCorrelationId()
                    .build();

            // WHEN: connection persistence actor receives event with matching acknowledgement request
            final AttributeModified attributeModified = AttributeModified.of(
                    TestConstants.Things.THING_ID, JsonPointer.of("hello"), JsonValue.of("world!"), 5L, null,
                    dittoHeaders, null);
            underTest.tell(attributeModified, getRef());

            // THEN: then event is forwarded
            final OutboundSignal unmappedOutboundSignal = probe.expectMsgClass(OutboundSignal.class);
            assertThat(unmappedOutboundSignal.getSource()).isEqualTo(attributeModified);

            // WHEN: an acknowledgement of matching correlation ID is sent to the connection actor, which should not
            // happen as target-issued acks bypass the connection persistence actor
            final Acknowledgement acknowledgement =
                    Acknowledgement.of(acknowledgementLabel, TestConstants.Things.THING_ID, HttpStatus.OK,
                            dittoHeaders);
            underTest.tell(InboundSignal.of(acknowledgement), getRef());

            // THEN: an error is received
            expectMsgClass(AcknowledgementLabelNotDeclaredException.class);
        }};
    }

    @Test
    public void sendAckWithNotDeclaredLabel() {
        new TestKit(actorSystem) {{
            final ConnectionId connectionId = ConnectionId.of("sendAckWithNotDeclaredLabel");
            final Connection connectionWithTestAck = TestConstants.createConnection()
                    .toBuilder()
                    .id(connectionId)
                    .sources(TestConstants.createConnection().getSources().stream()
                            .map(source -> ConnectivityModelFactory.newSourceBuilder(source)
                                    .declaredAcknowledgementLabels(Set.of(getTestAck(connectionId)))
                                    .build())
                            .toList())
                    .build();
            final TestProbe probe = TestProbe.apply("probe", actorSystem);
            final ActorRef underTest = getOutboundDispatchingActor(connectionWithTestAck, probe.ref());

            // WHEN: signal is received with not-declared acknowledgement request
            final AcknowledgementLabel acknowledgementLabel = AcknowledgementLabel.of(connectionId + ":not-declared");
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .acknowledgementRequest(AcknowledgementRequest.of(acknowledgementLabel))
                    .readGrantedSubjects(Collections.singleton(TestConstants.Authorization.SUBJECT))
                    .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), getRef().path().toSerializationFormat())
                    .timeout("2s")
                    .randomCorrelationId()
                    .build();

            final AttributeModified attributeModified = AttributeModified.of(
                    TestConstants.Things.THING_ID, JsonPointer.of("hello"), JsonValue.of("world!"), 5L, null,
                    dittoHeaders, null);
            underTest.tell(attributeModified, getRef());

            // THEN: the acknowledgement request is removed from the signal
            final OutboundSignal eventOutboundSignal = probe.expectMsgClass(OutboundSignal.class);
            final AttributeModified expectedEvent = attributeModified.setDittoHeaders(dittoHeaders.toBuilder()
                    .acknowledgementRequests(Set.of())
                    .build());
            assertThat(eventOutboundSignal.getSource()).isEqualTo(expectedEvent);

            // THEN: acknowledgements of non-declared labels are answered by AcknowledgementLabelNotDeclaredException
            final Acknowledgement acknowledgement =
                    Acknowledgement.of(acknowledgementLabel, TestConstants.Things.THING_ID, HttpStatus.OK,
                            dittoHeaders);
            underTest.tell(InboundSignal.of(acknowledgement), getRef());
            expectMsg(AcknowledgementLabelNotDeclaredException.of(acknowledgementLabel, dittoHeaders));
        }};
    }

    @Test
    public void testThingEventWithAuthorizedSubjectExpectIsForwarded() {
        final Set<AuthorizationSubject> valid = Collections.singleton(TestConstants.Authorization.SUBJECT);
        testForwardThingEvent(TestConstants.createConnection(), true,
                TestConstants.thingModified(valid),
                TestConstants.Targets.TWIN_TARGET);
    }

    @Test
    public void testThingEventIsForwardedToFilteredTarget() {
        final ConnectionId connectionId = ConnectionId.of("testThingEventIsForwardedToFilteredTarget");
        final Connection connection =
                TestConstants.createConnection(connectionId, TestConstants.Targets.TARGET_WITH_PLACEHOLDER);
        final Signal<?> thingModified =
                TestConstants.thingModified(Collections.singleton(TestConstants.Authorization.SUBJECT));

        // expect that address is still with placeholders (as replacement was moved to MessageMappingProcessorActor
        final Target expectedTarget = ConnectivityModelFactory.newTarget(TestConstants.Targets.TARGET_WITH_PLACEHOLDER,
                TestConstants.Targets.TARGET_WITH_PLACEHOLDER.getAddress(), null);

        testForwardThingEvent(connection, true, thingModified, expectedTarget);
    }

    @Test
    public void testThingEventWithUnauthorizedSubjectExpectIsNotForwarded() {
        final Set<AuthorizationSubject> invalid =
                Collections.singleton(AuthorizationModelFactory.newAuthSubject("iot:user"));
        testForwardThingEvent(TestConstants.createConnection(), false, TestConstants.thingModified(invalid),
                TestConstants.Targets.TWIN_TARGET);
    }

    @Test
    public void testLiveMessageWithAuthorizedSubjectExpectIsNotForwarded() {
        final Set<AuthorizationSubject> valid = Collections.singleton(TestConstants.Authorization.SUBJECT);
        testForwardThingEvent(TestConstants.createConnection(), false, TestConstants.sendThingMessage(valid),
                TestConstants.Targets.TWIN_TARGET);
    }

    @Test
    public void testHandleLiveResponse() {
        new TestKit(actorSystem) {{
            final ConnectionId connectionId = ConnectionId.of("testHandleSignalWithAcknowledgementRequest");
            final Connection connectionWithTestAck = TestConstants.createConnection()
                    .toBuilder()
                    .id(connectionId)
                    .sources(TestConstants.createConnection().getSources().stream()
                            .map(source -> ConnectivityModelFactory.newSourceBuilder(source)
                                    .declaredAcknowledgementLabels(Set.of(getTestAck(connectionId)))
                                    .build())
                            .toList())
                    .build();
            final TestProbe probe = TestProbe.apply("probe", actorSystem);
            final ActorRef underTest = getOutboundDispatchingActor(connectionWithTestAck, probe.ref());

            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .channel(TopicPath.Channel.LIVE.getName())
                    .readGrantedSubjects(Collections.singleton(TestConstants.Authorization.SUBJECT))
                    .timeout("2s")
                    .randomCorrelationId()
                    .build();

            final RetrieveThingResponse retrieveThingResponse =
                    RetrieveThingResponse.of(TestConstants.Things.THING_ID, TestConstants.Things.THING, null,
                            null, dittoHeaders);

            underTest.tell(InboundSignal.of(retrieveThingResponse), getRef());

            final RetrieveThingResponse forwardedResponse = proxyActor.expectMsgClass(RetrieveThingResponse.class);
            assertThat(forwardedResponse).isEqualTo(retrieveThingResponse);
        }};
    }

    private void testForwardThingEvent(final Connection connection, final boolean isForwarded, final Signal<?> signal,
            final Target expectedTarget) {

        new TestKit(actorSystem) {{
            final TestProbe proxyActor = TestProbe.apply("proxy", actorSystem);
            final ActorSelection proxyActorSelection = ActorSelection.apply(proxyActor.ref(), "");
            final TestProbe mappingActor = TestProbe.apply("mapping", actorSystem);

            final OutboundMappingSettings settings =
                    OutboundMappingSettings.of(connection, TestConstants.CONNECTIVITY_CONFIG, actorSystem,
                            proxyActorSelection,
                            DittoProtocolAdapter.newInstance(),
                            MockActor.getThreadSafeDittoLoggingAdapter(actorSystem));
            final ActorRef underTest =
                    childActorOf(OutboundDispatchingActor.props(settings, mappingActor.ref()));

            underTest.tell(signal, getRef());

            if (isForwarded) {
                final OutboundSignal unmappedOutboundSignal =
                        mappingActor.expectMsgClass(OutboundSignal.class);
                assertThat(unmappedOutboundSignal.getSource()).isEqualTo(signal);

                // check target address only due to live migration
                assertThat(unmappedOutboundSignal.getTargets().stream().map(Target::getAddress))
                        .containsExactly(expectedTarget.getAddress());
            } else {
                mappingActor.expectNoMessage();
            }
        }};
    }

    private ActorRef getOutboundDispatchingActor(final Connection connection, final ActorRef mappingActor) {
        final ActorSelection proxyActorSelection = ActorSelection.apply(proxyActor.ref(), "");

        final OutboundMappingSettings settings =
                OutboundMappingSettings.of(connection, TestConstants.CONNECTIVITY_CONFIG, actorSystem,
                        proxyActorSelection,
                        DittoProtocolAdapter.newInstance(), MockActor.getThreadSafeDittoLoggingAdapter(actorSystem));
        return actorSystem.actorOf(OutboundDispatchingActor.props(settings, mappingActor));
    }

    private static AcknowledgementLabel getTestAck(final ConnectionId connectionId) {
        return AcknowledgementLabel.of(connectionId + ":test-ack");
    }

    private static AcknowledgementLabel getPlaceholderAck() {
        return AcknowledgementLabel.of("{{connection:id}}:test-ack");
    }

}
