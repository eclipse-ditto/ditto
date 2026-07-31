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
import java.util.Collections;
import java.util.LinkedHashSet;
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
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
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
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic4(), topic3(), topic5())));
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
                    .contains(targets.getFirst());

            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra -> {
                        assertThat(extra.getValue(JsonPointer.of("definition")))
                                .as("Outbound signal does not contain the requested extra fields.")
                                .isPresent();
                        assertThat(extra.getValue(JsonPointer.of("features/featureA")))
                                .as("Redundant extra fields exists, which must not exist.")
                                .isNotPresent();
                    });
        }};
    }

    @Test
    public void multipleTopicsWithExtraFieldsMidTopic() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Create a target with multiple FilteredTopics for the same streaming type with different extraFields
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic4(), topic3(), topic5())));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            // Now test a signal that should match the second subscription (no extraFields)
            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("feature3")
                            .build().setProperties(FeatureProperties.newBuilder().set("level", "full").build()),
                    List.of("multipleExtraFields"), targets, getRef());

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            // Expect a publish message
            final BaseClientActor.PublishMappedMessage publishWithPolicy =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            // Verify the target
            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.getFirst());

            // Verify no extra fields are present
            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .as("Outbound signal must not contain any extra field.")
                    .isEmpty();
        }};
    }

    @Test
    public void multipleTopicsWithExtraFieldsLastTopic() {
            new TestKit(actorSystemResource.getActorSystem()) {{
            // Create a target with multiple FilteredTopics for the same streaming type with different extraFields
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic4(), topic3(), topic5())));
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

            // Verify the target
            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.getFirst());

            // Verify extra fields are present only from the last topic
            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra -> {
                        assertThat(extra.getValue(JsonPointer.of("features/featureA")))
                                 .as("Outbound signal does not contain the requested extra fields.")
                                .isPresent().hasValueSatisfying(v -> assertThat(v.asObject().formatAsString()).isEqualTo("{\"properties\":{\"FAP1\":\"FAP1Value\"}}"));
                        assertThat(extra.getValue(JsonPointer.of("definition")))
                                .as("Redundant extra fields exists, which must not exist.")
                                .isNotPresent();
                    });
        }};
    }

    @Test
    public void multipleTopicsWithoutExtraFieldsFastProcessed() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Create a target with multiple topics all without extra fields.
            // Must send outbound signal, skipping filtering as the pre-filtering has already done the job
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic3(), topic3a())));
            final Connection connection = CONNECTION.toBuilder().targets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("random-featureId").build(),
                    List.of("multipleWithoutExtraFields"), targets, getRef());
            underTest.tell(outboundSignal, getRef());

            // Signal published ignoring filters
            final BaseClientActor.PublishMappedMessage publishWithPolicy =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            // No RetrieveThing call is made

            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.getFirst());

            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isEmpty();
        }};
    }

    @Test
    public void multipleTopicsDoubleMatchEnriched() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Create a target with one topic with extra fields and one without extra fields.
            // Create another target with the same topics in opposite order.
            // Must send outbound signals on both targets with extra fields
            final List<Target> targets = List.of(
                    createTestTargetMultiTopics(Collections.unmodifiableSet(new LinkedHashSet<>(List.of(topic4(), topic3a())))),
                    createTestTargetMultiTopics(Collections.unmodifiableSet(new LinkedHashSet<>(List.of(topic3a(), topic4())))));
            final Connection connection = CONNECTION.toBuilder().targets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("feature4").build(),
                    List.of("multipleWithoutExtraFields"), targets, getRef());
            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();
            partialRetrieveAndResponse();

            final BaseClientActor.PublishMappedMessage publishWithPolicy =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.getFirst());

            // Verify extra fields are sent on first target
            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra ->
                            assertThat(extra.getValue(JsonPointer.of("definition")))
                                    .as("Outbound signal does not contain the requested extra fields.")
                                    .isPresent());
            assertThat(publishWithPolicy.getOutboundSignal().getMappedOutboundSignals().get(1).getTargets())
                    .contains(targets.get(1));

            // Verify extra fields are sent on second target
            assertThat(publishWithPolicy.getOutboundSignal().getMappedOutboundSignals().get(1).getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra ->
                            assertThat(extra.getValue(JsonPointer.of("definition")))
                                    .as("Outbound signal does not contain the requested extra fields.")
                                    .isPresent());
        }};
    }

    @Test
    public void multipleTopicsExtraFieldsFilterless() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Create a target with multiple topics with some extra fields, but with a topic without a filter
            // Must send outbound signal, skipping filtering but enriching with the requested extra fields
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic3(), topic5(), topic2())));
            final Connection connection = CONNECTION.toBuilder().targets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("random-featureId").build(),
                    List.of("multipleWithExtraFields"), targets, getRef());
            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            final BaseClientActor.PublishMappedMessage publishWithPolicy =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.getFirst());

            // Verify extra fields from filterless topic
            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra ->
                            assertThat(extra.getValue(JsonPointer.of("definition")))
                                .as("Outbound signal does not contain the requested extra fields.")
                                .isPresent());

        }};
    }

    @Test
    public void multipleTopicsWithCommonRootExtraFields() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Create a target with multiple FilteredTopics for the same streaming type with different extraFields
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic4(), topic1(), topic5())));
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
                    .contains(targets.getFirst());

            // Verify extra fields are not trimmed despite common root with trimmed fields from other topics
            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra -> {
                        assertThat(extra.getValue(JsonPointer.of("features/featureA")))
                                .as("Outbound signal does not contain the requested extra fields.")
                                .isPresent().hasValueSatisfying(v -> assertThat(v.asObject().formatAsString()).isEqualTo("{\"properties\":{\"FAP1\":\"FAP1Value\"}}"));
                        assertThat(extra.getValue(JsonPointer.of("features/featureB")))
                                .as("Redundant extra fields exists, which must not exist.")
                                .isNotPresent();
                        assertThat(extra.getValue(JsonPointer.of("definition")))
                                .as("Redundant extra fields exists, which must not exist.")
                                .isNotPresent();
                    });
        }};
    }

    @Test
    public void multipleTopicsDoubleMatchDeterministicExtraFields() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Create 2 targets with 2 matching multiple FilteredTopics with different extraFields, ordered differently
            final List<Target> targets = List.of(
                    createTestTargetMultiTopics(Collections.unmodifiableSet(new LinkedHashSet<>(List.of(topic4(), topic1(), topic5())))),
                    createTestTargetMultiTopics(Collections.unmodifiableSet(new LinkedHashSet<>(List.of(topic1(), topic4(), topic5())))));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("feature4")
                            .build().setProperties(FeatureProperties.newBuilder().set("level", "full").build()),
                    List.of("multipleExtraFields"), targets, getRef());

            for (int i = 0; i < 10; i++) {
                underTest.tell(outboundSignal, getRef());
                partialRetrieveAndResponse();
                partialRetrieveAndResponse();

                final BaseClientActor.PublishMappedMessage publishWithPolicy =
                        clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

                assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                        .contains(targets.getFirst());

                // Verify extra fields are present only from one and the same topic on first target
                assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                        .isPresent()
                        .hasValueSatisfying(extra -> {
                            assertThat(extra.getValue(JsonPointer.of("definition")))
                                    .as("Outbound signal does not contain the requested extra fields.")
                                    .isPresent();
                            assertThat(extra.getValue(JsonPointer.of("features/featureA")))
                                    .as("Redundant extra fields exists, which must not exist.")
                                    .isNotPresent();
                            assertThat(extra.getValue(JsonPointer.of("features/featureB")))
                                    .as("Redundant extra fields exists, which must not exist.")
                                    .isNotPresent();
                        });

                assertThat(publishWithPolicy.getOutboundSignal().getMappedOutboundSignals().get(1).getTargets())
                        .contains(targets.getFirst());

                // Verify extra fields are present only from one and the same topic on second target
                assertThat(publishWithPolicy.getOutboundSignal()
                        .getMappedOutboundSignals()
                        .get(1)
                        .getAdaptable()
                        .getPayload()
                        .getExtra())
                        .isPresent()
                        .hasValueSatisfying(extra -> {
                            assertThat(extra.getValue(JsonPointer.of("definition")))
                                    .as("Outbound signal does not contain the requested extra fields.")
                                    .isPresent();
                            assertThat(extra.getValue(JsonPointer.of("features/featureA")))
                                    .as("Redundant extra fields exists, which must not exist.")
                                    .isNotPresent();
                            assertThat(extra.getValue(JsonPointer.of("features/featureB")))
                                    .as("Redundant extra fields exists, which must not exist.")
                                    .isNotPresent();
                        });
            }
        }};
    }

    @Test
    public void multipleTopicsWithWildcardExtraFields() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Create a target with multiple FilteredTopics for the same streaming type with different extraFields
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic4(), topicWildcard())));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("featureW")
                            .build().setProperties(FeatureProperties.newBuilder().set("level", "full").build()),
                    List.of("multipleExtraFields"), targets, getRef());

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            final BaseClientActor.PublishMappedMessage publishWithPolicy =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.getFirst());

            // Verify all features extra fields are present due to wildcard selection
            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra -> {
                        assertThat(extra.getValue(JsonPointer.of("features/featureA")))
                                .as("Outbound signal does not contain the requested extra fields.")
                                .isPresent().hasValueSatisfying(v -> assertThat(v.asObject().formatAsString()).isEqualTo("{\"properties\":{\"FAP1\":\"FAP1Value\"}}"));
                        assertThat(extra.getValue(JsonPointer.of("features/featureB")))
                                .as("Redundant extra fields exists, which must not exist.")
                                .isPresent().hasValueSatisfying(v -> assertThat(v.asObject().formatAsString()).isEqualTo("{\"properties\":{\"FBP1\":\"FBP1Value\"}}"));
                        assertThat(extra.getValue(JsonPointer.of("definition")))
                                .as("Redundant extra fields exists, which must not exist.")
                                .isNotPresent();
                    });
        }};
    }

    @Test
    public void multipleTopicsOneFilterlessNoExtraFields() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Create a target with one filterless and with no extra fields topic and another with extra fields
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic0(), topic4())));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("feature4")
                            .build().setProperties(FeatureProperties.newBuilder().set("level", "full").build()),
                    List.of("multipleExtraFields"), targets, getRef());

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            final BaseClientActor.PublishMappedMessage publishWithPolicy =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.getFirst());

            // Verify extra field is present
            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra ->
                            assertThat(extra.getValue(JsonPointer.of("definition")))
                                    .as("Outbound signal does not contain the requested extra fields.")
                                    .isPresent());
        }};
    }

    @Test
    public void crossStreamingTypeCatchAllDoesNotShortCircuitEnrichment() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Target with a TWIN_EVENTS extras-topic plus a LIVE_EVENTS catch-all (no filter, no extras).
            // A TWIN_EVENTS signal must still be enriched via the TWIN_EVENTS topic — the LIVE_EVENTS
            // catch-all is irrelevant to a TWIN_EVENTS signal and must not short-circuit enrichment.
            final List<Target> targets =
                    List.of(createTestTargetMultiTopics(Set.of(topic4(), liveEventsCatchAll())));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("feature4")
                            .build().setProperties(FeatureProperties.newBuilder().set("size", "large").build()),
                    List.of("crossStreamingType"), targets, getRef());

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            final BaseClientActor.PublishMappedMessage publishWithPolicy =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.getFirst());

            // Verify the TWIN_EVENTS extras-topic enriched the signal with "definition"
            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .as("TWIN_EVENTS extras-topic must enrich the outbound signal despite a LIVE_EVENTS catch-all")
                    .isPresent()
                    .hasValueSatisfying(extra ->
                            assertThat(extra.getValue(JsonPointer.of("definition")))
                                    .as("Expected 'definition' extra field from topic4")
                                    .isPresent());
        }};
    }

    @Test
    public void crossStreamingTypeCatchAllDoesNotLeakIntoOtherStreamingType() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Target with a TWIN_EVENTS catch-all (no filter, no extras) plus a LIVE_EVENTS extras-topic.
            // A LIVE_EVENTS signal must be enriched via the LIVE_EVENTS topic — the TWIN_EVENTS
            // catch-all belongs to a different streaming type and must not short-circuit enrichment.
            final List<Target> targets =
                    List.of(createTestTargetMultiTopics(Set.of(twinEventsCatchAll(), liveEventsExtras())));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = outboundLiveEvent(
                    Attributes.newBuilder().set("attr1", "value1").build(),
                    List.of("crossStreamingType"), targets, getRef());

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            final BaseClientActor.PublishMappedMessage publishWithPolicy =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publishWithPolicy.getOutboundSignal().first().getTargets())
                    .contains(targets.getFirst());

            // Verify the LIVE_EVENTS extras-topic enriched the signal with "definition"
            assertThat(publishWithPolicy.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .as("LIVE_EVENTS extras-topic must enrich the outbound signal despite a TWIN_EVENTS catch-all")
                    .isPresent()
                    .hasValueSatisfying(extra ->
                            assertThat(extra.getValue(JsonPointer.of("definition")))
                                    .as("Expected 'definition' extra field from LIVE_EVENTS extras-topic")
                                    .isPresent());
        }};
    }

    // --- target topic pipeline (fn:) filter tests (Task 4) ---

    @Test
    public void mixedTopicsTargetPurePipelineNonMatchDropsEntireTarget() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Target with an RQL+extraFields topic that cannot match this signal (topic4 requires
            // resource:path == /features/feature4), plus a pure-pipeline topic on "ditto-originator".
            // Neither topic matches, so the whole target is dropped.
            final FilteredTopic pipelineTopic = ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                    .withFilter("fn:filter(header:ditto-originator,'ne','x')")
                    .build();
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic4(), pipelineTopic)));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = withOriginator(outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("unrelatedFeature").build(),
                    List.of("source1", "multipleExtraFields"), targets, getRef()), "x");

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            // THEN: sender receives a weak acknowledgement for the dropped target - no signal is published
            final Acknowledgements acks = expectMsgClass(Acknowledgements.class);
            final List<String> ackLabels = acks.getSuccessfulAcknowledgements()
                    .stream()
                    .map(ack -> ack.getLabel().toString())
                    .toList();
            assertThat(ackLabels).containsExactlyInAnyOrder("source1", "multipleExtraFields");
            acks.forEach(ack -> assertThat(ack.isWeak()).describedAs("Expect weak ack, got: " + ack).isTrue());
        }};
    }

    @Test
    public void mixedTopicsTargetPurePipelineMatchPublishesWithoutExtraFieldsAndHeadersUnchanged() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Same mixed target as above, but the pipeline now matches: the target must publish via the
            // pure-pipeline topic (no thing needed) without any extra fields, and without leaking anything
            // (in particular not the internal pipeline seed, see TargetTopicFilter#PIPELINE_SEED) onto the
            // published signal's headers. A second, unfiltered "control" target on the very same signal
            // establishes what the mapping/dispatch pipeline normally does to headers (e.g. stripping
            // internal-only ones, adding "content-type") so the comparison isolates exactly what the pipeline
            // *filter evaluation* itself changed, rather than unrelated header bookkeeping.
            final FilteredTopic pipelineTopic = ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                    .withFilter("fn:filter(header:ditto-originator,'eq','x')")
                    .build();
            final Target pipelineTarget = createTestTargetMultiTopics(Set.of(topic4(), pipelineTopic));
            final Target controlTarget = ConnectivityModelFactory.newTargetBuilder(pipelineTarget)
                    .address("pipelineControlTarget")
                    .topics(Set.of(topic0()))
                    .issuedAcknowledgementLabel(AcknowledgementLabel.of("pipelineControl"))
                    .build();
            final List<Target> targets = List.of(pipelineTarget, controlTarget);
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = withOriginator(outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("unrelatedFeature").build(),
                    List.of("source1", "multipleExtraFields", "pipelineControl"), targets, getRef()), "x");

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            final BaseClientActor.PublishMappedMessage publish =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publish.getOutboundSignal().getMappedOutboundSignals()).hasSize(2);
            assertThat(publish.getOutboundSignal().first().getTargets()).contains(pipelineTarget);

            // Verify no extra fields: the winning topic is the pure-pipeline one, which carries none
            assertThat(publish.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .as("Outbound signal must not contain any extra field: the pure-pipeline topic won selection.")
                    .isEmpty();

            // Verify no leak: the "ditto-originator" header survives untouched, and the header key set is
            // identical to the control target's - i.e. the pipeline evaluation (in particular the internal
            // boolean seed) added/removed nothing beyond what plain unfiltered mapping already does.
            final DittoHeaders pipelineHeaders = publish.getOutboundSignal().first().getAdaptable().getDittoHeaders();
            final DittoHeaders controlHeaders =
                    publish.getOutboundSignal().getMappedOutboundSignals().get(1).getAdaptable().getDittoHeaders();
            assertThat(pipelineHeaders.get("ditto-originator")).isEqualTo("x");
            assertThat(pipelineHeaders.keySet())
                    .as("Pipeline evaluation must not add/remove any header compared to an unfiltered publish of " +
                            "the same signal")
                    .isEqualTo(controlHeaders.keySet());
        }};
    }

    @Test
    public void mixedTopicsTargetEnrichmentSucceedsPipelineMatchesPublishesWithRqlTopicExtraFields() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // T-C1 scenario 1: a single target carries both an RQL+extraFields topic (topic4) and a
            // pure-pipeline topic. Enrichment succeeds and both independently match; the extraFields-first
            // sort (see enrichAndFilterSignal) must make topic4 win the extraFields selection.
            final FilteredTopic pipelineTopic = ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                    .withFilter("fn:filter(header:ditto-originator,'eq','x')")
                    .build();
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic4(), pipelineTopic)));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = withOriginator(outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("feature4")
                            .build().setProperties(FeatureProperties.newBuilder().set("size", "large").build()),
                    List.of("multipleExtraFields"), targets, getRef()), "x");

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            final BaseClientActor.PublishMappedMessage publish =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publish.getOutboundSignal().first().getTargets()).contains(targets.getFirst());
            assertThat(publish.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra ->
                            assertThat(extra.getValue(JsonPointer.of("definition")))
                                    .as("Outbound signal does not contain the requested extra fields from topic4.")
                                    .isPresent());
        }};
    }

    @Test
    public void mixedTopicsTargetEnrichmentSucceedsPipelineNonMatchRqlMatchesPublishesWithExtraFields() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // T-C1 scenario 2: same mixed target, but the pipeline does NOT match this time (originator "x" !=
            // required "y"). topic4's RQL matches independently on feature4, so the target must still publish
            // with topic4's extraFields - a non-matching pipeline topic must not sink the whole target nor win
            // the extraFields selection (it must return empty from applyFilter).
            final FilteredTopic pipelineTopic = ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                    .withFilter("fn:filter(header:ditto-originator,'eq','y')")
                    .build();
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic4(), pipelineTopic)));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = withOriginator(outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("feature4")
                            .build().setProperties(FeatureProperties.newBuilder().set("size", "large").build()),
                    List.of("multipleExtraFields"), targets, getRef()), "x");

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            final BaseClientActor.PublishMappedMessage publish =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publish.getOutboundSignal().first().getTargets()).contains(targets.getFirst());
            assertThat(publish.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra ->
                            assertThat(extra.getValue(JsonPointer.of("definition")))
                                    .as("RQL topic must win and enrich the outbound signal even though the " +
                                            "sibling pipeline topic did not match.")
                                    .isPresent());
        }};
    }

    @Test
    public void rqlAndPipelineFilterParamsEvaluateRqlAgainstEnrichedThing() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // A single topic with an RQL filter param, a pipeline filter param (AND semantics) and extraFields.
            // The RQL param references "features/featureA", which is absent from the signal-derived (raw) thing
            // and only appears once partialRetrieveAndResponse's extra data is merged in - so a match proves the
            // RQL param (fed via TargetTopicFilter.PartitionedFilters#getRqlExpressions) is evaluated against
            // the enriched thing.
            final FilteredTopic combinedTopic = ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                    .withFilters(List.of("exists(features/featureA)",
                            "fn:filter(header:ditto-originator,'eq','x')"))
                    .withExtraFields(ThingFieldSelector.fromString("definition"))
                    .build();
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(combinedTopic)));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = withOriginator(outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("unrelatedFeature").build(),
                    List.of("multipleExtraFields"), targets, getRef()), "x");

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            final BaseClientActor.PublishMappedMessage publish =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publish.getOutboundSignal().first().getTargets()).contains(targets.getFirst());
            assertThat(publish.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra -> assertThat(extra.getValue(JsonPointer.of("definition")))
                            .isPresent());
        }};
    }

    @Test
    public void chainedPipelineStagesWithExtraFieldsAllMatchPublishesEnriched() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // A single topic with one pipeline filter param chaining TWO fn: stages (AND semantics) and
            // extraFields: both stages match, so the signal is published with the topic's extra fields after
            // the post-enrichment re-evaluation.
            final FilteredTopic pipelineTopic = ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                    .withFilters(List.of("fn:filter(header:ditto-originator,'eq','x')" +
                            "|fn:filter(header:ditto-originator,'ne','y')"))
                    .withExtraFields(ThingFieldSelector.fromString("definition"))
                    .build();
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(pipelineTopic)));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = withOriginator(outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("unrelatedFeature").build(),
                    List.of("multipleExtraFields"), targets, getRef()), "x");

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            final BaseClientActor.PublishMappedMessage publish =
                    clientActorProbe.expectMsgClass(BaseClientActor.PublishMappedMessage.class);

            assertThat(publish.getOutboundSignal().first().getTargets()).contains(targets.getFirst());
            assertThat(publish.getOutboundSignal().first().getAdaptable().getPayload().getExtra())
                    .isPresent()
                    .hasValueSatisfying(extra -> assertThat(extra.getValue(JsonPointer.of("definition")))
                            .isPresent());
        }};
    }

    @Test
    public void chainedPipelineStagesSecondStageNonMatchDropsTarget() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Same topic shape as above, but the SECOND chained stage does not match ("ne 'x'" with originator
            // "x") - AND semantics must drop the whole target even though the first stage matches.
            final FilteredTopic pipelineTopic = ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                    .withFilters(List.of("fn:filter(header:ditto-originator,'eq','x')" +
                            "|fn:filter(header:ditto-originator,'ne','x')"))
                    .withExtraFields(ThingFieldSelector.fromString("definition"))
                    .build();
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(pipelineTopic)));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = withOriginator(outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("unrelatedFeature").build(),
                    List.of("source1", "multipleExtraFields"), targets, getRef()), "x");

            underTest.tell(outboundSignal, getRef());
            partialRetrieveAndResponse();

            // THEN: sender receives weak acknowledgements for the dropped target - no signal is published
            final Acknowledgements acks = expectMsgClass(Acknowledgements.class);
            final List<String> ackLabels = acks.getSuccessfulAcknowledgements()
                    .stream()
                    .map(ack -> ack.getLabel().toString())
                    .toList();
            assertThat(ackLabels).containsExactlyInAnyOrder("source1", "multipleExtraFields");
            acks.forEach(ack -> assertThat(ack.isWeak()).describedAs("Expect weak ack, got: " + ack).isTrue());
        }};
    }

    @Test
    public void rqlExtraFieldsOnlyTargetStillDroppedWhenEnrichmentFails() {
        new TestKit(actorSystemResource.getActorSystem()) {{
            // Regression guard for T-C1 scenario 4: a target with only an RQL+extraFields topic (no pipeline
            // topic) must still be dropped when signal enrichment genuinely fails - the widened :459/:498
            // predicate must not change this pre-existing behavior. Enrichment failure is simulated exactly
            // like the existing eventsWithFailedEnrichmentIssueFailedAcks test: never reply to the
            // RetrieveThing, so the underlying ask-with-retry ultimately fails. That failure is intercepted by
            // OutboundMappingProcessorActor's own recovery path (recoverFromEnrichmentError) before
            // enrichAndFilterSignal's topics-selection code ever runs, so it proves the observable
            // "still dropped" contract end-to-end.
            final List<Target> targets = List.of(createTestTargetMultiTopics(Set.of(topic4())));
            final Connection connection = CONNECTION.toBuilder().setTargets(targets).build();
            final ActorRef underTest = getTestActorRef(connection);

            final OutboundSignal outboundSignal = outboundFeatureTwinEvent(THING,
                    Feature.newBuilder().withId("feature4").build(),
                    List.of("source1", "multipleExtraFields"), targets, getRef());
            underTest.tell(outboundSignal, getRef());
            proxyActorProbe.expectMsgClass(RetrieveThing.class);
            // no reply: the retrieval ask fails/times out, simulating an enrichment failure

            final Acknowledgements acks = expectMsgClass(Duration.ofSeconds(5), Acknowledgements.class);
            final List<String> failedAckLabels = acks.getFailedAcknowledgements()
                    .stream()
                    .map(ack -> ack.getLabel().toString())
                    .toList();
            assertThat(failedAckLabels).containsExactly("multipleExtraFields");
        }};
    }

    private void partialRetrieveAndResponse() {
        // Expect enrichment request for all fields in all topics
        final RetrieveThing retrieveEnrichedThing = proxyActorProbe.expectMsgClass(RetrieveThing.class);
        assertThat(retrieveEnrichedThing.getSelectedFields()).isPresent();

        final Attributes attributes = Attributes.newBuilder().set("newAttribute", "attrValue").build();
        final FeatureProperties propertiesA = FeatureProperties.newBuilder().set("FAP1", "FAP1Value").build();
        final FeatureProperties propertiesB = FeatureProperties.newBuilder().set("FBP1", "FBP1Value").build();
        final Feature featureA = Feature.newBuilder().properties(propertiesA).withId("featureA").build();
        final Feature featureB = Feature.newBuilder().properties(propertiesB).withId("featureB").build();
        final Features features = Features.newBuilder().set(featureA).set(featureB).build();

        // Reply with enriched Thing
        final Thing enrichedThing = Thing.newBuilder()
                .setAttributes(attributes)
                .setFeatures(features)
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

    /**
     * Returns a copy of {@code outboundSignal} whose source signal carries an explicit {@code ditto-originator}
     * header, as required by any target topic pipeline filter relying on {@code header:ditto-originator}.
     */
    private static OutboundSignal withOriginator(final OutboundSignal outboundSignal, final String originator) {
        return OutboundSignalFactory.newOutboundSignal(
                outboundSignal.getSource().setDittoHeaders(outboundSignal.getSource().getDittoHeaders().toBuilder()
                        .putHeader("ditto-originator", originator)
                        .build()),
                outboundSignal.getTargets());
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
        return Thing.newBuilder().setId(thingId()).build();
    }

    private static FilteredTopic topic0() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS).build();
    }

    private static FilteredTopic topic1() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                .withFilter("and(eq(topic:action,\"modified\"),eq(resource:path,\"/features/feature4\"))")
                .withExtraFields(ThingFieldSelector.fromString("features/featureA,features/featureB"))
                .build();

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
                .withFilter("and(eq(topic:action,\"modified\"),eq(resource:path,\"/features/feature4\"))").build();
    }

    private static FilteredTopic topic4() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                .withFilter("and(eq(topic:action,\"modified\"),eq(resource:path,\"/features/feature4\"))")
                .withExtraFields(ThingFieldSelector.fromString("definition")).build();

    }

    private static FilteredTopic topic5() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                .withFilter("and(eq(topic:action,\"modified\"),eq(resource:path,\"/features/feature5\"))")
                .withExtraFields(ThingFieldSelector.fromString("features/featureA")).build();

    }

    private static FilteredTopic topicWildcard() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS)
                .withFilter("and(eq(topic:action,\"modified\"),eq(resource:path,\"/features/featureW\"))")
                .withExtraFields(ThingFieldSelector.fromString("features/*")).build();

    }

    private static FilteredTopic liveEventsCatchAll() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.LIVE_EVENTS).build();
    }

    private static FilteredTopic twinEventsCatchAll() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.TWIN_EVENTS).build();
    }

    private static FilteredTopic liveEventsExtras() {
        return ConnectivityModelFactory.newFilteredTopicBuilder(Topic.LIVE_EVENTS)
                .withExtraFields(ThingFieldSelector.fromString("definition")).build();
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
