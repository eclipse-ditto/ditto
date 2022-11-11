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

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
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
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.protocol.DittoProtocolAdapterProvider;
import org.eclipse.ditto.internal.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.internal.utils.protocol.config.ProtocolConfig;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import akka.testkit.javadsl.TestKit;

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
          expect 6 mappings:
           - 3 targets with 1 mapper  (can be grouped together, mapping is done once)  -> 1 message (with 3 targets)
           - 2 targets with 2 mappers (can be grouped together, mapping is done twice) -> 2 messages (with 2 targets)
           - 1 target  with 3 mappers (no grouping, mapping is done three times)       -> 3 messages (with 1 target)
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
            final ThingModifiedEvent signal = TestConstants.thingModified(Collections.emptyList());
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
            ThingModifiedEvent signal = TestConstants.thingModified(Collections.emptyList());
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
                                    AuthorizationSubject.newInstance("issuer:subject")))
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
            final AcknowledgementLabel customAckLabel = AcknowledgementLabel.of("custom:ack");
            final AcknowledgementLabel targetIssuedAckLabel = AcknowledgementLabel.of("issued:ack");
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .channel(TopicPath.Channel.LIVE.getName())
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
                                    AuthorizationSubject.newInstance("issuer:subject")))
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
        testOutbound(TestConstants.thingModifiedWithCor(Collections.emptyList()),
                2, 0, 0, false, targetWithMapping(DUPLICATING_MAPPER));
    }

    @Test
    public void testOutboundMappingFails() {
        testOutbound(0, 0, 1, targetWithMapping(FAILING_MAPPER));
    }

    @Test
    public void testOutboundMessageDroppedFailedMappedDuplicated() {
        testOutbound(TestConstants.thingModifiedWithCor(Collections.emptyList()),
                2 /* duplicated */ + 1 /* mapped */, 1, 1, false,
                targetWithMapping(DROPPING_MAPPER, FAILING_MAPPER, DITTO_MAPPER, DUPLICATING_MAPPER));
    }

    private static Target targetWithMapping(final String... mappings) {
        return ConnectivityModelFactory.newTargetBuilder(TestConstants.Targets.TWIN_TARGET)
                .address(UUID.randomUUID().toString())
                .payloadMapping(ConnectivityModelFactory.newPayloadMapping(mappings))
                .build();
    }

    private void testOutbound(final int mapped, final int dropped, final int failed, final Target... targets) {
        testOutbound(TestConstants.thingModified(Collections.emptyList()), mapped, dropped, failed, true, targets);
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
        return adaptable.setDittoHeaders(adaptable.getDittoHeaders().toBuilder().correlationId(null).build());
    }

}
