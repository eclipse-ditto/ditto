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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel.TWIN_PERSISTED;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.disableLogging;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.header;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.PayloadMappingDefinition;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor.PublishMappedMessage;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

public abstract class AbstractConsumerActorTest<M> {

    private static final Config CONFIG = ConfigFactory.load("test");
    private static final Connection CONNECTION = TestConstants.createConnection();
    private static final ConnectionId CONNECTION_ID = CONNECTION.getId();
    private static final FiniteDuration ONE_SECOND = FiniteDuration.apply(1, TimeUnit.SECONDS);
    private static final Set<AcknowledgementLabel> acks = new HashSet<>(
            Collections.singletonList(AcknowledgementLabel.of("twin-persisted")));
    protected static final Map.Entry<String, String> REPLY_TO_HEADER = header("reply-to", "reply-to-address");
    protected static final Map.Entry<String, Object> REQUESTED_ACKS_HEADER =
            header("requested-acks", JsonValue.of("twin-persisted"));
    protected static final Enforcement ENFORCEMENT =
            ConnectivityModelFactory.newEnforcement("{{ header:device_id }}", "{{ thing:id }}");

    protected static ActorSystem actorSystem;
    protected static ProtocolAdapterProvider protocolAdapterProvider;
    protected TestProbe connectionActorProbe;

    @Rule
    public TestName name = new TestName();

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
        protocolAdapterProvider = ProtocolAdapterProvider.load(TestConstants.PROTOCOL_CONFIG, actorSystem);
    }

    @Before
    public void init() {
        connectionActorProbe = TestProbe.apply("connectionActor", actorSystem);
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    public void testInboundMessageSucceeds() {
        testInboundMessage(header("device_id", TestConstants.Things.THING_ID), true, s -> {}, o -> {});
    }


    @Test
    public void testInboundMessageWitMultipleMappingsSucceeds() {
        testInboundMessage(header("device_id", TestConstants.Things.THING_ID), 2, 0, s -> {}, o -> {},
                ConnectivityModelFactory.newPayloadMapping("ditto", "ditto"));
    }

    @Test
    public void testPositiveSourceAcknowledgementSettlement() throws Exception {
        testSourceAcknowledgementSettlement(true, modifyThing ->
                ModifyThingResponse.modified(modifyThing.getThingEntityId(), modifyThing.getDittoHeaders())
        );
    }

    @Test
    public void testNegativeSourceAcknowledgementSettlement() throws Exception {
        testSourceAcknowledgementSettlement(false, modifyThing ->
                ThingNotAccessibleException.newBuilder(modifyThing.getThingEntityId())
                        .dittoHeaders(modifyThing.getDittoHeaders())
                        .build()
        );
    }

    private void testSourceAcknowledgementSettlement(final boolean isSuccessExpected,
            final Function<ModifyThing, Object> responseCreator) throws Exception {

        new TestKit(actorSystem) {{
            final TestProbe sender = TestProbe.apply(actorSystem);
            final TestProbe concierge = TestProbe.apply(actorSystem);
            final TestProbe clientActor = TestProbe.apply(actorSystem);

            final ActorRef mappingActor = setupMessageMappingProcessorActor(clientActor.ref(), concierge.ref());
            final ActorRef underTest = actorSystem.actorOf(getConsumerActorProps(mappingActor, acks));

            underTest.tell(getInboundMessage(header("device_id", TestConstants.Things.THING_ID), REQUESTED_ACKS_HEADER),
                    sender.ref());

            final ModifyThing modifyThing = concierge.expectMsgClass(ModifyThing.class);
            assertThat((CharSequence) modifyThing.getThingEntityId()).isEqualTo(TestConstants.Things.THING_ID);
            concierge.reply(responseCreator.apply(modifyThing));

            final PublishMappedMessage publishedMessage = clientActor.expectMsgClass(PublishMappedMessage.class);
            assertThat(publishedMessage.getOutboundSignal()
                    .first()
                    .getExternalMessage()
                    .getInternalHeaders()
                    .getAcknowledgementRequests()
                    .stream()
                    .map(AcknowledgementRequest::getLabel)
                    .collect(Collectors.toList())
            ).containsExactly(TWIN_PERSISTED);

            verifyMessageSettlement(isSuccessExpected);
        }};
    }

    @Test
    public void testInboundMessageWithMultipleMappingsOneFailingSucceeds() {
        testInboundMessage(header("device_id", TestConstants.Things.THING_ID), 1, 1, s -> {}, o -> {},
                ConnectivityModelFactory.newPayloadMapping("faulty", "ditto"));
    }

    @Test
    public void testInboundMessageWithDuplicatingMapperSucceeds() {
        testInboundMessage(header("device_id", TestConstants.Things.THING_ID), 3, 0, s -> {}, o -> {},
                ConnectivityModelFactory.newPayloadMapping("duplicator", "ditto"));
    }

    @Test
    public void testInboundMessageFails() {
        disableLogging(actorSystem);
        testInboundMessage(header("device_id", "_invalid"), false, s -> {}, outboundSignal -> {
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
        disableLogging(actorSystem);
        testInboundMessage(header("some", "header"), false, s -> {}, outboundSignal -> {
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
        testInboundMessage(header("device_id", TestConstants.Things.THING_ID), true, msg -> {
            assertThat(msg.getDittoHeaders()).containsEntry("eclipse", "ditto");
            assertThat(msg.getDittoHeaders()).containsEntry("thing_id", TestConstants.Things.THING_ID.toString());
            assertThat(msg.getDittoHeaders()).containsEntry("device_id", TestConstants.Things.THING_ID.toString());
            assertThat(msg.getDittoHeaders()).containsEntry("prefixed_thing_id",
                    "some.prefix." + TestConstants.Things.THING_ID);
            assertThat(msg.getDittoHeaders()).containsEntry("suffixed_thing_id",
                    TestConstants.Things.THING_ID + ".some.suffix");
        }, response -> fail("not expected"));
    }

    @Test
    public void testInboundMessageWithHeaderMappingThrowsUnresolvedPlaceholderException() {
        disableLogging(actorSystem);
        testInboundMessage(header("useless", "header"), false,
                msg -> {},
                response -> {
                    assertThat(response.getSource().getDittoHeaders()).contains(REPLY_TO_HEADER);
                    assertThat(response.getExternalMessage().getTextPayload().orElse(""))
                            .contains("{{ header:device_id }}");
                }
        );
    }

    protected abstract Props getConsumerActorProps(final ActorRef mappingActor, final PayloadMapping payloadMapping);

    protected abstract Props getConsumerActorProps(final ActorRef mappingActor,
            final Set<AcknowledgementLabel> acknowledgements);

    protected abstract M getInboundMessage(final Map.Entry<String, Object> header);

    protected abstract M getInboundMessage(final Map.Entry<String, Object> header,
            final Map.Entry<String, Object> header2);

    protected abstract void verifyMessageSettlement(boolean isSuccessExpected) throws Exception;

    private void testInboundMessage(final Map.Entry<String, Object> header,
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
            final TestProbe concierge = TestProbe.apply(actorSystem);
            final TestProbe clientActor = TestProbe.apply(actorSystem);

            final ActorRef mappingActor = setupMessageMappingProcessorActor(clientActor.ref(), concierge.ref());

            final ActorRef underTest = actorSystem.actorOf(getConsumerActorProps(mappingActor, payloadMapping));

            underTest.tell(getInboundMessage(header), sender.ref());

            if (forwardedToConcierge >= 0) {
                for (int i = 0; i < forwardedToConcierge; i++) {
                    final ModifyThing modifyThing = concierge.expectMsgClass(ModifyThing.class);
                    assertThat((CharSequence) modifyThing.getThingEntityId()).isEqualTo(TestConstants.Things.THING_ID);
                    verifySignal.accept(modifyThing);
                }
            } else {
                concierge.expectNoMessage(ONE_SECOND);
            }

            if (respondedToCaller >= 0) {
                for (int i = 0; i < respondedToCaller; i++) {
                    final PublishMappedMessage publishMappedMessage =
                            clientActor.expectMsgClass(PublishMappedMessage.class);
                    verifyResponse.accept(publishMappedMessage.getOutboundSignal().first());
                }
            } else {
                clientActor.expectNoMessage(ONE_SECOND);
            }
        }};
    }

    private ActorRef setupMessageMappingProcessorActor(final ActorRef clientActor,
            final ActorRef conciergeForwarderActor) {

        final Map<String, MappingContext> mappings = new HashMap<>();
        mappings.put("ditto", DittoMessageMapper.CONTEXT);
        mappings.put("faulty", FaultyMessageMapper.CONTEXT);
        mappings.put("duplicator", DuplicatingMessageMapper.CONTEXT);

        final PayloadMappingDefinition payloadMappingDefinition =
                ConnectivityModelFactory.newPayloadMappingDefinition(mappings);

        final ConnectivityConfig connectivityConfig = TestConstants.CONNECTIVITY_CONFIG;
        final DittoDiagnosticLoggingAdapter logger = Mockito.mock(DittoDiagnosticLoggingAdapter.class);
        Mockito.when(logger.withCorrelationId(Mockito.any(DittoHeaders.class)))
                .thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(CharSequence.class)))
                .thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(WithDittoHeaders.class)))
                .thenReturn(logger);
        final MessageMappingProcessor mappingProcessor =
                MessageMappingProcessor.of(CONNECTION_ID, payloadMappingDefinition, actorSystem,
                        connectivityConfig, protocolAdapterProvider, logger);
        final Props messageMappingProcessorProps =
                MessageMappingProcessorActor.props(conciergeForwarderActor, clientActor, mappingProcessor,
                        CONNECTION, connectionActorProbe.ref(), 43);

        return actorSystem.actorOf(messageMappingProcessorProps,
                MessageMappingProcessorActor.ACTOR_NAME + "-" + name.getMethodName());
    }
}
