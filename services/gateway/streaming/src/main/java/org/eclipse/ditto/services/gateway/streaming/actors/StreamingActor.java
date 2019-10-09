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
package org.eclipse.ditto.services.gateway.streaming.actors;

import java.time.Duration;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.jwt.ImmutableJsonWebToken;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthorizationContextProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtValidator;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.JwtToken;
import org.eclipse.ditto.services.gateway.streaming.RefreshSession;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;

/**
 * Parent Actor for {@link StreamingSessionActor}s delegating most of the messages to a specific session.
 */
public final class StreamingActor extends AbstractActor {

    /**
     * The name of this Actor.
     */
    public static final String ACTOR_NAME = "streaming";
    private static final Duration SESSIONS_COUNTER_SCRAPE_INTERVAL = Duration.ofSeconds(30);

    private final DiagnosticLoggingAdapter logger = LogUtil.obtain(this);

    private final DittoProtocolSub dittoProtocolSub;
    private final ActorRef commandRouter;
    private final JwtValidator jwtValidator;
    private final JwtAuthorizationContextProvider jwtAuthorizationContextProvider;

    private final SupervisorStrategy strategy = new OneForOneStrategy(true, DeciderBuilder
            .match(Throwable.class, e -> {
                logger.error(e, "Escalating above actor!");
                return SupervisorStrategy.escalate();
            }).matchAny(e -> {
                logger.error("Unknown message:'{}'! Escalating above actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    private final Gauge streamingSessionsCounter;
    private final Cancellable sessionCounterScheduler;

    @SuppressWarnings("unused")
    private StreamingActor(final DittoProtocolSub dittoProtocolSub,
            final ActorRef commandRouter,
            final JwtValidator jwtValidator,
            final JwtAuthorizationContextProvider jwtAuthorizationContextProvider) {
        this.dittoProtocolSub = dittoProtocolSub;
        this.commandRouter = commandRouter;
        this.jwtValidator = jwtValidator;
        this.jwtAuthorizationContextProvider = jwtAuthorizationContextProvider;

        streamingSessionsCounter = DittoMetrics.gauge("streaming_sessions_count");

        sessionCounterScheduler = getContext().getSystem().getScheduler()
                .schedule(SESSIONS_COUNTER_SCRAPE_INTERVAL, SESSIONS_COUNTER_SCRAPE_INTERVAL,
                        this::updateStreamingSessionsCounter, getContext().getDispatcher());
    }

    private void updateStreamingSessionsCounter() {
        if (getContext() != null) {
            streamingSessionsCounter.set(
                    StreamSupport.stream(getContext().getChildren().spliterator(), false).count());
        }
    }

    @Override
    public void postStop() {
        sessionCounterScheduler.cancel();
    }

    /**
     * Creates Akka configuration object Props for this StreamingActor.
     *
     * @param dittoProtocolSub the Ditto protocol sub access.
     * @param commandRouter the command router used to send signals into the cluster
     * @param jwtValidator the validator for JWT tokens.
     * @return the Akka configuration Props object.
     */
    public static Props props(final DittoProtocolSub dittoProtocolSub, final ActorRef commandRouter,
            final JwtValidator jwtValidator, final JwtAuthorizationContextProvider jwtAuthorizationContextProvider) {
        return Props.create(StreamingActor.class, dittoProtocolSub, commandRouter, jwtValidator,
                jwtAuthorizationContextProvider);
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                // Handle internal connect/streaming commands
                .match(Connect.class, connect -> {
                    final ActorRef eventAndResponsePublisher = connect.getEventAndResponsePublisher();
                    eventAndResponsePublisher.forward(connect, getContext());
                    final String connectionCorrelationId = connect.getConnectionCorrelationId();
                    getContext().actorOf(
                            StreamingSessionActor.props(connectionCorrelationId, connect.getType(), dittoProtocolSub,
                                    eventAndResponsePublisher, connect.getSessionExpirationTime()),
                            connectionCorrelationId);
                })
                .match(StartStreaming.class,
                        startStreaming -> forwardToSessionActor(startStreaming.getConnectionCorrelationId(),
                                startStreaming)
                )
                .match(StopStreaming.class,
                        stopStreaming -> forwardToSessionActor(stopStreaming.getConnectionCorrelationId(),
                                stopStreaming)
                )
                .match(JwtToken.class, this::refreshWebsocketSession)
                .match(Signal.class, signal -> {
                    final Optional<String> originOpt = signal.getDittoHeaders().getOrigin();
                    if (originOpt.isPresent()) {
                        final String origin = originOpt.get();
                        final ActorRef sessionActor = getContext().getChild(origin);
                        if (sessionActor != null) {
                            commandRouter.tell(signal, sessionActor);
                        } else {
                            logger.debug("No session actor found for origin: {}", origin);
                        }
                    } else {
                        logger.warning("Signal is missing the required origin header: {}",
                                signal.getDittoHeaders().getCorrelationId());
                    }
                })
                .match(DittoRuntimeException.class, cre -> {
                    final Optional<String> originOpt = cre.getDittoHeaders().getOrigin();
                    if (originOpt.isPresent()) {
                        forwardToSessionActor(originOpt.get(), cre);
                    } else {
                        logger.warning("Unhandled DittoRuntimeException: <{}: {}>", cre.getClass().getSimpleName(),
                                cre.getMessage());
                    }
                })
                .matchAny(any -> logger.warning("Got unknown message: '{}'", any)).build();
    }


    private void refreshWebsocketSession(final JwtToken jwtToken) {
        final JsonWebToken jsonWebToken = ImmutableJsonWebToken.fromToken(jwtToken.getJwtTokenAsString());
        jwtValidator.validate(jsonWebToken).thenAccept(binaryValidationResult -> {
            if (binaryValidationResult.isValid()) {
                final String connectionCorrelationId = jwtToken.getConnectionCorrelationId();
                final AuthorizationContext authorizationContext =
                        jwtAuthorizationContextProvider.getAuthorizationContext(jsonWebToken);
                forwardToSessionActor(connectionCorrelationId,
                        new RefreshSession(connectionCorrelationId, jsonWebToken.getExpirationTime(),
                                authorizationContext));
            }
        });
    }

    private void forwardToSessionActor(final String connectionCorrelationId, final Object object) {
        if (object instanceof WithDittoHeaders) {
            LogUtil.enhanceLogWithCorrelationId(logger, (WithDittoHeaders<?>) object);
        } else {
            LogUtil.enhanceLogWithCorrelationId(logger, (String) null);
        }
        logger.debug("Forwarding to session actor '{}': {}", connectionCorrelationId, object);
        getContext().actorSelection(connectionCorrelationId).forward(object, getContext());
    }
}
