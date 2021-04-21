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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests in addition to {@link org.eclipse.ditto.services.connectivity.messaging.MessageMappingProcessorActorTest}
 * for {@link org.eclipse.ditto.services.connectivity.messaging.OutboundMappingProcessorActor} only.
 */
public final class OutboundMappingProcessorActorTest {

    private static final Connection CONNECTION = createTestConnection();

    private ActorSystem actorSystem;
    private ProtocolAdapterProvider protocolAdapterProvider;
    private TestProbe clientActorProbe;
    private TestProbe proxyActorProbe;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        protocolAdapterProvider = ProtocolAdapterProvider.load(TestConstants.PROTOCOL_CONFIG, actorSystem);
        clientActorProbe = TestProbe.apply("clientActor", actorSystem);
        proxyActorProbe = TestProbe.apply("proxyActor", actorSystem);
        MockProxyActor.create(actorSystem, proxyActorProbe.ref());
        proxyActorProbe.expectMsgClass(ActorRef.class).tell(proxyActorProbe.ref(), proxyActorProbe.ref());
        proxyActorProbe.expectMsgClass(ActorRef.class);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    public void sendWeakAckForAllSourcesAndTargetsWhenDroppedByAllTargets() {
        new TestKit(actorSystem) {{
            final Props props =
                    OutboundMappingProcessorActor.props(clientActorProbe.ref(), getProcessor(), CONNECTION, 3);
            final ActorRef underTest = actorSystem.actorOf(props);

            // WHEN: mapping processor actor receives outbound signal whose every authorized target is filtered out
            final OutboundSignal outboundSignal = outboundTwinEvent(
                    Attributes.newBuilder().build(),
                    List.of("source1", "target1", "target2", "unknown"),
                    List.of(target2()) // authorized target2 will drop the signal after filtering
            );
            underTest.tell(outboundSignal, getRef());
            proxyActorProbe.expectMsgClass(RetrieveThing.class);
            proxyActorProbe.reply(retrieveThingResponse(Attributes.newBuilder().build()));

            // THEN: sender receives weak acknowledgements for all source declared and target issued acknowledgements
            final Acknowledgements acks = expectMsgClass(Acknowledgements.class);
            final List<String> ackLabels = acks.getSuccessfulAcknowledgements()
                    .stream()
                    .map(ack -> ack.getLabel().toString())
                    .collect(Collectors.toList());
            assertThat(ackLabels).containsExactlyInAnyOrder("source1", "target1", "target2");
            acks.forEach(ack -> assertThat(ack.isWeak()).describedAs("Expect weak ack, got: " + ack).isTrue());
        }};
    }

    @Test
    public void sendWeakAckWhenDroppedBySomeTarget() {
        new TestKit(actorSystem) {{
            final Props props =
                    OutboundMappingProcessorActor.props(clientActorProbe.ref(), getProcessor(), CONNECTION, 3);
            final ActorRef underTest = actorSystem.actorOf(props);

            // WHEN: mapping processor actor receives outbound signal with 2 authorized targets,
            // 1 of which drops it via RQL filter after enrichment
            final OutboundSignal outboundSignal = outboundTwinEvent(
                    Attributes.newBuilder().build(),
                    List.of("source1", "target1", "target2", "unknown"),
                    List.of(target1(), target2())
            );
            underTest.tell(outboundSignal, getRef());
            proxyActorProbe.expectMsgClass(RetrieveThing.class);
            proxyActorProbe.reply(retrieveThingResponse(Attributes.newBuilder().build()));

            // THEN: sender receives weak acknowledgement only for the target that dropped it
            final Acknowledgements acks = expectMsgClass(Acknowledgements.class);
            final List<String> ackLabels = acks.getSuccessfulAcknowledgements()
                    .stream()
                    .map(ack -> ack.getLabel().toString())
                    .collect(Collectors.toList());
            assertThat(ackLabels).containsExactlyInAnyOrder("target2");
            acks.forEach(ack -> assertThat(ack.isWeak()).describedAs("Expect weak ack, got: " + ack).isTrue());
        }};
    }

    @Test
    public void sendWeakAckWhenDroppedByMapper() {
        new TestKit(actorSystem) {{
            final Props props =
                    OutboundMappingProcessorActor.props(clientActorProbe.ref(), getProcessor(), CONNECTION, 3);
            final ActorRef underTest = actorSystem.actorOf(props);

            // WHEN: mapping processor actor receives outbound signal with 2 authorized targets,
            // 1 of which drops it via payload mapping
            final OutboundSignal outboundSignal = outboundTwinEvent(
                    Attributes.newBuilder().build(),
                    List.of("source1", "target1", "target3"),
                    List.of(target1(), target3())
            );
            underTest.tell(outboundSignal, getRef());

            // THEN: sender receives weak acknowledgement only for the target that dropped it
            final Acknowledgements acks = expectMsgClass(Acknowledgements.class);
            final List<String> ackLabels = acks.getSuccessfulAcknowledgements()
                    .stream()
                    .map(ack -> ack.getLabel().toString())
                    .collect(Collectors.toList());
            assertThat(ackLabels).containsExactlyInAnyOrder("target3");
            acks.forEach(ack -> assertThat(ack.isWeak()).describedAs("Expect weak ack, got: " + ack).isTrue());
        }};
    }

    @Test
    public void doNotSendWeakAckForLiveResponse() {
        new TestKit(actorSystem) {{
            final Props props =
                    OutboundMappingProcessorActor.props(clientActorProbe.ref(), getProcessor(), CONNECTION, 3);
            final ActorRef underTest = actorSystem.actorOf(props);

            // WHEN: mapping processor actor receives outbound signal with 3 authorized targets,
            // 2 of which drops it via payload mapping, 1 of which issues live-response
            final OutboundSignal outboundSignal = outboundLiveEvent(
                    Attributes.newBuilder().build(),
                    List.of("source1", "target1", "target3", "live-response"),
                    List.of(target1(), target3(), target4())
            );
            underTest.tell(outboundSignal, getRef());

            // THEN: sender does not receive a weak acknowledgement for live-response
            final Acknowledgements acks = expectMsgClass(Acknowledgements.class);
            final List<String> ackLabels = acks.getSuccessfulAcknowledgements()
                    .stream()
                    .map(ack -> ack.getLabel().toString())
                    .collect(Collectors.toList());
            assertThat(ackLabels).containsExactlyInAnyOrder("target3");
        }};
    }

    @Test
    public void expectNoTargetIssuedAckRequestInPublishedSignals() {
        new TestKit(actorSystem) {{
            final Props props =
                    OutboundMappingProcessorActor.props(clientActorProbe.ref(), getProcessor(), CONNECTION, 3);
            final ActorRef underTest = actorSystem.actorOf(props);

            // WHEN: mapping processor actor receives outbound signal
            // with requests for source-declared and target-issued acks
            final OutboundSignal outboundSignal = outboundTwinEvent(
                    Attributes.newBuilder().build(),
                    List.of("source1", "target1", "target2"),
                    List.of(target1())
            );
            underTest.tell(outboundSignal, getRef());

            // THEN: only the source-declared ack is present in the published signal
            final BaseClientActor.PublishMappedMessage publish =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publish.getOutboundSignal().first().getAdaptable().getDittoHeaders()
                    .getAcknowledgementRequests())
                    .containsExactly(AcknowledgementRequest.parseAcknowledgementRequest("source1"));
        }};
    }

    private OutboundMappingProcessor getProcessor() {
        return OutboundMappingProcessor.of(CONNECTION, actorSystem, TestConstants.CONNECTIVITY_CONFIG,
                protocolAdapterProvider.getProtocolAdapter("test"),
                AbstractMessageMappingProcessorActorTest.mockLoggingAdapter());
    }

    private static OutboundSignal outboundTwinEvent(final Attributes attributes, final Collection<String> requestedAcks,
            final List<Target> targets) {
        final Thing thing = Thing.newBuilder().setId(thingId())
                .setAttributes(attributes)
                .build();
        final ThingModified thingModified = ThingModified.of(thing, 2L, Instant.EPOCH, DittoHeaders.newBuilder()
                        .acknowledgementRequests(requestedAcks.stream()
                                .map(AcknowledgementRequest::parseAcknowledgementRequest)
                                .collect(Collectors.toList()))
                        .build(),
                Metadata.newMetadata(JsonObject.empty()));
        return OutboundSignalFactory.newOutboundSignal(thingModified, targets);
    }

    private static OutboundSignal outboundLiveEvent(final Attributes attributes, final Collection<String> requestedAcks,
            final List<Target> targets) {
        final OutboundSignal outboundTwinEvent = outboundTwinEvent(attributes, requestedAcks, targets);
        return OutboundSignalFactory.newOutboundSignal(
                outboundTwinEvent.getSource()
                        .setDittoHeaders(outboundTwinEvent.getSource().getDittoHeaders().toBuilder()
                                .channel(TopicPath.Channel.LIVE.getName())
                                .build()),
                outboundTwinEvent.getTargets());
    }

    private static Connection createTestConnection() {
        final ConnectionType type = ConnectionType.MQTT_5;
        final ConnectivityStatus status = ConnectivityStatus.OPEN;
        final String uri = "tcp://localhost:1883";
        return ConnectivityModelFactory.newConnectionBuilder(connectionId(), type, status, uri)
                .setSources(List.of(createTestSource()))
                .setTargets(List.of(target1(), target2(), target3(), target4()))
                .payloadMappingDefinition(ConnectivityModelFactory.newPayloadMappingDefinition(Map.of(
                        "javascript",
                        ConnectivityModelFactory.newMappingContext("JavaScript", Map.of(
                                "incomingScript", "function mapToDittoProtocolMsg() { return null; }",
                                "outgoingScript", "function mapFromDittoProtocolMsg() { return null; }"
                        ))
                )))
                .build();
    }

    private static Source createTestSource() {
        return ConnectivityModelFactory.newSourceBuilder()
                .address("source1")
                .authorizationContext(singletonContext(source1Subject()))
                .declaredAcknowledgementLabels(Set.of(AcknowledgementLabel.of("source1")))
                .build();
    }

    private static Target target1() {
        return ConnectivityModelFactory.newTargetBuilder()
                .address("target1")
                .authorizationContext(singletonContext(target1Subject()))
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS).build(),
                        ConnectivityModelFactory.newFilteredTopicBuilder(Topic.LIVE_EVENTS).build())
                .issuedAcknowledgementLabel(AcknowledgementLabel.of("target1"))
                .build();
    }

    private static Target target2() {
        return ConnectivityModelFactory.newTargetBuilder()
                .address("target2")
                .authorizationContext(singletonContext(target2Subject()))
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                                .withFilter("exists(attributes/target2)")
                                .withExtraFields(ThingFieldSelector.fromString("attributes"))
                                .build(),
                        ConnectivityModelFactory.newFilteredTopicBuilder(Topic.LIVE_EVENTS)
                                .withFilter("exists(attributes/target2)")
                                .withExtraFields(ThingFieldSelector.fromString("attributes"))
                                .build())
                .issuedAcknowledgementLabel(AcknowledgementLabel.of("target2"))
                .build();
    }

    private static Target target3() {
        return ConnectivityModelFactory.newTargetBuilder()
                .address("target3")
                .authorizationContext(singletonContext(target2Subject()))
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS).build(),
                        ConnectivityModelFactory.newFilteredTopicBuilder(Topic.LIVE_EVENTS).build())
                .issuedAcknowledgementLabel(AcknowledgementLabel.of("target3"))
                .payloadMapping(ConnectivityModelFactory.newPayloadMapping("javascript"))
                .build();
    }

    private static Target target4() {
        return ConnectivityModelFactory.newTargetBuilder()
                .address("target4")
                .authorizationContext(singletonContext(target2Subject()))
                .topics(ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS).build(),
                        ConnectivityModelFactory.newFilteredTopicBuilder(Topic.LIVE_EVENTS).build())
                .issuedAcknowledgementLabel(AcknowledgementLabel.of("live-response"))
                .payloadMapping(ConnectivityModelFactory.newPayloadMapping("javascript"))
                .build();
    }

    private static AuthorizationContext singletonContext(final AuthorizationSubject subject) {
        return AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, subject);
    }

    private static ConnectionId connectionId() {
        return ConnectionId.of("cid");
    }

    private static AuthorizationSubject source1Subject() {
        return AuthorizationSubject.newInstance("source:1");
    }

    private static AuthorizationSubject target1Subject() {
        return AuthorizationSubject.newInstance("target:1");
    }

    private static AuthorizationSubject target2Subject() {
        return AuthorizationSubject.newInstance("target:1");
    }

    private static ThingId thingId() {
        return ThingId.of("thing:id");
    }

    private static RetrieveThingResponse retrieveThingResponse(final Attributes attributes) {
        return RetrieveThingResponse.of(thingId(),
                Thing.newBuilder().setId(thingId()).setAttributes(attributes).build(), null, null,
                DittoHeaders.empty());
    }
}
