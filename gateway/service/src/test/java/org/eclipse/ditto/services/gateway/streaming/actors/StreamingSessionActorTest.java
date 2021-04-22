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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotDeclaredException;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotUniqueException;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.BinaryValidationResult;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.model.jwt.JwtInvalidException;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationResult;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationResultProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtValidator;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.IncomingSignal;
import org.eclipse.ditto.services.gateway.streaming.Jwt;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StreamingAck;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.models.acks.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.services.utils.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.utils.pubsub.StreamingType;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.AcknowledgementCorrelationIdMissingException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayWebsocketSessionClosedException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayWebsocketSessionExpiredException;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.testkit.TestActor;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link StreamingSessionActor}.
 */
public final class StreamingSessionActorTest {

    @Rule
    public final TestName testName = new TestName();

    private final ActorSystem actorSystem;
    private final DittoProtocolSub mockSub;
    private final TestProbe commandRouterProbe;
    private final SourceQueueWithComplete<SessionedJsonifiable> sourceQueue;
    private final TestSubscriber.Probe<SessionedJsonifiable> sinkProbe;
    private final KillSwitch killSwitch;
    private final JwtValidator mockValidator = mock(JwtValidator.class);
    private final JwtAuthenticationResultProvider mockAuthenticationResultProvider =
            mock(JwtAuthenticationResultProvider.class);
    final AuthorizationSubject authorizationSubject1 = AuthorizationSubject.newInstance("test-subject-1");
    final AuthorizationSubject authorizationSubject2 = AuthorizationSubject.newInstance("test-subject-2");
    final AuthorizationSubject authorizationSubject3 = AuthorizationSubject.newInstance("test-subject-3");
    final AuthorizationContext authorizationContext =
            AuthorizationContext.newInstance(DittoAuthorizationContextType.JWT,
                    authorizationSubject1, authorizationSubject2, authorizationSubject3);

    public StreamingSessionActorTest() {
        actorSystem = ActorSystem.create();
        mockSub = mock(DittoProtocolSub.class);
        when(mockSub.declareAcknowledgementLabels(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        commandRouterProbe = TestProbe.apply("commandRouter", actorSystem);
        final Sink<SessionedJsonifiable, TestSubscriber.Probe<SessionedJsonifiable>> sink =
                TestSink.probe(actorSystem);
        final Source<SessionedJsonifiable, SourceQueueWithComplete<SessionedJsonifiable>> source =
                Source.queue(100, OverflowStrategy.fail());
        final var pair = source.viaMat(KillSwitches.single(), Keep.both()).toMat(sink, Keep.both()).run(actorSystem);
        sourceQueue = pair.first().first();
        sinkProbe = pair.second();
        killSwitch = pair.first().second();
    }

    @After
    public void cleanUp() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void terminateOnStreamFailure() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps()));
            killSwitch.abort(new IllegalStateException("expected exception"));
            expectTerminated(underTest);
        }};
    }

    @Test
    public void completeStreamWhenStopped() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps()));
            underTest.tell(PoisonPill.getInstance(), getRef());
            expectTerminated(underTest);
            sinkProbe.ensureSubscription();
            sinkProbe.expectComplete();
        }};
    }

    @Test
    public void terminateOnAckLabelDeclarationFailure() {
        onDeclareAckLabels(CompletableFuture.failedStage(AcknowledgementLabelNotUniqueException.getInstance()));
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps("ack")));
            expectTerminated(underTest);
        }};
    }

    @Test
    public void sendDeclaredAckForGlobalDispatching() {
        onDeclareAckLabels(CompletableFuture.completedStage(null));
        new TestKit(actorSystem) {{
            final var underTest = watch(actorSystem.actorOf(getProps("ack")));
            final var ack = Acknowledgement.of(AcknowledgementLabel.of("ack"), ThingId.of("thing:id"), HttpStatus.OK,
                    DittoHeaders.newBuilder().correlationId("corr:" + testName.getMethodName()).build());
            underTest.tell(IncomingSignal.of(ack), ActorRef.noSender());
            commandRouterProbe.expectMsg(ack);
        }};
    }

    @Test
    public void sendMalformedAck() {
        onDeclareAckLabels(CompletableFuture.completedStage(null));
        new TestKit(actorSystem) {{
            final var underTest = watch(actorSystem.actorOf(getProps("ack")));
            final var ack = Acknowledgement.of(AcknowledgementLabel.of("ack"), ThingId.of("thing:id"), HttpStatus.OK,
                    DittoHeaders.empty());
            underTest.tell(IncomingSignal.of(ack), ActorRef.noSender());
            final var sessionedJsonifiable = sinkProbe.requestNext();

            assertThat(sessionedJsonifiable.getJsonifiable())
                    .isInstanceOf(AcknowledgementCorrelationIdMissingException.class);
        }};
    }

    @Test
    public void sendNonDeclaredAck() {
        onDeclareAckLabels(CompletableFuture.completedStage(null));
        new TestKit(actorSystem) {{
            final var underTest = watch(actorSystem.actorOf(getProps("ack")));
            final var ack = Acknowledgement.of(AcknowledgementLabel.of("ack2"), ThingId.of("thing:id"), HttpStatus.OK,
                    DittoHeaders.empty());
            underTest.tell(IncomingSignal.of(ack), ActorRef.noSender());
            final var sessionedJsonifiable = sinkProbe.requestNext();

            assertThat(sessionedJsonifiable.getJsonifiable())
                    .isInstanceOf(AcknowledgementLabelNotDeclaredException.class);
        }};
    }

    @Test
    public void acknowledgementRequestsAreRestrictedToDeclaredAcks() {
        onDeclareAckLabels(CompletableFuture.completedStage(null));
        setUpMockForTwinEventsSubscription();
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps("ack")));
            subscribeForTwinEvents(underTest);
            final Signal<?> signal = ThingDeleted.of(ThingId.of("thing:id"), 2L, null, DittoHeaders.newBuilder()
                            .correlationId("corr:" + testName.getMethodName())
                            .readGrantedSubjects(List.of(AuthorizationSubject.newInstance("ditto:ditto")))
                            .acknowledgementRequests(ackRequests("ack", "ack2"))
                            .build(),
                    null);
            underTest.tell(signal, ActorRef.noSender());

            final Signal<?> expectedSignal = signal.setDittoHeaders(signal.getDittoHeaders()
                    .toBuilder()
                    .acknowledgementRequests(ackRequests("ack"))
                    .build());
            assertThat(sinkProbe.requestNext().getJsonifiable()).isEqualTo(expectedSignal);
        }};
    }

    @Test
    public void invalidJwtClosesStream() {
        Mockito.when(mockValidator.validate(Mockito.any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        BinaryValidationResult.invalid(JwtInvalidException.newBuilder().build())));

        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps()));

            final Jwt jwt = Jwt.newInstance(getTokenString(), testName.getMethodName());

            underTest.tell(jwt, getRef());

            assertThat(sinkProbe.expectSubscriptionAndError())
                    .isInstanceOf(GatewayWebsocketSessionClosedException.class)
                    .hasMessageContaining("invalid");
        }};
    }

    @Test
    public void changingAuthorizationContextClosesStream() {
        Mockito.when(mockValidator.validate(Mockito.any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        Mockito.when(mockAuthenticationResultProvider.getAuthenticationResult(any(), any()))
                .thenReturn(JwtAuthenticationResult.successful(
                        DittoHeaders.empty(),
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                                AuthorizationSubject.newInstance("new:auth-subject")),
                        mock(JsonWebToken.class)));

        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps()));

            final Jwt jwt = Jwt.newInstance(getTokenString(), testName.getMethodName());

            underTest.tell(jwt, getRef());

            assertThat(sinkProbe.expectSubscriptionAndError())
                    .isInstanceOf(GatewayWebsocketSessionClosedException.class)
                    .hasMessageContaining("authorization context");
        }};
    }

    @Test
    public void keepingAuthorizationContextDoesNotCloseStream() {
        Mockito.when(mockValidator.validate(Mockito.any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        Mockito.when(mockAuthenticationResultProvider.getAuthenticationResult(any(), any()))
                .thenReturn(JwtAuthenticationResult.successful(DittoHeaders.empty(), authorizationContext,
                        mock(JsonWebToken.class)));

        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps()));

            final Jwt jwt = Jwt.newInstance(getTokenString(), testName.getMethodName());

            underTest.tell(jwt, getRef());

            sinkProbe.expectSubscription();
            sinkProbe.expectNoMessage();
        }};
    }

    @Test
    public void hugeJwtExpirationTimeDoesNotCloseStream() {
        Mockito.when(mockValidator.validate(Mockito.any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        Mockito.when(mockAuthenticationResultProvider.getAuthenticationResult(any(), any()))
                .thenReturn(JwtAuthenticationResult.successful(DittoHeaders.empty(), authorizationContext,
                        mock(JsonWebToken.class)));

        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps()));

            // maximum expiration is too far in the future
            final Jwt jwt =
                    Jwt.newInstance(getTokenString(Instant.now().plus(Duration.ofDays(999))), testName.getMethodName());

            underTest.tell(jwt, getRef());

            sinkProbe.expectSubscription();
            sinkProbe.expectNoMessage();
        }};
    }

    @Test
    public void jwtExpirationTimeClosesStream() {
        Mockito.when(mockValidator.validate(Mockito.any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        Mockito.when(mockAuthenticationResultProvider.getAuthenticationResult(any(), any()))
                .thenReturn(JwtAuthenticationResult.successful(DittoHeaders.empty(), authorizationContext,
                        mock(JsonWebToken.class)));

        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(getProps()));

            final Jwt jwt = Jwt.newInstance(getTokenString(Instant.now()), testName.getMethodName());

            underTest.tell(jwt, getRef());

            sinkProbe.expectSubscription();
            assertThat(sinkProbe.expectError())
                    .isInstanceOf(GatewayWebsocketSessionExpiredException.class)
                    .hasMessageContaining("expired");
        }};
    }

    private static String getTokenString() {
        return getTokenString(Instant.now().plusSeconds(60L));
    }

    private static String getTokenString(final Instant expiration) {
        final String header = "{\"header\":\"foo\"}";
        final String payload = "{\"payload\":\"bar\",\"exp\":" + expiration.getEpochSecond() + "}";
        final String signature = "{\"signature\":\"baz\"}";
        return base64(header) + "." + base64(payload) + "." + base64(signature);
    }

    private static String base64(final String value) {
        return new String(Base64.getEncoder().encode(value.getBytes()));
    }

    private Props getProps(final String... declaredAcks) {
        final Connect connect = getConnect(acks(declaredAcks));
        final AcknowledgementConfig acknowledgementConfig = DefaultAcknowledgementConfig.of(ConfigFactory.empty());
        final HeaderTranslator headerTranslator = HeaderTranslator.empty();
        final Props mockProps = Props.create(Actor.class, () -> new TestActor(new LinkedBlockingDeque<>()));
        return StreamingSessionActor.props(connect, mockSub, commandRouterProbe.ref(), acknowledgementConfig,
                headerTranslator, mockProps, mockValidator, mockAuthenticationResultProvider);
    }

    private void onDeclareAckLabels(final CompletionStage<Void> answer) {
        doAnswer(invocation -> answer).when(mockSub).declareAcknowledgementLabels(any(), any(), any());
    }

    private void setUpMockForTwinEventsSubscription() {
        doAnswer(invocation -> CompletableFuture.completedStage(null))
                .when(mockSub)
                .subscribe(any(), any(), any());
    }

    private void subscribeForTwinEvents(final ActorRef underTest) {
        final AuthorizationContext authorizationContext =
                AuthorizationContext.newInstance(DittoAuthorizationContextType.PRE_AUTHENTICATED_HTTP,
                        AuthorizationSubject.newInstance("ditto:ditto"));
        underTest.tell(StartStreaming.getBuilder(StreamingType.EVENTS, testName.getMethodName(), authorizationContext)
                .build(), ActorRef.noSender());
        assertThat(sinkProbe.requestNext().getJsonifiable())
                .isEqualTo(new StreamingAck(StreamingType.EVENTS, true));
    }

    private Connect getConnect(final Set<AcknowledgementLabel> declaredAcks) {
        return new Connect(sourceQueue, testName.getMethodName(), "WS", JsonSchemaVersion.LATEST, null, declaredAcks,
                authorizationContext);
    }

    private Set<AcknowledgementLabel> acks(final String... ackLabelNames) {
        return Arrays.stream(ackLabelNames).map(AcknowledgementLabel::of).collect(Collectors.toSet());
    }

    private Set<AcknowledgementRequest> ackRequests(final String... ackLabelNames) {
        return Arrays.stream(ackLabelNames)
                .map(AcknowledgementRequest::parseAcknowledgementRequest)
                .collect(Collectors.toSet());
    }
}
