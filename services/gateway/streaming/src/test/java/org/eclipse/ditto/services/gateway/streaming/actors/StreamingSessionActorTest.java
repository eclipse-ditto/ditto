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
package org.eclipse.ditto.services.gateway.streaming.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.model.base.common.BinaryValidationResult;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.model.jwt.JwtInvalidException;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationResultProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtValidator;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.Jwt;
import org.eclipse.ditto.services.models.acks.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayWebsocketSessionClosedException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Attributes;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link StreamingSessionActor}.
 */
public final class StreamingSessionActorTest {

    private static final String CONNECTION_CORRELATION_ID = "connectionCorrelationId";

    private static ActorSystem actorSystem;

    private final List<ActorRef> createdActors = new ArrayList<>();
    private final TestProbe eventResponsePublisherProbe = TestProbe.apply("eventAndResponsePublisher", actorSystem);
    private final TestProbe commandRouterProbe = TestProbe.apply("commandRouter", actorSystem);
    private final TestProbe subscriptionManagerProbe = TestProbe.apply("subscriptionManager", actorSystem);
    private final DittoProtocolSub dittoProtocolSub = Mockito.mock(DittoProtocolSub.class);
    private final JwtValidator jwtValidator = Mockito.mock(JwtValidator.class);

    @BeforeClass
    public static void startActorSystem() {
        actorSystem = ActorSystem.create();
        actorSystem.eventStream().setLogLevel(Attributes.logLevelWarning());
    }

    @AfterClass
    public static void shutdown() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @After
    public void stopActors() {
        createdActors.forEach(actorSystem::stop);
    }

    @Test
    public void invalidJwtClosesStream() {
        Mockito.when(jwtValidator.validate(Mockito.any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        BinaryValidationResult.invalid(JwtInvalidException.newBuilder().build())));

        new TestKit(actorSystem) {{
            final ActorRef underTest = createStreamingSessionActor();

            final Jwt jwt = Jwt.newInstance(getTokenString(), CONNECTION_CORRELATION_ID);

            underTest.tell(jwt, getRef());

            final Status.Failure failure = eventResponsePublisherProbe.expectMsgClass(Status.Failure.class);
            assertThat(failure.cause()).isInstanceOf(GatewayWebsocketSessionClosedException.class);
        }};
    }

    private static String getTokenString() {
        final String header = "{\"header\":\"foo\"}";
        final String payload = "{\"payload\":\"bar\"}";
        final String signature = "{\"signature\":\"baz\"}";
        return base64(header) + "." + base64(payload) + "." + base64(signature);
    }

    private static String base64(final String value) {
        return new String(Base64.getEncoder().encode(value.getBytes()));
    }

    private ActorRef createStreamingSessionActor() {
        final Source<SessionedJsonifiable, SourceQueueWithComplete<SessionedJsonifiable>> source =
                Source.queue(10, OverflowStrategy.fail());
        final SourceQueueWithComplete<SessionedJsonifiable> sourceQueue =
                source.toMat(Sink.actorRef(eventResponsePublisherProbe.ref(), "COMPLETE"), Keep.left())
                        .run(actorSystem);
        final Connect connect = new Connect(sourceQueue, CONNECTION_CORRELATION_ID, "ws", JsonSchemaVersion.V_2,
                null);
        final Props props = StreamingSessionActor.props(connect, dittoProtocolSub, commandRouterProbe.ref(),
                DefaultAcknowledgementConfig.of(ConfigFactory.empty()),
                HeaderTranslator.empty(),
                Props.create(TestProbeForwarder.class, subscriptionManagerProbe),
                jwtValidator,
                Mockito.mock(JwtAuthenticationResultProvider.class));
        final ActorRef createdActor = actorSystem.actorOf(props);
        createdActors.add(createdActor);
        return createdActor;
    }

    private static final class TestProbeForwarder extends AbstractActor {

        final TestProbe testProbe;

        private TestProbeForwarder(final TestProbe testProbe) {
            this.testProbe = testProbe;
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create().matchAny(msg -> testProbe.ref().forward(msg, getContext())).build();
        }
    }

}
