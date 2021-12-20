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
package org.eclipse.ditto.gateway.service.streaming.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.AcknowledgementCorrelationIdMissingException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayWebsocketSessionClosedException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayWebsocketSessionExpiredException;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationResultProvider;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtValidator;
import org.eclipse.ditto.gateway.service.streaming.Connect;
import org.eclipse.ditto.gateway.service.streaming.IncomingSignal;
import org.eclipse.ditto.gateway.service.streaming.Jwt;
import org.eclipse.ditto.gateway.service.streaming.StartStreaming;
import org.eclipse.ditto.gateway.service.streaming.StreamingAck;
import org.eclipse.ditto.internal.models.acks.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.pubsub.DittoProtocolSub;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.jwt.model.JwtInvalidException;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.ConfigFactory;

import akka.actor.Actor;
import akka.actor.ActorRef;
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

/**
 * Tests {@link StreamingSessionActor}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class StreamingSessionActorTest {

    private static AuthorizationContext authorizationContext;

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance();

    @Rule
    public final TestName testName = new TestName();

    @Mock private DittoProtocolSub mockSub;
    @Mock private JwtValidator mockValidator;
    @Mock private JwtAuthenticationResultProvider mockAuthenticationResultProvider;

    private TestProbe commandRouterProbe;
    private SourceQueueWithComplete<SessionedJsonifiable> sourceQueue;
    private TestSubscriber.Probe<SessionedJsonifiable> sinkProbe;
    private KillSwitch killSwitch;

    @BeforeClass
    public static void beforeClass() {
        authorizationContext = AuthorizationContext.newInstance(DittoAuthorizationContextType.JWT,
                AuthorizationSubject.newInstance("test-subject-1"),
                AuthorizationSubject.newInstance("test-subject-2"),
                AuthorizationSubject.newInstance("test-subject-3"));
    }

    @Before
    public void before() {
        commandRouterProbe = actorSystemResource.newTestProbe("commandRouter");

        Mockito.when(mockSub.declareAcknowledgementLabels(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        final Sink<SessionedJsonifiable, TestSubscriber.Probe<SessionedJsonifiable>> sink =
                TestSink.probe(actorSystemResource.getActorSystem());
        final Source<SessionedJsonifiable, SourceQueueWithComplete<SessionedJsonifiable>> source =
                Source.queue(100, OverflowStrategy.fail());
        final var pair = source.viaMat(KillSwitches.single(), Keep.both())
                .toMat(sink, Keep.both())
                .run(actorSystemResource.getActorSystem());
        sourceQueue = pair.first().first();
        sinkProbe = pair.second();
        killSwitch = pair.first().second();
    }

    @Test
    public void terminateOnStreamFailure() {
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.watch(actorSystemResource.newActor(getProps()));
        killSwitch.abort(new IllegalStateException("expected exception"));

        testKit.expectTerminated(underTest);
    }

    @Test
    public void completeStreamWhenStopped() {
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.watch(actorSystemResource.newActor(getProps()));
        underTest.tell(PoisonPill.getInstance(), testKit.getRef());

        testKit.expectTerminated(underTest);
        sinkProbe.ensureSubscription();
        sinkProbe.expectComplete();
    }

    @Test
    public void terminateOnAckLabelDeclarationFailure() {
        onDeclareAckLabels(CompletableFuture.failedStage(AcknowledgementLabelNotUniqueException.getInstance()));
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.watch(actorSystemResource.newActor(getProps("ack")));

        testKit.expectTerminated(underTest);
    }

    @Test
    public void sendDeclaredAckForGlobalDispatching() {
        onDeclareAckLabels(CompletableFuture.completedStage(null));
        final var acknowledgement = Acknowledgement.of(AcknowledgementLabel.of("ack"),
                ThingId.of("thing:id"),
                HttpStatus.OK,
                DittoHeaders.newBuilder().correlationId("corr:" + testName.getMethodName()).build());
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.watch(actorSystemResource.newActor(getProps("ack")));

        underTest.tell(IncomingSignal.of(acknowledgement), ActorRef.noSender());

        commandRouterProbe.expectMsg(acknowledgement);
    }

    @Test
    public void sendMalformedAck() {
        onDeclareAckLabels(CompletableFuture.completedStage(null));
        final var acknowledgement = Acknowledgement.of(AcknowledgementLabel.of("ack"),
                ThingId.of("thing:id"),
                HttpStatus.OK,
                DittoHeaders.empty());
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.watch(actorSystemResource.newActor(getProps("ack")));

        underTest.tell(IncomingSignal.of(acknowledgement), ActorRef.noSender());

        final var sessionedJsonifiable = sinkProbe.requestNext();

        assertThat(sessionedJsonifiable.getJsonifiable())
                .isInstanceOf(AcknowledgementCorrelationIdMissingException.class);
    }

    @Test
    public void sendNonDeclaredAck() {
        onDeclareAckLabels(CompletableFuture.completedStage(null));
        final var acknowledgement = Acknowledgement.of(AcknowledgementLabel.of("ack2"),
                ThingId.of("thing:id"),
                HttpStatus.OK,
                DittoHeaders.empty());
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.watch(actorSystemResource.newActor(getProps("ack")));

        underTest.tell(IncomingSignal.of(acknowledgement), ActorRef.noSender());
        final var sessionedJsonifiable = sinkProbe.requestNext();

        assertThat(sessionedJsonifiable.getJsonifiable())
                .isInstanceOf(AcknowledgementLabelNotDeclaredException.class);
    }

    @Test
    public void acknowledgementRequestsAreRestrictedToDeclaredAcks() {
        onDeclareAckLabels(CompletableFuture.completedStage(null));
        setUpMockForTwinEventsSubscription();
        final var dittoHeaders = DittoHeaders.newBuilder()
                .correlationId("corr:" + testName.getMethodName())
                .readGrantedSubjects(List.of(AuthorizationSubject.newInstance("ditto:ditto")))
                .acknowledgementRequests(getAcknowledgementRequests("ack", "ack2"))
                .build();
        final var signal = ThingDeleted.of(ThingId.of("thing:id"), 2L, null, dittoHeaders, null);
        final var expectedSignal = signal.setDittoHeaders(DittoHeaders.newBuilder(signal.getDittoHeaders())
                .acknowledgementRequests(getAcknowledgementRequests("ack"))
                .build());
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.watch(actorSystemResource.newActor(getProps("ack")));
        subscribeForTwinEvents(underTest);

        underTest.tell(signal, ActorRef.noSender());

        final var sessionedJsonifiable = sinkProbe.requestNext();

        assertThat(sessionedJsonifiable.getJsonifiable()).isEqualTo(expectedSignal);
    }

    @Test
    public void invalidJwtClosesStream() {
        Mockito.when(mockValidator.validate(Mockito.any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        BinaryValidationResult.invalid(JwtInvalidException.newBuilder().build())));
        final var jwt = Jwt.newInstance(getTokenString(), testName.getMethodName());
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.watch(actorSystemResource.newActor(getProps()));

        underTest.tell(jwt, testKit.getRef());

        assertThat(sinkProbe.expectSubscriptionAndError())
                .isInstanceOf(GatewayWebsocketSessionClosedException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    public void changingAuthorizationContextClosesStream() {
        Mockito.when(mockValidator.validate(Mockito.any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        Mockito.when(mockAuthenticationResultProvider.getAuthenticationResult(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedStage(JwtAuthenticationResult.successful(DittoHeaders.empty(),
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                                AuthorizationSubject.newInstance("new:auth-subject")),
                        Mockito.mock(JsonWebToken.class))));
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.watch(actorSystemResource.newActor(getProps()));
        final var jwt = Jwt.newInstance(getTokenString(), testName.getMethodName());

        underTest.tell(jwt, testKit.getRef());

        assertThat(sinkProbe.expectSubscriptionAndError())
                .isInstanceOf(GatewayWebsocketSessionClosedException.class)
                .hasMessageContaining("authorization context");
    }

    @Test
    public void keepingAuthorizationContextDoesNotCloseStream() {
        Mockito.when(mockValidator.validate(Mockito.any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        Mockito.when(mockAuthenticationResultProvider.getAuthenticationResult(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedStage(JwtAuthenticationResult.successful(DittoHeaders.empty(),
                        authorizationContext,
                        Mockito.mock(JsonWebToken.class))));
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.watch(actorSystemResource.newActor(getProps()));
        final var jwt = Jwt.newInstance(getTokenString(), testName.getMethodName());

        underTest.tell(jwt, testKit.getRef());

        sinkProbe.expectSubscription();
        sinkProbe.expectNoMessage();
    }

    @Test
    public void hugeJwtExpirationTimeDoesNotCloseStream() {
        Mockito.when(mockValidator.validate(Mockito.any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        Mockito.when(mockAuthenticationResultProvider.getAuthenticationResult(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedStage(JwtAuthenticationResult.successful(DittoHeaders.empty(),
                        authorizationContext,
                        Mockito.mock(JsonWebToken.class))));

        // maximum expiration is too far in the future
        final var jwt =
                Jwt.newInstance(getTokenString(Instant.now().plus(Duration.ofDays(999))), testName.getMethodName());
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.watch(actorSystemResource.newActor(getProps()));

        underTest.tell(jwt, testKit.getRef());

        sinkProbe.expectSubscription();
        sinkProbe.expectNoMessage();
    }

    @Test
    public void jwtExpirationTimeClosesStream() {
        Mockito.when(mockValidator.validate(Mockito.any(JsonWebToken.class)))
                .thenReturn(CompletableFuture.completedFuture(BinaryValidationResult.valid()));
        Mockito.when(mockAuthenticationResultProvider.getAuthenticationResult(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedStage(JwtAuthenticationResult.successful(DittoHeaders.empty(),
                        authorizationContext,
                        Mockito.mock(JsonWebToken.class))));
        final var jwt = Jwt.newInstance(getTokenString(Instant.now()), testName.getMethodName());
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.watch(actorSystemResource.newActor(getProps()));

        underTest.tell(jwt, testKit.getRef());

        sinkProbe.expectSubscription();

        assertThat(sinkProbe.expectError())
                .isInstanceOf(GatewayWebsocketSessionExpiredException.class)
                .hasMessageContaining("expired");
    }


    @Test
    public void sendLiveCommandResponseAndEnsureForwarding() {
        final var dittoHeaders = DittoHeaders.newBuilder()
                .channel(TopicPath.Channel.LIVE.getName())
                .correlationId("corr:" + testName.getMethodName())
                .build();
        final var retrieveThingResponse =
                RetrieveThingResponse.of(ThingId.generateRandom(), JsonObject.empty(), dittoHeaders);
        final var testKit = actorSystemResource.newTestKit();
        final var underTest = testKit.watch(actorSystemResource.newActor(getProps()));

        underTest.tell(IncomingSignal.of(retrieveThingResponse), ActorRef.noSender());

        commandRouterProbe.expectMsg(retrieveThingResponse);
    }

    private static String getTokenString() {
        return getTokenString(Instant.now().plusSeconds(60L));
    }

    private static String getTokenString(final Instant expiration) {
        final var header = "{\"header\":\"foo\"}";
        final var payload = "{\"payload\":\"bar\",\"exp\":" + expiration.getEpochSecond() + "}";
        final var signature = "{\"signature\":\"baz\"}";
        return base64(header) + "." + base64(payload) + "." + base64(signature);
    }

    private static String base64(final String value) {
        return new String(Base64.getEncoder().encode(value.getBytes()));
    }

    private Props getProps(final String... declaredAcks) {
        return StreamingSessionActor.props(getConnect(getAcknowledgementLabels(declaredAcks)),
                mockSub,
                commandRouterProbe.ref(),
                DefaultAcknowledgementConfig.of(ConfigFactory.empty()),
                HeaderTranslator.empty(),
                Props.create(Actor.class, () -> new TestActor(new LinkedBlockingDeque<>())),
                mockValidator,
                mockAuthenticationResultProvider);
    }

    private void onDeclareAckLabels(final CompletionStage<Void> answer) {
        Mockito.when(mockSub.declareAcknowledgementLabels(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(answer);
    }

    private void setUpMockForTwinEventsSubscription() {
        Mockito.when(mockSub.subscribe(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedStage(null));
    }

    private void subscribeForTwinEvents(final ActorRef underTest) {
        final var authorizationContext =
                AuthorizationContext.newInstance(DittoAuthorizationContextType.PRE_AUTHENTICATED_HTTP,
                        AuthorizationSubject.newInstance("ditto:ditto"));

        final var startStreaming =
                StartStreaming.getBuilder(StreamingType.EVENTS, testName.getMethodName(), authorizationContext).build();
        underTest.tell(startStreaming, ActorRef.noSender());

        final var sessionedJsonifiable = sinkProbe.requestNext();

        assertThat(sessionedJsonifiable.getJsonifiable()).isEqualTo(new StreamingAck(StreamingType.EVENTS, true));
    }

    private Connect getConnect(final Set<AcknowledgementLabel> declaredAcks) {
        return new Connect(sourceQueue,
                testName.getMethodName(),
                "WS",
                JsonSchemaVersion.LATEST,
                null,
                declaredAcks,
                authorizationContext);
    }

    private static Set<AcknowledgementLabel> getAcknowledgementLabels(final String... ackLabelNames) {
        return Stream.of(ackLabelNames).map(AcknowledgementLabel::of).collect(Collectors.toSet());
    }

    private static Set<AcknowledgementRequest> getAcknowledgementRequests(final String... ackLabelNames) {
        return Stream.of(ackLabelNames)
                .map(AcknowledgementRequest::parseAcknowledgementRequest)
                .collect(Collectors.toSet());
    }

}
