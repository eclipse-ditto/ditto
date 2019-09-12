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

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.eclipse.ditto.services.connectivity.messaging.InitializationState.ResourceReady;
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
import akka.routing.Broadcast;
import akka.routing.ConsistentHashingPool;
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
                    final UnresolvedPlaceholderException exception =
                            UnresolvedPlaceholderException.fromMessage(
                                    response.getExternalMessage().getTextPayload().orElse(""),
                                    response.getSource().getDittoHeaders());
                    assertThat(exception.getErrorCode()).isEqualTo(UnresolvedPlaceholderException.ERROR_CODE);
                    assertThat(exception.getDittoHeaders()).contains(REPLY_TO_HEADER);
                    assertThat(exception.getMessage()).contains("{{ header:device_id }}");
                }
        );
    }

    protected abstract Props getConsumerActorProps(final ActorRef mappingActor);

    protected abstract M getInboundMessage(final Map.Entry<String, Object> header);

    private void testInboundMessage(final Map.Entry<String, Object> header,
            final boolean isForwardedToConcierge,
            final Consumer<Signal<?>> verifySignal,
            final Consumer<OutboundSignal.WithExternalMessage> verifyResponse) {

        new TestKit(actorSystem) {{
            final TestProbe sender = TestProbe.apply(actorSystem);
            final TestProbe concierge = TestProbe.apply(actorSystem);
            final TestProbe publisher = TestProbe.apply(actorSystem);

            final ActorRef mappingActor = setupMessageMappingProcessorActor(getRef(), publisher.ref(), concierge.ref());

            for (int i = 0; i < PROCESSOR_POOL_SIZE; i++) {
                expectMsgClass(ResourceReady.class);
            }

            final ActorRef underTest = actorSystem.actorOf(getConsumerActorProps(mappingActor));

            underTest.tell(getInboundMessage(header), sender.ref());

            if (isForwardedToConcierge) {
                publisher.expectNoMessage(ONE_SECOND);
                final ModifyThing modifyThing = concierge.expectMsgClass(ModifyThing.class);
                assertThat((CharSequence) modifyThing.getThingEntityId()).isEqualTo(TestConstants.Things.THING_ID);
                verifySignal.accept(modifyThing);
            } else {
                concierge.expectNoMessage(ONE_SECOND);
                final OutboundSignal.WithExternalMessage outboundSignal =
                        publisher.expectMsgClass(OutboundSignal.WithExternalMessage.class);
                verifyResponse.accept(outboundSignal);
            }
        }};
    }

    private ActorRef setupMessageMappingProcessorActor(final ActorRef testRef, final ActorRef publisher,
            final ActorRef conciergeForwarderActor) {
        final ConnectivityConfig connectivityConfig = TestConstants.CONNECTIVITY_CONFIG;
        final MessageMappingProcessor mappingProcessor = MessageMappingProcessor.of(CONNECTION_ID, null, actorSystem,
                connectivityConfig, protocolAdapterProvider, Mockito.mock(DiagnosticLoggingAdapter.class));
        final Props messageMappingProcessorProps =
                MessageMappingProcessorActor.props(conciergeForwarderActor, mappingProcessor, CONNECTION_ID);

        final ActorRef mappingActor = actorSystem.actorOf(new ConsistentHashingPool(PROCESSOR_POOL_SIZE)
                        .withDispatcher("message-mapping-processor-dispatcher")
                        .props(messageMappingProcessorProps),
                MessageMappingProcessorActor.ACTOR_NAME + "-" + name.getMethodName());
        mappingActor.tell(new Broadcast(ResourceReady.publisherReady(publisher)), testRef);
        return mappingActor;
    }

}
