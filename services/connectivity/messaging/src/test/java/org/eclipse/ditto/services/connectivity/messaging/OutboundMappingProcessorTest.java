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
package org.eclipse.ditto.services.connectivity.messaging;

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
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.PayloadMappingDefinition;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.protocol.DittoProtocolAdapterProvider;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.services.utils.protocol.config.ProtocolConfig;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link OutboundMappingProcessor}.
 */
public final class OutboundMappingProcessorTest {

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

    private static ActorSystem actorSystem;
    private static ConnectivityConfig connectivityConfig;
    private static ProtocolAdapterProvider protocolAdapterProvider;
    private static ThreadSafeDittoLoggingAdapter logger;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);

        logger = Mockito.mock(ThreadSafeDittoLoggingAdapter.class);
        when(logger.withMdcEntry(Mockito.any(CharSequence.class), Mockito.nullable(CharSequence.class)))
                .thenReturn(logger);
        when(logger.withCorrelationId(Mockito.nullable(String.class))).thenReturn(logger);
        when(logger.withCorrelationId(Mockito.nullable(WithDittoHeaders.class))).thenReturn(logger);
        when(logger.withCorrelationId(Mockito.nullable(DittoHeaders.class))).thenReturn(logger);
        when(logger.withCorrelationId(Mockito.nullable(CharSequence.class))).thenReturn(logger);

        connectivityConfig =
                DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
        protocolAdapterProvider = new DittoProtocolAdapterProvider(Mockito.mock(ProtocolConfig.class));
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
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

        underTest = OutboundMappingProcessor.of(ConnectionId.of("theConnection"), ConnectionType.AMQP_10,
                payloadMappingDefinition, actorSystem,
                connectivityConfig, protocolAdapterProvider.getProtocolAdapter(null), logger);
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testOutboundMessageEnriched() {
        new TestKit(actorSystem) {{
            final ThingModifiedEvent signal = TestConstants.thingModified(Collections.emptyList());
            final JsonObject extra = JsonObject.newBuilder().set("x", 5).build();
            final OutboundSignal outboundSignal = Mockito.mock(OutboundSignal.class);
            final MappingOutcome.Visitor<OutboundSignal.Mapped, Void> mock = Mockito.mock(MappingOutcome.Visitor.class);
            when(outboundSignal.getExtra()).thenReturn(Optional.of(extra));
            when(outboundSignal.getSource()).thenReturn(signal);
            underTest.process(outboundSignal).forEach(outcome -> outcome.accept(mock));
            final ArgumentCaptor<OutboundSignal.Mapped> captor = ArgumentCaptor.forClass(OutboundSignal.Mapped.class);
            verify(mock, times(1)).onMapped(captor.capture());
            verify(mock, times(0)).onError(any(Exception.class), any(), any());
            verify(mock, times(0)).onDropped(any());

            assertThat(captor.getAllValues()).allSatisfy(em -> assertThat(em.getAdaptable().getPayload().getExtra())
                    .contains(extra));
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
    private void testOutbound(final ThingModifiedEvent<?> signal, final int mapped, final int dropped, final int failed,
            final boolean assertTargets, final Target... targets) {
        new TestKit(actorSystem) {{

            // expect one message per mapper per target
            final List<Target> expectedTargets = Arrays.stream(targets)
                    .flatMap(t -> Stream.generate(() -> t).limit(t.getPayloadMapping().getMappings().size()))
                    .collect(Collectors.toList());

            final OutboundSignal outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(signal, Arrays.asList(targets));

            final MappingOutcome.Visitor<OutboundSignal.Mapped, Void> mock = Mockito.mock(MappingOutcome.Visitor.class);
            underTest.process(outboundSignal).forEach(outcome -> outcome.accept(mock));
            final ArgumentCaptor<OutboundSignal.Mapped> captor = ArgumentCaptor.forClass(OutboundSignal.Mapped.class);
            verify(mock, times(mapped)).onMapped(captor.capture());
            verify(mock, times(failed)).onError(any(Exception.class), any(), any());
            verify(mock, times(dropped)).onDropped(any());

            assertThat(captor.getAllValues()).allSatisfy(em ->
                    assertThat(em.getExternalMessage().getTextPayload())
                            .contains(TestConstants.signalToDittoProtocolJsonString(signal)));

            if (assertTargets && mapped > 0) {
                assertThat(captor.getAllValues()
                        .stream()
                        .flatMap(mapped -> mapped.getTargets().stream())
                        .collect(Collectors.toList()))
                        .containsExactlyInAnyOrderElementsOf(expectedTargets);
            }
        }};
    }

}
