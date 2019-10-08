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

/* Copyright (c) 2011-2018 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.protocol.DittoProtocolAdapterProvider;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.services.utils.protocol.config.ProtocolConfig;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import akka.actor.ActorSystem;
import akka.event.DiagnosticLoggingAdapter;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link MessageMappingProcessor}.
 */
public class MessageMappingProcessorTest {

    private static ActorSystem actorSystem;
    private static ConnectivityConfig connectivityConfig;
    private static ProtocolAdapterProvider protocolAdapterProvider;
    private static DiagnosticLoggingAdapter log;

    private MessageMappingProcessor underTest;

    private static final String DITTO_MAPPER = "ditto";
    private static final String DROPPING_MAPPER = "dropping";
    private static final String FAILING_MAPPER = "faulty";
    private static final String DUPLICATING_MAPPER = "duplicating";

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);

        log = Mockito.mock(DiagnosticLoggingAdapter.class);

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
        mappings.put(FAILING_MAPPER, FaultyMessageMapper.CONTEXT);
        mappings.put(DROPPING_MAPPER, DroppingMessageMapper.CONTEXT);
        mappings.put(DUPLICATING_MAPPER, DuplicatingMessageMapper.CONTEXT);
        underTest =
                MessageMappingProcessor.of(ConnectionId.of("theConnection"), mappings, actorSystem, connectivityConfig,
                        protocolAdapterProvider, log);
    }

    @Test
    public void testOutboundMessageMapped() {
        testOutbound(1, 0, 0);
    }

    @Test
    public void testOutboundMessageDropped() {
        testOutbound(0, 1, 0, targetWithMapping(DROPPING_MAPPER));
    }

    @Test
    public void testOutboundMessageDuplicated() {
        testOutbound(2, 0, 0, targetWithMapping(DUPLICATING_MAPPER));
    }

    @Test
    public void testOutboundMappingFails() {
        testOutbound(0, 0, 1, targetWithMapping(FAILING_MAPPER));
    }

    @Test
    public void testOutboundMessageDroppedFailedMappedDuplicated() {
        testOutbound(2 /* duplicated */ + 1 /* mapped */, 1, 1,
                targetWithMapping(DROPPING_MAPPER, FAILING_MAPPER, DITTO_MAPPER, DUPLICATING_MAPPER));
    }

    @Test
    public void testInboundMessageMapped() {
        testInbound(1, 0, 0);
    }

    @Test
    public void testInboundMessageDropped() {
        testInbound(0, 1, 0, DROPPING_MAPPER);
    }

    @Test
    public void testInboundMessageDuplicated() {
        testInbound(2, 0, 0, DUPLICATING_MAPPER);
    }

    @Test
    public void testInboundMappingFails() {
        testInbound(0, 0, 1, FAILING_MAPPER);
    }

    @Test
    public void testInboundMessageDroppedFailedMappedDuplicated() {
        testInbound(2 /* duplicated */ + 1 /* mapped */, 1, 1,
                DROPPING_MAPPER, FAILING_MAPPER, DITTO_MAPPER, DUPLICATING_MAPPER);
    }

    private static Target targetWithMapping(final String... mappings) {
        return ConnectivityModelFactory.newTargetBuilder(TestConstants.Targets.TWIN_TARGET)
                .mapping(Arrays.asList(mappings))
                .build();
    }

    private void testOutbound(final int mapped, final int dropped, final int failed, final Target... targets) {
        new TestKit(actorSystem) {{
            final ThingModifiedEvent signal = TestConstants.thingModified(Collections.emptyList());
            final OutboundSignal outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(signal, Arrays.asList(targets));
            final MappingResultHandler<ExternalMessage> mock = Mockito.mock(MappingResultHandler.class);
            underTest.process(outboundSignal, mock);
            final ArgumentCaptor<ExternalMessage> captor = ArgumentCaptor.forClass(ExternalMessage.class);
            verify(mock, times(mapped)).onMessageMapped(captor.capture());
            verify(mock, times(failed)).onException(any(Exception.class));
            verify(mock, times(dropped)).onMessageDropped();

            assertThat(captor.getAllValues()).allSatisfy(em -> assertThat(em.getTextPayload()).contains(
                    TestConstants.signalToDittoProtocolJsonString(signal)));
        }};
    }

    private void testInbound(final int mapped, final int dropped, final int failed, final String... mappers) {
        new TestKit(actorSystem) {{
            final ExternalMessage externalMessage = ExternalMessageFactory
                    .newExternalMessageBuilder(Collections.emptyMap())
                    .withText(TestConstants.modifyThing())
                    .withPayloadMapping(Arrays.asList(mappers))
                    .build();
            final MappingResultHandler<MappedInboundExternalMessage> mock = Mockito.mock(MappingResultHandler.class);
            underTest.process(externalMessage, mock);
            final ArgumentCaptor<MappedInboundExternalMessage> captor =
                    ArgumentCaptor.forClass(MappedInboundExternalMessage.class);
            verify(mock, times(mapped)).onMessageMapped(captor.capture());
            verify(mock, times(failed)).onException(any(Exception.class));
            verify(mock, times(dropped)).onMessageDropped();

            assertThat(captor.getAllValues()).allSatisfy(mapped -> {
                assertThat(mapped.getSignal().getDittoHeaders()).containsEntry(
                        DittoHeaderDefinition.CORRELATION_ID.getKey(),
                        TestConstants.CORRELATION_ID);
                assertThat((Object) mapped.getSignal().getEntityId()).isEqualTo(TestConstants.Things.THING_ID);
                assertThat(mapped.getSignal()).isInstanceOf(ModifyThing.class);
            });
        }};
    }
}