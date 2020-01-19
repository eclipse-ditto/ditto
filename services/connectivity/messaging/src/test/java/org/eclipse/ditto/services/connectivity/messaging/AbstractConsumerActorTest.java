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
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.disableLogging;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.header;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.junit.AfterClass;
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
import akka.event.DiagnosticLoggingAdapter;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

public abstract class AbstractConsumerActorTest<M> {

    private static final Config CONFIG = ConfigFactory.load("test");
    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();
    private static final FiniteDuration ONE_SECOND = FiniteDuration.apply(1, TimeUnit.SECONDS);
    protected static final Map.Entry<String, String> REPLY_TO_HEADER = header("reply-to", "reply-to-address");
    protected static final Enforcement ENFORCEMENT =
            ConnectivityModelFactory.newEnforcement("{{ header:device_id }}", "{{ thing:id }}");
    private static final int PROCESSOR_POOL_SIZE = 2;

    protected static ActorSystem actorSystem;
    protected static ProtocolAdapterProvider protocolAdapterProvider;

    @Rule
    public TestName name = new TestName();

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
        protocolAdapterProvider = ProtocolAdapterProvider.load(TestConstants.PROTOCOL_CONFIG, actorSystem);
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

    protected abstract M getInboundMessage(final Map.Entry<String, Object> header);

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
                    verifyResponse.accept(publishMappedMessage.getOutboundSignal());
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
        final MessageMappingProcessor mappingProcessor =
                MessageMappingProcessor.of(CONNECTION_ID, payloadMappingDefinition, actorSystem,
                        connectivityConfig, protocolAdapterProvider, Mockito.mock(DiagnosticLoggingAdapter.class));
        final Props messageMappingProcessorProps =
                MessageMappingProcessorActor.props(conciergeForwarderActor, clientActor, mappingProcessor,
                        CONNECTION_ID, 43);

        return actorSystem.actorOf(messageMappingProcessorProps,
                MessageMappingProcessorActor.ACTOR_NAME + "-" + name.getMethodName());
    }

}
