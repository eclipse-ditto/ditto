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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationFactory;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationResultProvider;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtValidator;
import org.eclipse.ditto.services.gateway.streaming.Connect;
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
public final class StreamingActor extends AbstractActorWithTimers implements RetrieveConfigBehavior,
        ModifyConfigBehavior {

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
    private int childCounter = -1;

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
        return retrieveConfigBehavior()
                .orElse(modifyConfigBehavior())
                .orElse(createConnectAndMetricsBehavior())
                .orElse(ReceiveBuilder.create()
                        .matchAny(any -> logger.warning("Got unknown message: '{}'", any))
                        .build());
    }

    private Receive createConnectAndMetricsBehavior() {
        return ReceiveBuilder.create()
                .match(Connect.class, connect -> {
                    final String sessionActorName = getUniqueChildActorName(connect.getConnectionCorrelationId());
                    final ActorRef streamingSessionActor = getContext().actorOf(
                            StreamingSessionActor.props(connect, dittoProtocolSub,
                                    commandRouter, streamingConfig.getAcknowledgementConfig(), headerTranslator,
                                    subscriptionManagerProps, jwtValidator, jwtAuthenticationResultProvider),
                            sessionActorName);
                    getSender().tell(streamingSessionActor, ActorRef.noSender());
                })
                .matchEquals(Control.RETRIEVE_WEBSOCKET_CONFIG, this::replyWebSocketConfig)
                .matchEquals(Control.SCRAPE_STREAM_COUNTER, this::updateStreamingSessionsCounter)
                .build();
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

    private String getUniqueChildActorName(final String suffix) {
        final int counter = ++childCounter;
        return String.format("%x-%s", counter, URLEncoder.encode(suffix, StandardCharsets.UTF_8));
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
