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

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.jwt.ImmutableJsonWebToken;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationResult;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationFactory;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationResultProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtValidator;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.InvalidJwt;
import org.eclipse.ditto.services.gateway.streaming.Jwt;
import org.eclipse.ditto.services.gateway.streaming.RefreshSession;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.gateway.util.config.streaming.DefaultStreamingConfig;
import org.eclipse.ditto.services.gateway.util.config.streaming.StreamingConfig;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.utils.akka.actors.ModifyConfigBehavior;
import org.eclipse.ditto.services.utils.akka.actors.RetrieveConfigBehavior;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.services.utils.search.SubscriptionManager;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;

import com.typesafe.config.Config;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;

/**
 * Parent Actor for {@link StreamingSessionActor}s delegating most of the messages to a specific session.
 * Manages WebSocket configuration.
 */
public final class StreamingActor extends AbstractActorWithTimers
        implements RetrieveConfigBehavior, ModifyConfigBehavior {

    /**
     * The name of this Actor.
     */
    public static final String ACTOR_NAME = "streaming";

    private final DittoProtocolSub dittoProtocolSub;
    private final ActorRef commandRouter;
    private final Gauge streamingSessionsCounter;
    private final JwtValidator jwtValidator;
    private final JwtAuthenticationResultProvider jwtAuthenticationResultProvider;
    private final Props subscriptionManagerProps;
    private final DittoDiagnosticLoggingAdapter logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final HeaderTranslator headerTranslator;

    private StreamingConfig streamingConfig;

    private final SupervisorStrategy strategy = new OneForOneStrategy(true, DeciderBuilder
            .match(Throwable.class, e -> {
                logger.error(e, "Escalating above actor!");
                return SupervisorStrategy.escalate();
            }).matchAny(e -> {
                logger.error("Unknown message:'{}'! Escalating above actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    @SuppressWarnings("unused")
    private StreamingActor(final DittoProtocolSub dittoProtocolSub,
            final ActorRef commandRouter,
            final JwtAuthenticationFactory jwtAuthenticationFactory,
            final StreamingConfig streamingConfig,
            final HeaderTranslator headerTranslator,
            final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder) {

        this.dittoProtocolSub = dittoProtocolSub;
        this.commandRouter = commandRouter;
        this.streamingConfig = streamingConfig;
        this.headerTranslator = headerTranslator;
        streamingSessionsCounter = DittoMetrics.gauge("streaming_sessions_count");
        jwtValidator = jwtAuthenticationFactory.getJwtValidator();
        jwtAuthenticationResultProvider = jwtAuthenticationFactory.newJwtAuthenticationResultProvider();
        subscriptionManagerProps =
                SubscriptionManager.props(streamingConfig.getSearchIdleTimeout(), pubSubMediator, conciergeForwarder,
                        ActorMaterializer.create(getContext()));
        scheduleScrapeStreamSessionsCounter();
    }

    /**
     * Creates Akka configuration object Props for this StreamingActor.
     *
     * @param dittoProtocolSub the Ditto protocol sub access.
     * @param commandRouter the command router used to send signals into the cluster.
     * @param streamingConfig the streaming config.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @return the Akka configuration Props object.
     */
    public static Props props(final DittoProtocolSub dittoProtocolSub,
            final ActorRef commandRouter,
            final JwtAuthenticationFactory jwtAuthenticationFactory,
            final StreamingConfig streamingConfig,
            final HeaderTranslator headerTranslator,
            final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder) {

        return Props.create(StreamingActor.class, dittoProtocolSub, commandRouter, jwtAuthenticationFactory,
                streamingConfig, headerTranslator, pubSubMediator, conciergeForwarder);
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
                            StreamingSessionActor.props(connect, dittoProtocolSub, eventAndResponsePublisher,
                                    streamingConfig.getAcknowledgementConfig(), headerTranslator,
                                    subscriptionManagerProps),
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
                .match(Jwt.class, this::refreshWebSocketSession)
                .build()
                .orElse(retrieveConfigBehavior())
                .orElse(modifyConfigBehavior())
                .orElse(ReceiveBuilder.create()
                        .match(Acknowledgement.class, acknowledgement ->
                                lookupSessionActor(acknowledgement, sessionActor ->
                                        sessionActor.forward(acknowledgement, getContext())
                                )
                        )
                        .match(Signal.class, this::handleSignal)
                        .matchEquals(Control.RETRIEVE_WEBSOCKET_CONFIG, this::replyWebSocketConfig)
                        .matchEquals(Control.SCRAPE_STREAM_COUNTER, this::updateStreamingSessionsCounter)
                        .match(DittoRuntimeException.class, cre -> {
                            final Optional<String> originOpt = cre.getDittoHeaders().getOrigin();
                            if (originOpt.isPresent()) {
                                forwardToSessionActor(originOpt.get(), cre);
                            } else {
                                logger.withCorrelationId(cre).warning("Unhandled DittoRuntimeException: <{}: {}>",
                                        cre.getClass().getSimpleName(), cre.getMessage());
                            }
                        })
                        .matchAny(any -> logger.warning("Got unknown message: '{}'", any))
                        .build());
    }

    @Override
    public Config getConfig() {
        return streamingConfig.render().getConfig(StreamingConfig.CONFIG_PATH);
    }

    @Override
    public Config setConfig(final Config config) {
        streamingConfig = DefaultStreamingConfig.of(
                config.atKey(StreamingConfig.CONFIG_PATH).withFallback(streamingConfig.render()));
        // reschedule scrapes: interval may have changed.
        scheduleScrapeStreamSessionsCounter();
        return streamingConfig.render();
    }

    private void refreshWebSocketSession(final Jwt jwt) {
        final String connectionCorrelationId = jwt.getConnectionCorrelationId();
        final JsonWebToken jsonWebToken = ImmutableJsonWebToken.fromToken(jwt.toString());
        jwtValidator.validate(jsonWebToken).thenAccept(binaryValidationResult -> {
            if (binaryValidationResult.isValid()) {
                try {
                    final AuthenticationResult authorizationResult =
                            jwtAuthenticationResultProvider.getAuthenticationResult(jsonWebToken, DittoHeaders.empty());
                    final AuthorizationContext authorizationContext =
                            authorizationResult.getDittoHeaders().getAuthorizationContext();

                    forwardToSessionActor(connectionCorrelationId,
                            new RefreshSession(connectionCorrelationId, jsonWebToken.getExpirationTime(),
                                    authorizationContext));
                } catch (final Exception exception) {
                    logger.info("Got exception when handling refreshed JWT for WebSocket session <{}>: {}",
                            connectionCorrelationId, exception.getMessage());
                    forwardToSessionActor(connectionCorrelationId, InvalidJwt.getInstance());
                }
            } else {
                forwardToSessionActor(connectionCorrelationId, InvalidJwt.getInstance());
            }
        });
    }

    private void forwardToSessionActor(final CharSequence connectionCorrelationId, final Object object) {
        if (object instanceof WithDittoHeaders) {
            logger.setCorrelationId((WithDittoHeaders<?>) object);
        }
        logger.debug("Forwarding to session actor '{}': {}", connectionCorrelationId, object);
        logger.discardCorrelationId();
        getContext().actorSelection(connectionCorrelationId.toString()).forward(object, getContext());
    }

    private void handleSignal(final Signal<?> signal) {
        if (signal.getDittoHeaders().isResponseRequired()) {
            lookupSessionActor(signal, sessionActor -> {
                if (signal instanceof ThingModifyCommand || signal instanceof ThingSearchCommand) {
                    // also tell the sessionActor so that the sessionActor may start an AcknowledgementAggregator
                    sessionActor.tell(signal, getSelf());
                }
                commandRouter.tell(signal, sessionActor);
            });
        } else {
            commandRouter.tell(signal, ActorRef.noSender());
        }
    }

    private void lookupSessionActor(final WithDittoHeaders<?> withHeaders, final Consumer<ActorRef> sessionActorCon) {

        final DittoHeaders dittoHeaders = withHeaders.getDittoHeaders();
        final Optional<String> originOpt = dittoHeaders.getOrigin();
        if (originOpt.isPresent()) {
            final String origin = originOpt.get();
            final Optional<ActorRef> sessionActor = getContext().findChild(origin);
            if (sessionActor.isPresent()) {
                sessionActorCon.accept(sessionActor.get());
            } else {
                logger.withCorrelationId(dittoHeaders)
                        .error("No session actor found for origin <{}>", origin);
            }
        } else {
            logger.withCorrelationId(dittoHeaders)
                    .error("No origin header present for WithDittoHeaders <{}>", withHeaders);
        }
    }

    private void scheduleScrapeStreamSessionsCounter() {
        getTimers().startPeriodicTimer(Control.SCRAPE_STREAM_COUNTER, Control.SCRAPE_STREAM_COUNTER,
                streamingConfig.getSessionCounterScrapeInterval());
    }

    private void replyWebSocketConfig(final Control trigger) {
        getSender().tell(streamingConfig.getWebsocketConfig(), getSelf());
    }

    private void updateStreamingSessionsCounter(final Control trigger) {
        if (getContext() != null) {
            streamingSessionsCounter.set(
                    StreamSupport.stream(getContext().getChildren().spliterator(), false).count());
        }
    }

    /**
     * Control messages to send in the same actor system.
     */
    public enum Control {

        /**
         * Tell streaming actor to set the stream counter to its current number of child actors.
         */
        SCRAPE_STREAM_COUNTER,

        /**
         * Request the current WebSocket config.
         */
        RETRIEVE_WEBSOCKET_CONFIG
    }

}
