/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.PayloadMappingDefinition;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.mapping.DittoMessageMapper;
import org.eclipse.ditto.edge.service.headers.DittoHeadersValidator;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.javadsl.Sink;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

public abstract class AbstractConsumerActorTest<M> {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    protected static final Config CONFIG = ConfigFactory.load("test");
    private static final Connection CONNECTION = TestConstants.createConnection();
    private static final ConnectionId CONNECTION_ID = CONNECTION.getId();
    private static final FiniteDuration ONE_SECOND = FiniteDuration.apply(1, TimeUnit.SECONDS);
    protected static final Map.Entry<String, String> REPLY_TO_HEADER =
            TestConstants.header("reply-to", "reply-to-address");
    protected static final Enforcement ENFORCEMENT =
            ConnectivityModelFactory.newEnforcement("{{ header:device_id }}", "{{ thing:id }}");

    protected static ActorSystem actorSystem;
    protected static ProtocolAdapterProvider protocolAdapterProvider;
    protected TestProbe connectionActorProbe;

    @Rule
    public TestName name = new TestName();

    @Before
    public void init() {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
        protocolAdapterProvider = ProtocolAdapterProvider.load(TestConstants.PROTOCOL_CONFIG, actorSystem);
        connectionActorProbe = TestProbe.apply("connectionActor", actorSystem);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestConstants.disableLogging(actorSystem);
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void testInboundMessageSucceeds() {
        testInboundMessage(TestConstants.header("device_id", TestConstants.Things.THING_ID), true, s -> {}, o -> {});
    }

    @Test
    public void testInboundMessageWitMultipleMappingsSucceeds() {
        testInboundMessage(TestConstants.header("device_id", TestConstants.Things.THING_ID), 2, 0,
                s -> {}, o -> {}, ConnectivityModelFactory.newPayloadMapping("ditto", "ditto"));
    }

    @Test
    public void testInboundMessageWithMultipleMappingsOneFailingSucceeds() {
        testInboundMessage(TestConstants.header("device_id", TestConstants.Things.THING_ID), 1, 1,
                s -> {}, o -> {}, ConnectivityModelFactory.newPayloadMapping("faulty", "ditto"));
    }

    @Test
    public void testInboundMessageWithDuplicatingMapperSucceeds() {
        testInboundMessage(TestConstants.header("device_id", TestConstants.Things.THING_ID), 3, 0,
                s -> {}, o -> {}, ConnectivityModelFactory.newPayloadMapping("duplicator", "ditto"));
    }

    @Test
    public void testInboundMessageFails() {
        TestConstants.disableLogging(actorSystem);
        testInboundMessage(TestConstants.header("device_id", "_invalid"), false, s -> {}, outboundSignal -> {
            final ConnectionSignalIdEnforcementFailedException exception =
                    ConnectionSignalIdEnforcementFailedException.fromMessage(
                            outboundSignal.getExternalMessage().getTextPayload().orElse(""),
                            outboundSignal.getSource().getDittoHeaders());
            assertThat(exception.getErrorCode()).isEqualTo(ConnectionSignalIdEnforcementFailedException.ERROR_CODE);
            assertThat(exception.getDittoHeaders()).contains(REPLY_TO_HEADER);
        });
    }

    @Test
    public void testInboundMessageFailsIfHeaderIsMissing() {
        TestConstants.disableLogging(actorSystem);
        testInboundMessage(TestConstants.header("some", "header"), false, s -> {}, outboundSignal -> {
            final UnresolvedPlaceholderException exception =
                    UnresolvedPlaceholderException.fromMessage(
                            outboundSignal.getExternalMessage().getTextPayload().orElse(""),
                            outboundSignal.getSource().getDittoHeaders());
            assertThat(exception.getErrorCode()).isEqualTo(UnresolvedPlaceholderException.ERROR_CODE);
            assertThat(exception.getDittoHeaders()).contains(REPLY_TO_HEADER);
            assertThat(exception.getMessage()).contains("header:device_id");
        });
    }

    @Test
    public void testInboundMessageWithHeaderMapping() {
        testHeaderMapping();
    }

    protected abstract void testHeaderMapping();

    @Test
    public void testInboundMessageWithHeaderMappingThrowsUnresolvedPlaceholderException() {
        TestConstants.disableLogging(actorSystem);
        testInboundMessage(TestConstants.header("useless", "header"), false,
                msg -> {},
                response -> {
                    assertThat(response.getSource().getDittoHeaders()).contains(REPLY_TO_HEADER);
                    assertThat(response.getExternalMessage().getTextPayload().orElse(""))
                            .contains("{{ header:device_id }}");
                }
        );
    }

    protected abstract Props getConsumerActorProps(final Sink<Object, NotUsed> inboundMappingSink,
            final PayloadMapping payloadMapping);

    protected abstract M getInboundMessage(final String payload, final Map.Entry<String, Object> header);

    protected void consumeMessage(final ActorRef consumerActor, final M inboundMessage, final ActorRef sender) {
        consumerActor.tell(inboundMessage, sender);
    }

    protected void testInboundMessage(final Map.Entry<String, Object> header,
            final boolean isForwardedToConcierge,
            final Consumer<Signal<?>> verifySignal,
            final Consumer<OutboundSignal.Mapped> verifyResponse) {
        testInboundMessage(header, isForwardedToConcierge ? 1 : 0, isForwardedToConcierge ? 0 : 1, verifySignal,
                verifyResponse, ConnectivityModelFactory.emptyPayloadMapping());
    }

    private void testInboundMessage(final Map.Entry<String, Object> header,
            final int forwardedToConcierge,
            final int respondedToCaller,
            final Consumer<Signal<?>> verifySignal,
            final Consumer<OutboundSignal.Mapped> verifyResponse,
            final PayloadMapping payloadMapping
    ) {
        new TestKit(actorSystem) {{
            final TestProbe sender = TestProbe.apply(actorSystem);
            final TestProbe proxyActor = TestProbe.apply(actorSystem);
            final TestProbe clientActor = TestProbe.apply(actorSystem);

            final Sink<Object, NotUsed> inboundMappingSink =
                    setupInboundMappingSink(clientActor.ref(), proxyActor.ref());

            final ActorRef underTest = actorSystem.actorOf(getConsumerActorProps(inboundMappingSink, payloadMapping));

            consumeMessage(underTest, getInboundMessage(TestConstants.modifyThing(), header), sender.ref());

            if (forwardedToConcierge >= 0) {
                for (int i = 0; i < forwardedToConcierge; i++) {
                    final ModifyThing modifyThing = proxyActor.expectMsgClass(ModifyThing.class);
                    assertThat((CharSequence) modifyThing.getEntityId()).isEqualTo(TestConstants.Things.THING_ID);
                    verifySignal.accept(modifyThing);
                }
            } else {
                proxyActor.expectNoMessage(ONE_SECOND);
            }

            if (respondedToCaller >= 0) {
                for (int i = 0; i < respondedToCaller; i++) {
                    final BaseClientActor.PublishMappedMessage publishMappedMessage =
                            clientActor.expectMsgClass(BaseClientActor.PublishMappedMessage.class);
                    verifyResponse.accept(publishMappedMessage.getOutboundSignal().first());
                }
            } else {
                clientActor.expectNoMessage(ONE_SECOND);
            }

            stopConsumerActor(underTest);
        }};
    }

    protected void stopConsumerActor(final ActorRef underTest) {
        // implement in subclasses if required
    }

    protected Sink<Object, NotUsed> setupInboundMappingSink(final ActorRef clientActor, final ActorRef proxyActor) {
        final Map<String, MappingContext> mappings = new HashMap<>();
        mappings.put("ditto", DittoMessageMapper.CONTEXT);
        mappings.put("faulty", FaultyMessageMapper.CONTEXT);
        mappings.put("duplicator", DuplicatingMessageMapper.CONTEXT);

        final PayloadMappingDefinition payloadMappingDefinition =
                ConnectivityModelFactory.newPayloadMappingDefinition(mappings);

        final ConnectivityConfig connectivityConfig = TestConstants.CONNECTIVITY_CONFIG;
        final ThreadSafeDittoLoggingAdapter logger = Mockito.mock(ThreadSafeDittoLoggingAdapter.class);
        when(logger.withMdcEntry(Mockito.any(CharSequence.class), Mockito.nullable(CharSequence.class)))
                .thenReturn(logger);
        when(logger.withCorrelationId(Mockito.any(DittoHeaders.class)))
                .thenReturn(logger);
        when(logger.withCorrelationId(Mockito.nullable(CharSequence.class)))
                .thenReturn(logger);
        when(logger.withCorrelationId(Mockito.any(WithDittoHeaders.class)))
                .thenReturn(logger);
        final ProtocolAdapter protocolAdapter = protocolAdapterProvider.getProtocolAdapter(null);
        final var connection = CONNECTION.toBuilder().payloadMappingDefinition(payloadMappingDefinition).build();
        final InboundMappingProcessor inboundMappingProcessor =
                InboundMappingProcessor.of(connection, connectivityConfig, actorSystem, protocolAdapter, logger);
        final OutboundMappingProcessor outboundMappingProcessor =
                OutboundMappingProcessor.of(connection, connectivityConfig, actorSystem, protocolAdapter, logger);
        final Props props = OutboundMappingProcessorActor.props(clientActor, List.of(outboundMappingProcessor),
                CONNECTION, connectivityConfig, 43);
        final ActorRef outboundProcessorActor = actorSystem.actorOf(props,
                OutboundMappingProcessorActor.ACTOR_NAME + "-" + name.getMethodName());

        final var config = actorSystem.settings().config();
        final Sink<Object, NotUsed> inboundDispatchingSink = InboundDispatchingSink.createSink(CONNECTION,
                protocolAdapter.headerTranslator(),
                ActorSelection.apply(proxyActor, ""),
                connectionActorProbe.ref(),
                outboundProcessorActor,
                TestProbe.apply(actorSystem).ref(),
                actorSystem,
                ConnectivityConfig.of(config),
                DittoHeadersValidator.get(actorSystem, ScopedConfig.dittoExtension(config)),
                null);

        return InboundMappingSink.createSink(List.of(inboundMappingProcessor),
                CONNECTION_ID,
                99,
                inboundDispatchingSink,
                connectivityConfig.getMappingConfig(),
                null,
                actorSystem.dispatchers().defaultGlobalDispatcher());
    }

}
