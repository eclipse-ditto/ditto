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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationResultProvider;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtValidator;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.gateway.service.streaming.Connect;
import org.eclipse.ditto.gateway.service.streaming.IncomingSignal;
import org.eclipse.ditto.internal.models.acks.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.internal.utils.pubsub.DittoProtocolSub;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Attributes;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Test the interaction between timeout, response-required and requested-acks for
 * {@link StreamingSessionActor}.
 */
@RunWith(Parameterized.class)
public final class StreamingSessionActorHeaderInteractionTest {

    private static ActorSystem actorSystem;

    private static final List<Duration> TIMEOUT = List.of(Duration.ZERO, Duration.ofMinutes(1L));
    private static final List<Boolean> RESPONSE_REQUIRED = List.of(false, true);
    private static final List<List<AcknowledgementRequest>> REQUESTED_ACKS =
            List.of(List.of(), List.of(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED)));
    private static final List<Boolean> IS_SUCCESS = List.of(false, true);

    @Parameterized.Parameters(name = "timeout={0} response-required={1} requested-acks={2} is-success={3}")
    public static Collection<Object[]> getParameters() {
        return TIMEOUT.stream().flatMap(timeout ->
                RESPONSE_REQUIRED.stream().flatMap(responseRequired ->
                        REQUESTED_ACKS.stream().flatMap(requestedAcks ->
                                IS_SUCCESS.stream().map(isSuccess ->
                                        new Object[]{timeout, responseRequired, requestedAcks, isSuccess}
                                )
                        )
                )
        ).collect(Collectors.toList());
    }

    private final Duration timeout;
    private final boolean responseRequired;
    private final List<AcknowledgementRequest> requestedAcks;
    private final boolean isSuccess;

    private final List<ActorRef> createdActors = new ArrayList<>();
    private final TestProbe eventResponsePublisherProbe = TestProbe.apply("eventAndResponsePublisher", actorSystem);
    private final TestProbe commandRouterProbe = TestProbe.apply("commandRouter", actorSystem);
    private final TestProbe subscriptionManagerProbe = TestProbe.apply("subscriptionManager", actorSystem);
    private final DittoProtocolSub dittoProtocolSub = Mockito.mock(DittoProtocolSub.class);

    private final SourceQueueWithComplete<SessionedJsonifiable> sourceQueue;

    public StreamingSessionActorHeaderInteractionTest(final Duration timeout, final Boolean responseRequired,
            final List<AcknowledgementRequest> requestedAcks, final Boolean isSuccess) {
        this.timeout = timeout;
        this.responseRequired = responseRequired;
        this.requestedAcks = requestedAcks;
        this.isSuccess = isSuccess;

        final Source<SessionedJsonifiable, SourceQueueWithComplete<SessionedJsonifiable>> source =
                Source.queue(10, OverflowStrategy.fail());
        sourceQueue = source.toMat(Sink.actorRef(eventResponsePublisherProbe.ref(), "COMPLETE"), Keep.left())
                .run(actorSystem);
    }

    @BeforeClass
    public static void startActorSystem() {
        actorSystem = ActorSystem.create();
        actorSystem.eventStream().setLogLevel(Attributes.logLevelWarning());
    }

    @AfterClass
    public static void shutdown() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Before
    public void setup() {
        when(dittoProtocolSub.declareAcknowledgementLabels(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @After
    public void stopActors() {
        createdActors.forEach(actorSystem::stop);
    }

    @Test
    public void run() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = createStreamingSessionActor();
            final ModifyThing modifyThing = getModifyThing();
            underTest.tell(IncomingSignal.of(modifyThing), getRef());
            final Optional<HttpStatus> expectedStatusCode = getExpectedOutcome();
            final boolean isBadRequest = expectedStatusCode.filter(HttpStatus.BAD_REQUEST::equals).isPresent();
            if (!isBadRequest) {
                commandRouterProbe.expectMsg(modifyThing);
                // Regardless whether downstream sends reply, streaming session actor should not publish response
                // or error when response-required = false.
                commandRouterProbe.reply(getModifyThingResponse(modifyThing));
            }
            if (expectedStatusCode.isPresent()) {
                final SessionedResponseErrorOrAck response =
                        eventResponsePublisherProbe.expectMsgClass(SessionedResponseErrorOrAck.class);
                assertThat(getHttpStatus(response)).isEqualTo(expectedStatusCode.get());
            } else {
                eventResponsePublisherProbe.expectNoMessage((FiniteDuration) FiniteDuration.apply("250ms"));
            }
        }};
    }

    private ActorRef createStreamingSessionActor() {
        final Connect connect =
                new Connect(sourceQueue, "connectionCorrelationId", "ws",
                        JsonSchemaVersion.V_2, null, Set.of(), AuthorizationModelFactory.emptyAuthContext());
        final Props props = StreamingSessionActor.props(connect, dittoProtocolSub, commandRouterProbe.ref(),
                DefaultAcknowledgementConfig.of(ConfigFactory.empty()), HeaderTranslator.empty(),
                Props.create(TestProbeForwarder.class, subscriptionManagerProbe), Mockito.mock(JwtValidator.class),
                Mockito.mock(JwtAuthenticationResultProvider.class));
        final ActorRef createdActor = actorSystem.actorOf(props);
        createdActors.add(createdActor);
        return createdActor;
    }

    private ModifyThing getModifyThing() {
        return ModifyThing.of(ThingId.of("thing:id"), Thing.newBuilder().build(), null, DittoHeaders.newBuilder()
                .timeout(timeout)
                .responseRequired(responseRequired)
                .acknowledgementRequests(requestedAcks)
                .build());
    }

    private Object getModifyThingResponse(final ModifyThing modifyThing) {
        return isSuccess
                ? ModifyThingResponse.modified(modifyThing.getEntityId(), modifyThing.getDittoHeaders())
                : ThingNotAccessibleException.newBuilder(modifyThing.getEntityId())
                .dittoHeaders(modifyThing.getDittoHeaders())
                .build();
    }

    private Optional<HttpStatus> getExpectedOutcome() {
        final Optional<HttpStatus> status;
        final HttpStatus successCode = HttpStatus.NO_CONTENT;
        final HttpStatus errorCode = HttpStatus.NOT_FOUND;
        final HttpStatus badRequest = HttpStatus.BAD_REQUEST;
        if (timeout.isZero()) {
            status = (responseRequired || !requestedAcks.isEmpty()) ? Optional.of(badRequest) : Optional.empty();
        } else {
            if (!responseRequired && !requestedAcks.isEmpty()) {
                // WS special case: no acks without response possible
                status = Optional.of(badRequest);
            } else if (isSuccess) {
                status = responseRequired ? Optional.of(successCode) : Optional.empty();
            } else {
                status = responseRequired ? Optional.of(errorCode) : Optional.empty();
            }
        }
        return status;
    }

    private static HttpStatus getHttpStatus(final SessionedResponseErrorOrAck sessionedResponseErrorOrAck) {
        final Jsonifiable<?> jsonifiable = sessionedResponseErrorOrAck.getJsonifiable();
        if (jsonifiable instanceof DittoRuntimeException) {
            return ((DittoRuntimeException) jsonifiable).getHttpStatus();
        } else {
            return ((CommandResponse<?>) jsonifiable).getHttpStatus();
        }
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
