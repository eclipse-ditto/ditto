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
package org.eclipse.ditto.gateway.service.streaming.actors;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.edge.service.streaming.StreamingSubscriptionManager;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationResultProvider;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtValidator;
import org.eclipse.ditto.gateway.service.streaming.signals.Connect;
import org.eclipse.ditto.gateway.service.util.config.streaming.DefaultStreamingConfig;
import org.eclipse.ditto.gateway.service.util.config.streaming.StreamingConfig;
import org.eclipse.ditto.internal.utils.akka.actors.ModifyConfigBehavior;
import org.eclipse.ditto.internal.utils.akka.actors.RetrieveConfigBehavior;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.internal.utils.pubsubthings.DittoProtocolSub;
import org.eclipse.ditto.internal.utils.search.SubscriptionManager;

import com.typesafe.config.Config;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Materializer;

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
    private final Props streamingSubscriptionManagerProps;
    private final DittoDiagnosticLoggingAdapter logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final HeaderTranslator headerTranslator;
    private int childCounter = -1;

    private StreamingConfig streamingConfig;

    private final SupervisorStrategy strategy = new OneForOneStrategy(true, DeciderBuilder
            .match(Throwable.class, e -> {
                logger.error(e, "Escalating above actor!");
                return (SupervisorStrategy.Directive) SupervisorStrategy.escalate();
            }).matchAny(e -> {
                logger.error("Unknown message:'{}'! Escalating above actor!", e);
                return (SupervisorStrategy.Directive) SupervisorStrategy.escalate();
            }).build());

    @SuppressWarnings("unused")
    private StreamingActor(final DittoProtocolSub dittoProtocolSub,
            final ActorRef commandRouter,
            final JwtValidator jwtValidator,
            final JwtAuthenticationResultProvider jwtAuthenticationResultProvider,
            final StreamingConfig streamingConfig,
            final HeaderTranslator headerTranslator,
            final ActorRef pubSubMediator,
            final ActorRef commandForwarder) {

        this.dittoProtocolSub = dittoProtocolSub;
        this.commandRouter = commandRouter;
        this.jwtValidator = jwtValidator;
        this.jwtAuthenticationResultProvider = jwtAuthenticationResultProvider;
        this.streamingConfig = streamingConfig;
        this.headerTranslator = headerTranslator;
        streamingSessionsCounter = DittoMetrics.gauge("streaming_sessions_count");
        final ActorSelection commandForwarderSelection = ActorSelection.apply(commandForwarder, "");
        final Materializer materializer = Materializer.createMaterializer(getContext());
        subscriptionManagerProps =
                SubscriptionManager.props(streamingConfig.getSearchIdleTimeout(), pubSubMediator,
                        commandForwarderSelection, materializer);
        streamingSubscriptionManagerProps =
                StreamingSubscriptionManager.props(streamingConfig.getSearchIdleTimeout(),
                        commandForwarderSelection, materializer);
        scheduleScrapeStreamSessionsCounter();
    }

    /**
     * Creates Akka configuration object Props for this StreamingActor.
     *
     * @param dittoProtocolSub the Ditto protocol sub access.
     * @param commandRouter the command router used to send signals into the cluster.
     * @param jwtValidator the validator of JWTs to use.
     * @param jwtAuthenticationResultProvider the JwtAuthenticationResultProvider.
     * @param streamingConfig the streaming config.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param pubSubMediator the ActorRef to the Akka pub/sub mediator.
     * @param commandForwarder the ActorRef of the actor to forward commands to.
     * @return the Akka configuration Props object.
     */
    public static Props props(final DittoProtocolSub dittoProtocolSub,
            final ActorRef commandRouter,
            final JwtValidator jwtValidator,
            final JwtAuthenticationResultProvider jwtAuthenticationResultProvider,
            final StreamingConfig streamingConfig,
            final HeaderTranslator headerTranslator,
            final ActorRef pubSubMediator,
            final ActorRef commandForwarder) {

        return Props.create(StreamingActor.class, dittoProtocolSub, commandRouter, jwtValidator,
                jwtAuthenticationResultProvider, streamingConfig, headerTranslator, pubSubMediator, commandForwarder);
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
                                    commandRouter, streamingConfig, headerTranslator,
                                    subscriptionManagerProps, streamingSubscriptionManagerProps,
                                    jwtValidator, jwtAuthenticationResultProvider),
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
        getTimers().startTimerAtFixedRate(Control.SCRAPE_STREAM_COUNTER, Control.SCRAPE_STREAM_COUNTER,
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
