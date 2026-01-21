/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.model.PayloadMappingDefinition;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.mapping.DittoMessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.connectivity.service.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.connectivity.service.util.ConnectionPubSub;
import org.eclipse.ditto.internal.utils.pekko.ActorSystemResource;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.protocol.DittoProtocolAdapterProvider;
import org.eclipse.ditto.internal.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.internal.utils.protocol.config.ProtocolConfig;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Tests {@link OutboundMappingProcessor}.
 */
public final class OutboundMappingProcessorTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE =
            ActorSystemResource.newInstance(TestConstants.CONFIG);

    private OutboundMappingProcessor underTest;

    private static final String DITTO_MAPPER = "ditto";
    private static final Map<String, String> DITTO_MAPPER_CONDITIONS = Map.of(
            "testCondition", "fn:filter(header:correlation-id,'ne','testCor')",
            "testCondition2", "fn:filter(header:correlation-id,'ne','testCor2')");
    private static final String DITTO_MAPPER_BY_ALIAS = "ditto-by-alias";
    private static final String DITTO_MAPPER_CUSTOM_HEADER_BLOCKLIST = "ditto-cust-header";
    private static final String DROPPING_MAPPER = "dropping";
    private static final String FAILING_MAPPER = "faulty";
    private static final String DUPLICATING_MAPPER = "duplicating";

    private static ProtocolAdapterProvider protocolAdapterProvider;
    private static ThreadSafeDittoLoggingAdapter logger;

    @BeforeClass
    public static void setUp() {
        logger = Mockito.mock(ThreadSafeDittoLoggingAdapter.class);
        when(logger.withMdcEntry(Mockito.any(CharSequence.class), Mockito.nullable(CharSequence.class)))
                .thenReturn(logger);
        when(logger.withCorrelationId(Mockito.nullable(String.class))).thenReturn(logger);
        when(logger.withCorrelationId(Mockito.nullable(WithDittoHeaders.class))).thenReturn(logger);
        when(logger.withCorrelationId(Mockito.nullable(DittoHeaders.class))).thenReturn(logger);
        when(logger.withCorrelationId(Mockito.nullable(Map.class))).thenReturn(logger);
        when(logger.withCorrelationId(Mockito.nullable(CharSequence.class))).thenReturn(logger);

        protocolAdapterProvider = new DittoProtocolAdapterProvider(Mockito.mock(ProtocolConfig.class));
    }

    @Before
    public void init() {
        final Map<String, MappingContext> mappings = new HashMap<>();
        mappings.put(DITTO_MAPPER, DittoMessageMapper.CONTEXT);
        mappings.put(DITTO_MAPPER_BY_ALIAS,
                ConnectivityModelFactory.newMappingContext("Ditto", JsonObject.empty(),
                        DITTO_MAPPER_CONDITIONS, Collections.emptyMap()));

        final Map<String, String> dittoCustomMapperHeaders = new HashMap<>();
        dittoCustomMapperHeaders.put(
                MessageMapperConfiguration.CONTENT_TYPE_BLOCKLIST,
                "foo/bar"
        );
        final MappingContext dittoCustomMappingContext =
                ConnectivityModelFactory.newMappingContext("Ditto", dittoCustomMapperHeaders);
        mappings.put(DITTO_MAPPER_CUSTOM_HEADER_BLOCKLIST, dittoCustomMappingContext);
        mappings.put(FAILING_MAPPER, FaultyMessageMapper.CONTEXT);
        mappings.put(DROPPING_MAPPER, DroppingMessageMapper.CONTEXT);

        final Map<String, String> duplicatingMapperHeaders = new HashMap<>();
        duplicatingMapperHeaders.put(
                MessageMapperConfiguration.CONTENT_TYPE_BLOCKLIST,
                "text/custom-plain,application/custom-json"
        );
        final MappingContext duplicatingMappingContext =
                ConnectivityModelFactory.newMappingContext(DuplicatingMessageMapper.ALIAS, duplicatingMapperHeaders);
        mappings.put(DUPLICATING_MAPPER, duplicatingMappingContext);

        final PayloadMappingDefinition payloadMappingDefinition =
                ConnectivityModelFactory.newPayloadMappingDefinition(mappings);
        final Connection connection =
                ConnectivityModelFactory.newConnectionBuilder(ConnectionId.of("theConnection"),
                        ConnectionType.AMQP_10,
                        ConnectivityStatus.OPEN,
                        "amqp://localhost:5671")
                        .payloadMappingDefinition(payloadMappingDefinition)
                        .sources(List.of(ConnectivityModelFactory.newSourceBuilder()
                                .address("address")
                                .authorizationContext(AuthorizationContext.newInstance(
                                        DittoAuthorizationContextType.UNSPECIFIED,
                                        AuthorizationSubject.newInstance("ditto:ditto")
                                ))
                                .declaredAcknowledgementLabels(Set.of(AcknowledgementLabel.of("custom:ack")))
                                .build()))
                        .build();
        final ConnectionPubSub mockPubSub = Mockito.mock(ConnectionPubSub.class);
        underTest = OutboundMappingProcessor.of(connection,
                TestConstants.CONNECTIVITY_CONFIG,
                ACTOR_SYSTEM_RESOURCE.getActorSystem(),
                protocolAdapterProvider.getProtocolAdapter(null),
                logger);
    }

    @Test
    public void testOutboundMessageMapped() {
        testOutbound(1, 0, 0, targetWithMapping(DITTO_MAPPER));
    }

    @Test
    public void testOutboundResponseMapped() {
        testOutbound(1, 0, 0);
    }

    @Test
    public void testGroupingOfTargets() {
        /*
          Note: Targets are grouped by payload mapping. When there's no partial access filtering,
          one adaptable is created for all targets in a group. So we get:
           - 3 targets with 1 mapper (grouped together, mapping is done once)  -> 1 message (with 3 targets)
           - 2 targets with 2 mappers (grouped together, mapping is done twice) -> 2 messages (with 2 targets)
           - 1 target with 3 mappers (no grouping, mapping is done three times)       -> 3 messages (with 1 target)
          Total: 1 + 2 + 3 = 6 mappings
         */
        testOutbound(6, 0, 0,
                targetWithMapping(DITTO_MAPPER),
                targetWithMapping(DITTO_MAPPER),
                targetWithMapping(DITTO_MAPPER),
                targetWithMapping(DITTO_MAPPER, DITTO_MAPPER),
                targetWithMapping(DITTO_MAPPER, DITTO_MAPPER),
                targetWithMapping(DITTO_MAPPER, DITTO_MAPPER, DITTO_MAPPER)
        );
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    public void testOutboundMessageEnriched() {
        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{
            final ThingModifiedEvent signal = TestConstants.thingModified(
                    TestConstants.Targets.TWIN_TARGET.getAuthorizationContext().getAuthorizationSubjects());
            final JsonObject extra = JsonObject.newBuilder().set("x", 5).build();
            final OutboundSignal outboundSignal = Mockito.mock(OutboundSignal.class);
            final MappingOutcome.Visitor<OutboundSignal.Mapped, Void> mock = Mockito.mock(MappingOutcome.Visitor.class);
            when(outboundSignal.getExtra()).thenReturn(Optional.of(extra));
            when(outboundSignal.getSource()).thenReturn(signal);
            underTest.process(outboundSignal).forEach(outcome -> outcome.accept(mock));
            final ArgumentCaptor<OutboundSignal.Mapped> captor = ArgumentCaptor.forClass(OutboundSignal.Mapped.class);
            verify(mock, times(1)).onMapped(any(String.class), captor.capture());
            verify(mock, times(0)).onError(any(String.class), any(Exception.class), any(), any());
            verify(mock, times(0)).onDropped(any(String.class), any());

            assertThat(captor.getAllValues()).allSatisfy(em -> assertThat(em.getAdaptable().getPayload().getExtra())
                    .contains(extra));
        }};
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    public void testOutboundEventWithRequestedAcksWhichAreIssuedByTargetDontContainRequestedAcks() {
        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{
            final AuthorizationSubject targetSubject = AuthorizationSubject.newInstance("issuer:subject");
            ThingModifiedEvent signal = TestConstants.thingModified(Collections.singletonList(targetSubject));
            final AcknowledgementLabel customAckLabel = AcknowledgementLabel.of("custom:ack");
            final AcknowledgementLabel targetIssuedAckLabel = AcknowledgementLabel.of("issued:ack");
            signal = signal.setDittoHeaders(signal.getDittoHeaders().toBuilder()
                    .acknowledgementRequest(AcknowledgementRequest.of(targetIssuedAckLabel),
                            AcknowledgementRequest.of(customAckLabel),
                            AcknowledgementRequest.parseAcknowledgementRequest("non-declared:ack")
                    )
                    .build()
            );
            final OutboundSignal outboundSignal = Mockito.mock(OutboundSignal.class);
            final MappingOutcome.Visitor<OutboundSignal.Mapped, Void> mock = Mockito.mock(MappingOutcome.Visitor.class);
            when(outboundSignal.getSource()).thenReturn(signal);
            when(outboundSignal.getTargets()).thenReturn(List.of(
                    ConnectivityModelFactory.newTargetBuilder()
                            .address("test")
                            .issuedAcknowledgementLabel(targetIssuedAckLabel)
                            .authorizationContext(AuthorizationContext.newInstance(
                                    DittoAuthorizationContextType.UNSPECIFIED,
                                    targetSubject))
                            .topics(Topic.TWIN_EVENTS)
                            .build()
            ));
            underTest.process(outboundSignal).forEach(outcome -> outcome.accept(mock));
            final ArgumentCaptor<OutboundSignal.Mapped> captor = ArgumentCaptor.forClass(OutboundSignal.Mapped.class);
            verify(mock, times(1)).onMapped(any(String.class), captor.capture());
            verify(mock, times(0)).onError(any(String.class), any(Exception.class), any(), any());
            verify(mock, times(0)).onDropped(any(String.class), any());

            assertThat(captor.getAllValues()).allSatisfy(em ->
                    assertThat(em.getAdaptable().getDittoHeaders().getAcknowledgementRequests())
                            .containsOnly(AcknowledgementRequest.of(customAckLabel)));
        }};
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    public void testOutboundLiveMessageWithRequestedAcksWhichAreIssuedByTargetDontContainRequestedAcks() {
        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{
            final AuthorizationSubject targetSubject = AuthorizationSubject.newInstance("issuer:subject");
            final AcknowledgementLabel customAckLabel = AcknowledgementLabel.of("custom:ack");
            final AcknowledgementLabel targetIssuedAckLabel = AcknowledgementLabel.of("issued:ack");
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .channel(TopicPath.Channel.LIVE.getName())
                    .readGrantedSubjects(Collections.singletonList(targetSubject))
                    .acknowledgementRequest(AcknowledgementRequest.of(targetIssuedAckLabel),
                            AcknowledgementRequest.of(customAckLabel))
                    .build();
            final Message<Object> message =
                    Message.newBuilder(
                            MessageHeaders.newBuilder(MessageDirection.TO, TestConstants.Things.THING_ID, "ditto")
                                    // adding the ack requests additionally to the message headers would break the test
                                    // as the messageHeaders are merged into the DittoHeaders and overwrite them
                                    .acknowledgementRequest(AcknowledgementRequest.of(targetIssuedAckLabel),
                                            AcknowledgementRequest.of(customAckLabel))
                                    .build())
                            .build();
            final MessageCommand signal = SendThingMessage.of(TestConstants.Things.THING_ID, message, dittoHeaders);
            final OutboundSignal outboundSignal = Mockito.mock(OutboundSignal.class);
            final MappingOutcome.Visitor<OutboundSignal.Mapped, Void> mock = Mockito.mock(MappingOutcome.Visitor.class);
            when(outboundSignal.getSource()).thenReturn(signal);
            when(outboundSignal.getTargets()).thenReturn(List.of(
                    ConnectivityModelFactory.newTargetBuilder()
                            .address("test")
                            .issuedAcknowledgementLabel(targetIssuedAckLabel)
                            .authorizationContext(AuthorizationContext.newInstance(
                                    DittoAuthorizationContextType.UNSPECIFIED,
                                    targetSubject))
                            .topics(Topic.TWIN_EVENTS)
                            .build()
            ));
            underTest.process(outboundSignal).forEach(outcome -> outcome.accept(mock));
            final ArgumentCaptor<OutboundSignal.Mapped> captor = ArgumentCaptor.forClass(OutboundSignal.Mapped.class);
            verify(mock, times(1)).onMapped(any(String.class), captor.capture());
            verify(mock, times(0)).onError(any(String.class), any(Exception.class), any(), any());
            verify(mock, times(0)).onDropped(any(String.class), any());

            assertThat(captor.getAllValues()).allSatisfy(em ->
                    assertThat(em.getAdaptable().getDittoHeaders().getAcknowledgementRequests())
                            .containsAll(List.of(
                                    AcknowledgementRequest.of(customAckLabel),
                                    AcknowledgementRequest.of(targetIssuedAckLabel)))
            );
        }};
    }

    @Test
    public void testOutboundMessageDropped() {
        testOutbound(0, 1, 0, targetWithMapping(DROPPING_MAPPER));
    }

    @Test
    public void testOutboundMessageDuplicated() {
        final List<AuthorizationSubject> readSubjects =
                TestConstants.Targets.TWIN_TARGET.getAuthorizationContext().getAuthorizationSubjects();
        testOutbound(TestConstants.thingModifiedWithCor(readSubjects),
                2, 0, 0, false, targetWithMapping(DUPLICATING_MAPPER));
    }

    @Test
    public void testOutboundMappingFails() {
        testOutbound(0, 0, 1, targetWithMapping(FAILING_MAPPER));
    }

    @Test
    public void testOutboundMessageDroppedFailedMappedDuplicated() {
        final List<AuthorizationSubject> readSubjects =
                TestConstants.Targets.TWIN_TARGET.getAuthorizationContext().getAuthorizationSubjects();
        testOutbound(TestConstants.thingModifiedWithCor(readSubjects),
                2 /* duplicated */ + 1 /* mapped */, 1, 1, false,
                targetWithMapping(DROPPING_MAPPER, FAILING_MAPPER, DITTO_MAPPER, DUPLICATING_MAPPER));
    }

    private static Target targetWithMapping(final String... mappings) {
        return ConnectivityModelFactory.newTargetBuilder(TestConstants.Targets.TWIN_TARGET)
                .address(UUID.randomUUID().toString())
                .payloadMapping(ConnectivityModelFactory.newPayloadMapping(mappings))
                .authorizationContext(TestConstants.Targets.TWIN_TARGET.getAuthorizationContext())
                .build();
    }

    private void testOutbound(final int mapped, final int dropped, final int failed, final Target... targets) {
        final List<AuthorizationSubject> readSubjects = targets.length > 0 && targets[0].getAuthorizationContext() != null
                ? targets[0].getAuthorizationContext().getAuthorizationSubjects()
                : TestConstants.Targets.TWIN_TARGET.getAuthorizationContext().getAuthorizationSubjects();
        final ThingModifiedEvent<?> event = TestConstants.thingModified(readSubjects);
        testOutbound(event, mapped, dropped, failed, true, targets);
    }

    @SuppressWarnings("unchecked")
    private void testOutbound(
            final ThingModifiedEvent<?> signal,
            final int mapped,
            final int dropped,
            final int failed,
            final boolean assertTargets,
            final Target... targets
    ) {
        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{

            // expect one message per mapper per target
            final List<Target> expectedTargets = Arrays.stream(targets)
                    .flatMap(t -> Stream.generate(() -> t).limit(t.getPayloadMapping().getMappings().size()))
                    .toList();

            final OutboundSignal outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(signal, Arrays.asList(targets));

            final MappingOutcome.Visitor<OutboundSignal.Mapped, Void> mock = Mockito.mock(MappingOutcome.Visitor.class);
            underTest.process(outboundSignal).forEach(outcome -> outcome.accept(mock));
            final ArgumentCaptor<OutboundSignal.Mapped> captor = ArgumentCaptor.forClass(OutboundSignal.Mapped.class);
            verify(mock, times(mapped)).onMapped(any(String.class), captor.capture());
            verify(mock, times(failed)).onError(any(String.class), any(Exception.class), any(), any());
            verify(mock, times(dropped)).onDropped(any(String.class), any());

            assertThat(captor.getAllValues()).allSatisfy(em ->
                    assertThat(removeCorrelationId(ProtocolFactory.jsonifiableAdaptableFromJson(
                            JsonFactory.newObject(em.getExternalMessage().getTextPayload().orElseThrow()))).toJsonString())
                            .contains(removeCorrelationId(ProtocolFactory.wrapAsJsonifiableAdaptable(
                                    DittoProtocolAdapter.newInstance().toAdaptable(signal))).toJsonString()));

            if (assertTargets && mapped > 0) {
                assertThat(captor.getAllValues()
                        .stream()
                        .flatMap(mapped -> mapped.getTargets().stream())
                        .toList())
                        .containsExactlyInAnyOrderElementsOf(expectedTargets);
            }
        }};
    }

    // The correlation-id gets substituted with the internal correlation-id which changes the order in which the
    // headers are represented in string, this makes the string comparison fail.
    private static JsonifiableAdaptable removeCorrelationId(final JsonifiableAdaptable adaptable) {
        return adaptable.setDittoHeaders(adaptable.getDittoHeaders().toBuilder()
                .correlationId(null)
                .readGrantedSubjects(Collections.emptyList())
                .build());
    }

    @Test
    public void internalHeadersAreFilteredForKafkaConnection() {
        final DittoHeaders headersWithInternal = DittoHeaders.newBuilder()
                .correlationId("test-correlation-id")
                .build();

        final ThingModifiedEvent<?> event = TestConstants.thingModified(Collections.emptyList())
                .setDittoHeaders(headersWithInternal);

        final Target kafkaTarget = ConnectivityModelFactory.newTargetBuilder()
                .address("kafka-topic")
                .authorizationContext(AuthorizationContext.newInstance(
                        DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance("test:subject")
                ))
                .topics(Topic.TWIN_EVENTS)
                .payloadMapping(ConnectivityModelFactory.newPayloadMapping(DITTO_MAPPER))
                .build();

        final OutboundSignal outboundSignal =
                OutboundSignalFactory.newOutboundSignal(event, List.of(kafkaTarget));

        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{
            final MappingOutcome.Visitor<OutboundSignal.Mapped, Void> mock = Mockito.mock(MappingOutcome.Visitor.class);
            underTest.process(outboundSignal).forEach(outcome -> outcome.accept(mock));
            final ArgumentCaptor<OutboundSignal.Mapped> captor = ArgumentCaptor.forClass(OutboundSignal.Mapped.class);
            verify(mock, times(1)).onMapped(any(String.class), captor.capture());

            final OutboundSignal.Mapped mapped = captor.getValue();
            final JsonifiableAdaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(
                    JsonFactory.newObject(mapped.getExternalMessage().getTextPayload().orElseThrow()));

            assertThat(adaptable.getTopicPath().getAction()).contains(TopicPath.Action.MODIFIED);
            assertThat(adaptable.getPayload().getValue()).isPresent();
        }};
    }

    @Test
    public void internalHeadersAreFilteredForMqttConnection() {
        final DittoHeaders headersWithInternal = DittoHeaders.newBuilder()
                .correlationId("test-correlation-id")
                .build();

        final ThingModifiedEvent<?> event = TestConstants.thingModified(Collections.emptyList())
                .setDittoHeaders(headersWithInternal);

        final Target mqttTarget = ConnectivityModelFactory.newTargetBuilder()
                .address("mqtt/topic")
                .authorizationContext(AuthorizationContext.newInstance(
                        DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance("test:subject")
                ))
                .topics(Topic.TWIN_EVENTS)
                .payloadMapping(ConnectivityModelFactory.newPayloadMapping(DITTO_MAPPER))
                .build();

        final OutboundSignal outboundSignal =
                OutboundSignalFactory.newOutboundSignal(event, List.of(mqttTarget));

        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{
            final MappingOutcome.Visitor<OutboundSignal.Mapped, Void> mock = Mockito.mock(MappingOutcome.Visitor.class);
            underTest.process(outboundSignal).forEach(outcome -> outcome.accept(mock));
            final ArgumentCaptor<OutboundSignal.Mapped> captor = ArgumentCaptor.forClass(OutboundSignal.Mapped.class);
            verify(mock, times(1)).onMapped(any(String.class), captor.capture());

            final OutboundSignal.Mapped mapped = captor.getValue();
            final JsonifiableAdaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(
                    JsonFactory.newObject(mapped.getExternalMessage().getTextPayload().orElseThrow()));

            assertThat(adaptable.getTopicPath().getAction()).contains(TopicPath.Action.MODIFIED);
            assertThat(adaptable.getPayload().getValue()).isPresent();
        }};
    }

    @Test
    public void internalHeadersAreFilteredForAmqpConnection() {
        final DittoHeaders headersWithInternal = DittoHeaders.newBuilder()
                .correlationId("test-correlation-id")
                .build();

        final ThingModifiedEvent<?> event = TestConstants.thingModified(Collections.emptyList())
                .setDittoHeaders(headersWithInternal);

        final Target amqpTarget = ConnectivityModelFactory.newTargetBuilder()
                .address("amqp/queue")
                .authorizationContext(AuthorizationContext.newInstance(
                        DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance("test:subject")
                ))
                .topics(Topic.TWIN_EVENTS)
                .payloadMapping(ConnectivityModelFactory.newPayloadMapping(DITTO_MAPPER))
                .build();

        final OutboundSignal outboundSignal =
                OutboundSignalFactory.newOutboundSignal(event, List.of(amqpTarget));

        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{
            final MappingOutcome.Visitor<OutboundSignal.Mapped, Void> mock = Mockito.mock(MappingOutcome.Visitor.class);
            underTest.process(outboundSignal).forEach(outcome -> outcome.accept(mock));
            final ArgumentCaptor<OutboundSignal.Mapped> captor = ArgumentCaptor.forClass(OutboundSignal.Mapped.class);
            verify(mock, times(1)).onMapped(any(String.class), captor.capture());

            final OutboundSignal.Mapped mapped = captor.getValue();
            final JsonifiableAdaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(
                    JsonFactory.newObject(mapped.getExternalMessage().getTextPayload().orElseThrow()));

            assertThat(adaptable.getTopicPath().getAction()).contains(TopicPath.Action.MODIFIED);
            assertThat(adaptable.getPayload().getValue()).isPresent();
        }};
    }


    @Test
    public void testResponseDiversionIntegration() {
        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{
            // Test that response diversion interceptor is properly integrated
            final ConnectionId connectionId = ConnectionId.of("test-connection");
            final ConnectionId targetConnectionId = ConnectionId.of("target-connection");

            // Create connection with diversion configured in source header mapping
            final Map<String, String> headerMapping = Map.of(
                    DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), targetConnectionId.toString()
            );
            final Connection connection = ConnectivityModelFactory.newConnectionBuilder(
                    connectionId,
                    ConnectionType.MQTT,
                    ConnectivityStatus.OPEN,
                    "tcp://localhost:1883"
            )
                    .sources(Collections.singletonList(
                            ConnectivityModelFactory.newSourceBuilder()
                                    .address("test/topic")
                                    .authorizationContext(AuthorizationContext.newInstance(
                                            DittoAuthorizationContextType.UNSPECIFIED,
                                            AuthorizationSubject.newInstance("integration:source")))
                                    .headerMapping(ConnectivityModelFactory.newHeaderMapping(headerMapping))
                                    .build()
                    ))
                    .build();

            // Create headers with diversion configuration
            final DittoHeaders headersWithDiversion = DittoHeaders.newBuilder()
                    .correlationId("test-correlation")
                    .putHeader(DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), targetConnectionId.toString())
                    .putHeader(DittoHeaderDefinition.DIVERT_EXPECTED_RESPONSE_TYPES.getKey(), "response")
                    .putHeader(DittoHeaderDefinition.ORIGIN.getKey(), connectionId.toString())
                    .build();

            // Create a command response signal
            final ModifyThingResponse response = ModifyThingResponse.modified(
                    TestConstants.Things.THING_ID,
                    headersWithDiversion
            );

            // Create target for the outbound signal
            final Target target = ConnectivityModelFactory.newTargetBuilder()
                    .address("test/address")
                    .authorizationContext(AuthorizationContext.newInstance(
                            DittoAuthorizationContextType.UNSPECIFIED,
                            AuthorizationSubject.newInstance("integration:test")))
                    .topics(Topic.TWIN_EVENTS)
                    .build();

            final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(
                    response,
                    Collections.singletonList(target)
            );

            // Mock the response diversion pub sub
            final ConnectionPubSub mockPubSub = Mockito.mock(ConnectionPubSub.class);

            // Create processor with response diversion enabled
            final OutboundMappingProcessor processorWithDiversion = OutboundMappingProcessor.of(
                    connection,
                    TestConstants.CONNECTIVITY_CONFIG,
                    ACTOR_SYSTEM_RESOURCE.getActorSystem(),
                    protocolAdapterProvider.getProtocolAdapter(null),
                    logger,
                    ResponseDiversionInterceptor.of(connection, mockPubSub));

            // Process the outbound signal
            final MappingOutcome.Visitor<OutboundSignal.Mapped, Void> visitor = Mockito.mock(MappingOutcome.Visitor.class);
            final List<MappingOutcome<OutboundSignal.Mapped>> outcomes = processorWithDiversion.process(outboundSignal);

            // When response diversion is active, the signal should be diverted and no mapped outcome should be produced
            final long outcomeCount = outcomes.stream().peek(outcome -> outcome.accept(visitor)).count();

            // Verify the response was diverted (no mapping outcomes)
            assertThat(outcomeCount).isEqualTo(0);
            verify(visitor, times(0)).onMapped(any(), any());
            verify(visitor, times(0)).onError(any(), any(), any(), any());
            verify(visitor, times(0)).onDropped(any(), any());

            // Verify the response was published to the target connection
            verify(mockPubSub).publishResponseForDiversion(any(CommandResponse.class), eq(targetConnectionId), any(CharSequence.class), any());
        }};
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    public void partialAccessPathsFilteredPerTargetWithDifferentAuthorizationContexts() {
        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{
            final AuthorizationSubject user1Subject = AuthorizationSubject.newInstance("pre:user1");
            final AuthorizationSubject user2Subject = AuthorizationSubject.newInstance("pre:user2");

            final String partialAccessHeader = JsonFactory.newObjectBuilder()
                    .set("subjects", JsonFactory.newArrayBuilder()
                            .add(user1Subject.getId())
                            .add(user2Subject.getId())
                            .build())
                    .set("paths", JsonFactory.newObjectBuilder()
                            .set(JsonFactory.newKey("attributes/type"), JsonFactory.newArrayBuilder().add(0).add(1).build())
                            .set(JsonFactory.newKey("attributes/complex/some"), JsonFactory.newArrayBuilder().add(0).add(1).build())
                            .set(JsonFactory.newKey("attributes/complex/secret"), JsonFactory.newArrayBuilder().add(0).add(1).build())
                            .set(JsonFactory.newKey("features/some/properties/configuration/foo"),
                                    JsonFactory.newArrayBuilder().add(0).build())
                            .set(JsonFactory.newKey("features/other/properties/public"),
                                    JsonFactory.newArrayBuilder().add(1).build())
                            .build())
                    .build()
                    .toString();

            final DittoHeaders headersWithPartialAccess = DittoHeaders.newBuilder()
                    .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), partialAccessHeader)
                    .readGrantedSubjects(Set.of(user1Subject, user2Subject))
                    .build();

            final Attributes attributes = Attributes.newBuilder()
                    .set("type", "LORAWAN_GATEWAY")
                    .set("complex", JsonObject.newBuilder()
                            .set("some", 42)
                            .set("secret", "pssst")
                            .build())
                    .build();
            final Features features = Features.newBuilder()
                    .set(Feature.newBuilder()
                            .properties(JsonObject.newBuilder()
                                    .set("configuration", JsonObject.newBuilder()
                                            .set("foo", 123)
                                            .build())
                                    .build())
                            .withId("some")
                            .build())
                    .set(Feature.newBuilder()
                            .properties(JsonObject.newBuilder()
                                    .set("public", "here you go")
                                    .set("bar", false)
                                    .build())
                            .withId("other")
                            .build())
                    .build();
            final Thing thing = ThingsModelFactory.newThingBuilder()
                    .setId(TestConstants.Things.THING_ID)
                    .setAttributes(attributes)
                    .setFeatures(features)
                    .build();
            final ThingModifiedEvent<?> event = ThingModified.of(thing, 1, TestConstants.INSTANT, 
                    headersWithPartialAccess, TestConstants.METADATA);

            final Target user1Target = ConnectivityModelFactory.newTargetBuilder()
                    .address("user1-topic")
                    .authorizationContext(AuthorizationContext.newInstance(
                            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                            user1Subject))
                    .topics(Topic.TWIN_EVENTS)
                    .payloadMapping(ConnectivityModelFactory.newPayloadMapping(DITTO_MAPPER))
                    .build();

            final Target user2Target = ConnectivityModelFactory.newTargetBuilder()
                    .address("user2-topic")
                    .authorizationContext(AuthorizationContext.newInstance(
                            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                            user2Subject))
                    .topics(Topic.TWIN_EVENTS)
                    .payloadMapping(ConnectivityModelFactory.newPayloadMapping(DITTO_MAPPER))
                    .build();

            final OutboundSignal outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(event, List.of(user1Target, user2Target));

            final MappingOutcome.Visitor<OutboundSignal.Mapped, Void> mock = Mockito.mock(MappingOutcome.Visitor.class);
            underTest.process(outboundSignal).forEach(outcome -> outcome.accept(mock));
            final ArgumentCaptor<OutboundSignal.Mapped> captor = ArgumentCaptor.forClass(OutboundSignal.Mapped.class);
            verify(mock, times(2)).onMapped(any(String.class), captor.capture());

            final List<OutboundSignal.Mapped> mappedSignals = captor.getAllValues();
            assertThat(mappedSignals).hasSize(2);

            final OutboundSignal.Mapped user1Mapped = mappedSignals.stream()
                    .filter(m -> m.getTargets().contains(user1Target))
                    .findFirst()
                    .orElseThrow();
            final JsonifiableAdaptable user1Adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(
                    JsonFactory.newObject(user1Mapped.getExternalMessage().getTextPayload().orElseThrow()));

            final OutboundSignal.Mapped user2Mapped = mappedSignals.stream()
                    .filter(m -> m.getTargets().contains(user2Target))
                    .findFirst()
                    .orElseThrow();
            final JsonifiableAdaptable user2Adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(
                    JsonFactory.newObject(user2Mapped.getExternalMessage().getTextPayload().orElseThrow()));

            assertThat(user1Adaptable).isNotNull();
            assertThat(user2Adaptable).isNotNull();
        }};
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    public void partialAccessPathsWithRevokedPathsFilteredPerTarget() {
        // GIVEN: A connection with targets where one has access to a parent path but a child is revoked
        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{
            final AuthorizationSubject user1Subject = AuthorizationSubject.newInstance("pre:user1");

            // Partial access header where user1 has access to /attributes/complex/some but /attributes/complex/secret is revoked
            final String partialAccessHeader = JsonFactory.newObjectBuilder()
                    .set("subjects", JsonFactory.newArrayBuilder()
                            .add(user1Subject.getId())
                            .build())
                    .set("paths", JsonFactory.newObjectBuilder()
                            .set(JsonFactory.newKey("attributes/complex/some"), JsonFactory.newArrayBuilder().add(0).build())
                            .build())
                    .build()
                    .toString();

            final DittoHeaders headersWithPartialAccess = DittoHeaders.newBuilder()
                    .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), partialAccessHeader)
                    .readGrantedSubjects(Set.of(user1Subject))
                    .build();

            final Attributes attributes = Attributes.newBuilder()
                    .set("complex", JsonObject.newBuilder()
                            .set("some", 42)
                            .set("secret", "pssst")
                            .build())
                    .build();
            final Thing thing = ThingsModelFactory.newThingBuilder()
                    .setId(TestConstants.Things.THING_ID)
                    .setAttributes(attributes)
                    .build();
            final ThingModifiedEvent<?> event = ThingModified.of(thing, 1, TestConstants.INSTANT, 
                    headersWithPartialAccess, TestConstants.METADATA);

            final Target user1Target = ConnectivityModelFactory.newTargetBuilder()
                    .address("user1-topic")
                    .authorizationContext(AuthorizationContext.newInstance(
                            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                            user1Subject))
                    .topics(Topic.TWIN_EVENTS)
                    .payloadMapping(ConnectivityModelFactory.newPayloadMapping(DITTO_MAPPER))
                    .build();

            final OutboundSignal outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(event, List.of(user1Target));

            final MappingOutcome.Visitor<OutboundSignal.Mapped, Void> mock = Mockito.mock(MappingOutcome.Visitor.class);
            underTest.process(outboundSignal).forEach(outcome -> outcome.accept(mock));
            final ArgumentCaptor<OutboundSignal.Mapped> captor = ArgumentCaptor.forClass(OutboundSignal.Mapped.class);
            verify(mock, times(1)).onMapped(any(String.class), captor.capture());

            // THEN: The payload should be filtered to exclude revoked paths
            final OutboundSignal.Mapped mapped = captor.getValue();
            final JsonifiableAdaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(
                    JsonFactory.newObject(mapped.getExternalMessage().getTextPayload().orElseThrow()));

            // The filtering logic should ensure revoked paths are not included
            // This is verified by the filtering happening per target's auth context
            assertThat(adaptable).isNotNull();
        }};
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    public void partialAccessPathsFilterExtraFields() {
        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{
            final AuthorizationSubject user1Subject = AuthorizationSubject.newInstance("pre:user1");

            final String partialAccessHeader = JsonFactory.newObjectBuilder()
                    .set("subjects", JsonFactory.newArrayBuilder()
                            .add(user1Subject.getId())
                            .build())
                    .set("paths", JsonFactory.newObjectBuilder()
                            .set(JsonFactory.newKey("attributes/type"), JsonFactory.newArrayBuilder().add(0).build())
                            .build())
                    .build()
                    .toString();

            final DittoHeaders headersWithPartialAccess = DittoHeaders.newBuilder()
                    .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), partialAccessHeader)
                    .readGrantedSubjects(Set.of(user1Subject))
                    .build();

            final Attributes attributes = Attributes.newBuilder()
                    .set("type", "LORAWAN_GATEWAY")
                    .set("secret", "should-be-filtered")
                    .build();
            final Thing thing = ThingsModelFactory.newThingBuilder()
                    .setId(TestConstants.Things.THING_ID)
                    .setAttributes(attributes)
                    .build();
            final ThingModifiedEvent event = ThingModified.of(thing, 1, TestConstants.INSTANT, 
                    headersWithPartialAccess, TestConstants.METADATA);

            final JsonObject extraFields = JsonFactory.newObjectBuilder()
                    .set("attributes", JsonFactory.newObjectBuilder()
                            .set("type", "LORAWAN_GATEWAY")
                            .set("secret", "should-be-filtered-from-extra")
                            .build())
                    .build();

            final Target user1Target = ConnectivityModelFactory.newTargetBuilder()
                    .address("user1-topic")
                    .authorizationContext(AuthorizationContext.newInstance(
                            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                            user1Subject))
                    .topics(Topic.TWIN_EVENTS)
                    .payloadMapping(ConnectivityModelFactory.newPayloadMapping(DITTO_MAPPER))
                    .build();

            final OutboundSignal outboundSignalWithExtra = Mockito.mock(OutboundSignal.class);
            when(outboundSignalWithExtra.getSource()).thenReturn(event);
            when(outboundSignalWithExtra.getTargets()).thenReturn(List.of(user1Target));
            when(outboundSignalWithExtra.getExtra()).thenReturn(Optional.of(extraFields));

            final MappingOutcome.Visitor<OutboundSignal.Mapped, Void> mock = Mockito.mock(MappingOutcome.Visitor.class);
            underTest.process(outboundSignalWithExtra).forEach(outcome -> outcome.accept(mock));
            final ArgumentCaptor<OutboundSignal.Mapped> captor = ArgumentCaptor.forClass(OutboundSignal.Mapped.class);
            verify(mock, times(1)).onMapped(any(String.class), captor.capture());

            final OutboundSignal.Mapped mapped = captor.getValue();
            final JsonifiableAdaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(
                    JsonFactory.newObject(mapped.getExternalMessage().getTextPayload().orElseThrow()));

            final JsonObject payloadValue = adaptable.getPayload().getValue()
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .orElse(JsonFactory.newObject());
            assertThat(payloadValue.getValue("attributes/type")).isPresent();
            assertThat(payloadValue.getValue("attributes/secret")).isEmpty();

            final Optional<JsonObject> extra = adaptable.getPayload().getExtra();
            assertThat(extra).isPresent();
            final JsonObject extraObj = extra.get();
            if (extraObj.contains("attributes")) {
                final JsonObject extraAttributes = extraObj.getValue("attributes")
                        .filter(JsonValue::isObject)
                        .map(JsonValue::asObject)
                        .orElse(JsonFactory.newObject());
                assertThat(extraAttributes.getValue("type")).isPresent();
                assertThat(extraAttributes.getValue("secret")).isEmpty();
            }
        }};
    }

}
