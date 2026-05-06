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

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.FilteredTopic;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.internal.utils.pekko.ActorSystemResource;
import org.eclipse.ditto.internal.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.events.FeatureModified;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.jspecify.annotations.NonNull;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Tests in addition to {@link MessageMappingProcessorActorTest}
 * for {@link OutboundMappingProcessorActor} only.
 */
@FixMethodOrder(MethodSorters.DEFAULT)
public final class OutboundMappingProcessorActorTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final Connection CONNECTION = createTestConnection();
    public static final Thing THING = createTestThing();

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance(
            getClass().getSimpleName(),
            TestConstants.CONFIG
    );

    private ProtocolAdapterProvider protocolAdapterProvider;
    private TestProbe clientActorProbe;
    private TestProbe proxyActorProbe;

    @Before
    public void setUp() {
        protocolAdapterProvider =
                ProtocolAdapterProvider.load(TestConstants.PROTOCOL_CONFIG, actorSystemResource.getActorSystem());
        clientActorProbe = actorSystemResource.newTestProbe("clientActor");
        proxyActorProbe = actorSystemResource.newTestProbe("proxyActor");
        MockCommandForwarder.create(actorSystemResource.getActorSystem(), proxyActorProbe.ref());
        proxyActorProbe.expectMsgClass(ActorRef.class).tell(proxyActorProbe.ref(), proxyActorProbe.ref());
        proxyActorProbe.expectMsgClass(ActorRef.class);
    }

    @Test
    public void sendWeakAckForAllSourcesAndTargetsWhenDroppedByAllTargets() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            final ActorRef underTest = getTestActorRef(CONNECTION);

            // WHEN: mapping processor actor receives outbound signal whose every authorized target is filtered out
            final OutboundSignal outboundSignal = outboundTwinEvent(
                    Attributes.newBuilder().build(),
                    List.of("source1", "target1", "target2", "unknown"),
                    List.of(target2()), // authorized target2 will drop the signal after filtering
                    getRef());
            underTest.tell(outboundSignal, getRef());
            proxyActorProbe.expectMsgClass(RetrieveThing.class);
            proxyActorProbe.reply(retrieveThingResponse(Attributes.newBuilder().build()));

            // THEN: sender receives weak acknowledgements for all source declared and target issued acknowledgements
            final Acknowledgements acks = expectMsgClass(Acknowledgements.class);
            final List<String> ackLabels = acks.getSuccessfulAcknowledgements()
                    .stream()
                    .map(ack -> ack.getLabel().toString())
                    .toList();
            assertThat(ackLabels).containsExactlyInAnyOrder("source1", "target1", "target2");
            acks.forEach(ack -> assertThat(ack.isWeak()).describedAs("Expect weak ack, got: " + ack).isTrue());
        }};
    }

    @Test
    public void eventsWithFailedEnrichmentIssueFailedAcks() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            final ActorRef underTest = getTestActorRef(CONNECTION);

            final OutboundSignal outboundSignal = outboundTwinEvent(
                    Attributes.newBuilder().set("target2", "wayne").build(),
                    List.of("source1", "target1", "target2", "unknown"),
                    List.of(target1(), target2()),
                    getRef());
            underTest.tell(outboundSignal, getRef());
            proxyActorProbe.expectMsgClass(RetrieveThing.class);

            final Acknowledgements acks = expectMsgClass(Duration.ofSeconds(5), Acknowledgements.class);
            final List<String> fackLabels = acks.getFailedAcknowledgements()
                    .stream()
                    .map(ack -> ack.getLabel().toString())
                    .toList();
            assertThat(fackLabels).containsExactlyInAnyOrder("target2");
        }};
    }

    @Test
    public void sendWeakAckWhenDroppedBySomeTarget() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            final ActorRef underTest = getTestActorRef(CONNECTION);

            // WHEN: mapping processor actor receives outbound signal with 2 authorized targets,
            // 1 of which drops it via RQL filter after enrichment
            final OutboundSignal outboundSignal = outboundTwinEvent(
                    Attributes.newBuilder().build(),
                    List.of("source1", "target1", "target2", "unknown"),
                    List.of(target1(), target2()),
                    getRef());
            underTest.tell(outboundSignal, getRef());
            proxyActorProbe.expectMsgClass(RetrieveThing.class);
            proxyActorProbe.reply(retrieveThingResponse(Attributes.newBuilder().build()));

            // THEN: sender receives weak acknowledgement only for the target that dropped it
            final Acknowledgements acks = expectMsgClass(Acknowledgements.class);
            final List<String> ackLabels = acks.getSuccessfulAcknowledgements()
                    .stream()
                    .map(ack -> ack.getLabel().toString())
                    .toList();
            assertThat(ackLabels).containsExactlyInAnyOrder("target2");
            acks.forEach(ack -> assertThat(ack.isWeak()).describedAs("Expect weak ack, got: " + ack).isTrue());
        }};
    }

    @Test
    public void sendWeakAckWhenDroppedByMapper() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            final ActorRef underTest = getTestActorRef(CONNECTION);

            // WHEN: mapping processor actor receives outbound signal with 2 authorized targets,
            // 1 of which drops it via payload mapping
            final OutboundSignal outboundSignal = outboundTwinEvent(
                    Attributes.newBuilder().build(),
                    List.of("source1", "target1", "target3"),
                    List.of(target1(), target3()),
                    getRef());
            underTest.tell(outboundSignal, getRef());

            // THEN: sender receives weak acknowledgement only for the target that dropped it
            final Acknowledgements acks = expectMsgClass(Acknowledgements.class);
            final List<String> ackLabels = acks.getSuccessfulAcknowledgements()
                    .stream()
                    .map(ack -> ack.getLabel().toString())
                    .toList();
            assertThat(ackLabels).containsExactlyInAnyOrder("target3");
            acks.forEach(ack -> assertThat(ack.isWeak()).describedAs("Expect weak ack, got: " + ack).isTrue());
        }};
    }

    @Test
    public void doNotSendWeakAckForLiveResponse() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            final ActorRef underTest = getTestActorRef(CONNECTION);

            // WHEN: mapping processor actor receives outbound signal with 3 authorized targets,
            // 2 of which drops it via payload mapping, 1 of which issues live-response
            final OutboundSignal outboundSignal = outboundLiveEvent(
                    Attributes.newBuilder().build(),
                    List.of("source1", "target1", "target3", "live-response"),
                    List.of(target1(), target3(), target4()),
                    getRef()
            );
            underTest.tell(outboundSignal, getRef());

            // THEN: sender does not receive a weak acknowledgement for live-response
            final Acknowledgements acks = expectMsgClass(Acknowledgements.class);
            final List<String> ackLabels = acks.getSuccessfulAcknowledgements()
                    .stream()
                    .map(ack -> ack.getLabel().toString())
                    .toList();
            assertThat(ackLabels).containsExactlyInAnyOrder("target3");
        }};
    }

    @Test
    public void expectNoTargetIssuedAckRequestInPublishedSignals() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            final ActorRef underTest = getTestActorRef(CONNECTION);

            // WHEN: mapping processor actor receives outbound signal
            // with requests for source-declared and target-issued acks
            final OutboundSignal outboundSignal = outboundTwinEvent(
                    Attributes.newBuilder().build(),
                    List.of("source1", "target1", "target2"),
                    List.of(target1()),
                    getRef());
            underTest.tell(outboundSignal, getRef());

            // THEN: only the source-declared ack is present in the published signal
            final BaseClientActor.PublishMappedMessage publish =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publish.getOutboundSignal().first().getAdaptable().getDittoHeaders()
                    .getAcknowledgementRequests())
                    .containsExactly(AcknowledgementRequest.parseAcknowledgementRequest("source1"));
        }};
    }

    private List<OutboundMappingProcessor> getProcessors(Connection connection) {
        return List.of(
                OutboundMappingProcessor.of(connection,
                        TestConstants.CONNECTIVITY_CONFIG, actorSystemResource.getActorSystem(),
                        protocolAdapterProvider.getProtocolAdapter("test"),
                        AbstractMessageMappingProcessorActorTest.mockLoggingAdapter(), null)
        );
    }

    @Test
    public void multipleTopicsWithExtraFieldsFirstTopic() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Create a target with multiple FilteredTopics for the same streaming type with different extraFields
            List <Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic4(), topic3(), topic5())));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            // Now test a signal that should match the second subscription (no extraFields)
            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("feature4")
                            .build().setProperties(FeatureProperties.newBuilder().set("size", "large").build()),
                    List.of("multipleExtraFields"), targets, getRef());

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            // Expect a publish message with enriched signal
            final BaseClientActor.PublishMappedMessage publishWithPolicy =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            // Verify this is for the target with extraFields
            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.get(0));

            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra -> {
                        assertThat(extra.contains(JsonKey.of("definition")))
                                .as("Outbound signal does not contain the requested extra fields.")
                                .isTrue();
                        assertThat(extra.contains(JsonKey.of("attributes")))
                                .as("Redundant extra fields exists, which must not exist.")
                                .isFalse();
                    });
        }};
    }

    @Test
    public void multipleTopicsWithExtraFieldsMidTopic() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Create a target with multiple FilteredTopics for the same streaming type with different extraFields
            List <Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic4(), topic3(), topic5())));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            // Now test a signal that should match the second subscription (no extraFields)
            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("feature3")
                            .build().setProperties(FeatureProperties.newBuilder().set("level", "full").build()),
                    List.of("multipleExtraFields"), targets, getRef());

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            // Expect a publish message with enriched signal
            final BaseClientActor.PublishMappedMessage publishWithPolicy =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            // Verify this is for the target with extraFields
            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.get(0));

            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .as("Outbound signal must not contain any extra field.")
                    .isEmpty();
        }};
    }

    @Test
    public void multipleTopicsWithExtraFieldsLastTopic() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Create a target with multiple FilteredTopics for the same streaming type with different extraFields
            List <Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic4(), topic3(), topic5())));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("feature5")
                            .build().setProperties(FeatureProperties.newBuilder().set("level", "full").build()),
                    List.of("multipleExtraFields"), targets, getRef());

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            final BaseClientActor.PublishMappedMessage publishWithPolicy =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.get(0));

            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra -> {
                        assertThat(extra.contains(JsonKey.of("attributes")))
                                 .as("Outbound signal does not contain the requested extra fields.")
                                .isTrue();
                        assertThat(extra.contains(JsonKey.of("definition")))
                                .as("Redundant extra fields exists, which must not exist.")
                                .isFalse();
                    });
        }};
    }

    @Test
    public void multipleTopicsWithoutExtraFieldsFastProcessed() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Create a target with multiple topics all without extra fields.
            // Must send outbound signal, skipping filtering as the pe-filtering has already done the job
            List <Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic3(), topic3a())));
            final Connection connection = CONNECTION.toBuilder().targets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("water-tank").build(),
                    List.of("multipleWithoutExtraFields"), targets, getRef());
            underTest.tell(outboundSignal, getRef());

            final BaseClientActor.PublishMappedMessage publishWithPolicy =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.get(0));

            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isEmpty();
        }};
    }

    @Test
    public void multipleTopicsExtraFieldsFilterless() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Create a target with multiple topics with some extra fields, but with a topic without a filter
            // Must send outbound signal, skipping filtering but enriching with the requested extra fields
            List <Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic3(), topic5(), topic2())));
            final Connection connection = CONNECTION.toBuilder().targets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("some-feature").build(),
                    List.of("multipleWithExtraFields"), targets, getRef());
            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            final BaseClientActor.PublishMappedMessage publishWithPolicy =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.get(0));

            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra -> assertThat(extra.contains(JsonKey.of("definition")))
                            .as("Outbound signal does not contain the requested extra fields.")
                            .isTrue());

        }};
    }

    private void partialRetrieveAndResponse() {
        // Expect enrichment request for all fields in all topics
        final RetrieveThing retrieveEnrichedThing = proxyActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat(retrieveEnrichedThing.getSelectedFields())
                .isPresent()
                .hasValueSatisfying(fields ->
                        assertThat(fields.getPointers())
                                .contains(JsonPointer.of("/attributes"), JsonPointer.of("/definition")));

        // Reply with enriched Thing
        final Thing enrichedThing = Thing.newBuilder()
                .setAttributes(Attributes.newBuilder().set("attr1", "attrValue1").build())
                .setDefinition(ThingsModelFactory.newDefinition("testNamespace:TestDefinition:1.2.3"))
                .build();
        proxyActorProbe.reply(RetrieveThingResponse.of(thingId(), enrichedThing, null, null, DittoHeaders.empty()));
    }

    private ActorRef getTestActorRef(Connection connection) {
        final Props props = OutboundMappingProcessorActor.props(clientActorProbe.ref(),
                getProcessors(connection),
                connection,
                TestConstants.CONNECTIVITY_CONFIG,
                3);
        return actorSystemResource.newActor(props);
    }

    private static OutboundSignal outboundFeatureTwinEvent(final Thing thing, final Feature feature, final Collection<String> requestedAcks,
            final List<Target> targets, final ActorRef testRef) {
        final List<AuthorizationSubject> readGrantedSubjects = targets.stream()
                .map(Target::getAuthorizationContext)
                .flatMap(authContext -> authContext.getAuthorizationSubjects().stream())
                .distinct()
                .toList();

        final FeatureModified featureModified = FeatureModified.of(thing.getEntityId().get(), feature, 2L, Instant.EPOCH,
                DittoHeaders.newBuilder()
                        .acknowledgementRequests(requestedAcks.stream()
                                .map(AcknowledgementRequest::parseAcknowledgementRequest)
                                .toList())
                        .readGrantedSubjects(readGrantedSubjects)
                        .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                                testRef.path().toSerializationFormat())
                        .build(),
                Metadata.newMetadata(JsonObject.empty()));
        return OutboundSignalFactory.newOutboundSignal(featureModified, targets);
    }

    private static OutboundSignal outboundTwinEvent(final Attributes attributes, final Collection<String> requestedAcks,
            final List<Target> targets, final ActorRef testRef) {
        final Thing thing = Thing.newBuilder().setId(thingId())
                .setAttributes(attributes)
                .setPolicyId(PolicyId.of("test1:policy1"))
                .build();
        final List<AuthorizationSubject> readGrantedSubjects = targets.stream()
                .map(Target::getAuthorizationContext)
                .flatMap(authContext -> authContext.getAuthorizationSubjects().stream())
                .distinct()
                .toList();
        final ThingModified thingModified = ThingModified.of(thing, 2L, Instant.EPOCH, DittoHeaders.newBuilder()
                        .acknowledgementRequests(requestedAcks.stream()
                                .map(AcknowledgementRequest::parseAcknowledgementRequest)
                                .toList())
                        .readGrantedSubjects(readGrantedSubjects)
                        .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                                testRef.path().toSerializationFormat())
                        .build(),
                Metadata.newMetadata(JsonObject.empty()));
        return OutboundSignalFactory.newOutboundSignal(thingModified, targets);
    }

    private static OutboundSignal outboundLiveEvent(final Attributes attributes, final Collection<String> requestedAcks,
            final List<Target> targets, final ActorRef testRef) {
        final OutboundSignal outboundTwinEvent = outboundTwinEvent(attributes, requestedAcks, targets, testRef);
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

    private static @NonNull Thing createTestThing() {
        return Thing.newBuilder().setId(thingId())
                .setAttributes(Attributes.newBuilder().set("attr1", "attrValue1").build())
                .setPolicyId(PolicyId.of("test1:policy1"))
                .build();
    }

    private static FilteredTopic topic1() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS).build();
    }

    private static FilteredTopic topic2() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                .withExtraFields(ThingFieldSelector.fromString("definition")).build();

    }

    private static FilteredTopic topic3() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                .withFilter("and(eq(topic:action,\"modified\"),eq(resource:path,\"/features/feature3\"))").build();
    }

    private static FilteredTopic topic3a() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                .withFilter("and(eq(topic:action,\"modified\"),eq(resource:path,\"/features/feature3a\"))").build();
    }

    private static FilteredTopic topic4() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                .withFilter("and(eq(topic:action,\"modified\"),eq(resource:path,\"/features/feature4\"))")
                .withExtraFields(ThingFieldSelector.fromString("definition")).build();

    }

    private static FilteredTopic topic5() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                .withFilter("and(eq(topic:action,\"modified\"),eq(resource:path,\"/features/feature5\"))")
                .withExtraFields(ThingFieldSelector.fromString("attributes")).build();

    }

    private static @NonNull Target createTestTargetMultiTopics(Set<FilteredTopic> topics) {
        return ConnectivityModelFactory.newTargetBuilder()
                .address("multipleExtraFieldsTarget")
                .authorizationContext(singletonContext(target1Subject()))
                .topics(topics)
                .issuedAcknowledgementLabel(AcknowledgementLabel.of("multipleExtraFields"))
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
