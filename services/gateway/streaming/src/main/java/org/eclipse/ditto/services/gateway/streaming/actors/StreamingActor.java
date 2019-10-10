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
import java.util.stream.StreamSupport;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.DefaultWebsocketConfig;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.eclipse.ditto.services.gateway.streaming.WebsocketConfig;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.actors.ModifyConfigBehavior;
import org.eclipse.ditto.services.utils.akka.actors.RetrieveConfigBehavior;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.signals.base.Signal;

import com.typesafe.config.Config;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;

/**
 * Parent Actor for {@link StreamingSessionActor}s delegating most of the messages to a specific session.
 * Manages websocket configuration.
 */
public final class StreamingActor extends AbstractActorWithTimers
        implements RetrieveConfigBehavior, ModifyConfigBehavior {

    /**
     * The name of this Actor.
     */
    public static final String ACTOR_NAME = "streaming";

    private final DiagnosticLoggingAdapter logger = LogUtil.obtain(this);

    private final DittoProtocolSub dittoProtocolSub;
    private final ActorRef commandRouter;

    private final SupervisorStrategy strategy = new OneForOneStrategy(true, DeciderBuilder
            .match(Throwable.class, e -> {
                logger.error(e, "Escalating above actor!");
                return SupervisorStrategy.escalate();
            }).matchAny(e -> {
                logger.error("Unknown message:'{}'! Escalating above actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    private final Gauge streamingSessionsCounter;

    private WebsocketConfig websocketConfig;

    @SuppressWarnings("unused")
    private StreamingActor(final DittoProtocolSub dittoProtocolSub, final ActorRef commandRouter,
            final WebsocketConfig websocketConfig) {
        this.dittoProtocolSub = dittoProtocolSub;
        this.commandRouter = commandRouter;
        streamingSessionsCounter = DittoMetrics.gauge("streaming_sessions_count");
        this.websocketConfig = websocketConfig;

        // requires this.websocketConfig to be initialized
        scheduleScrapeStreamSessionsCounter();
    }

    /**
     * Creates Akka configuration object Props for this StreamingActor.
     *
     * @param dittoProtocolSub the Ditto protocol sub access.
     * @param commandRouter the command router used to send signals into the cluster
     * @param websocketConfig the websocket config
     * @return the Akka configuration Props object.
     */
    public static Props props(final DittoProtocolSub dittoProtocolSub, final ActorRef commandRouter,
            final WebsocketConfig websocketConfig) {
        return Props.create(StreamingActor.class, dittoProtocolSub, commandRouter, websocketConfig);
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
                                    eventAndResponsePublisher), connectionCorrelationId);
                })
                .match(StartStreaming.class,
                        startStreaming -> forwardToSessionActor(startStreaming.getConnectionCorrelationId(),
                                startStreaming)
                )
                .match(StopStreaming.class,
                        stopStreaming -> forwardToSessionActor(stopStreaming.getConnectionCorrelationId(),
                                stopStreaming)
                )
                .build()
                .orElse(retrieveConfigBehavior())
                .orElse(modifyConfigBehavior())
                .orElse(ReceiveBuilder.create()
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
                        .matchEquals(Control.RETRIEVE_WEBSOCKET_CONFIG, this::replyWebsocketConfig)
                        .matchEquals(Control.SCRAPE_STREAM_COUNTER, this::updateStreamingSessionsCounter)
                        .match(DittoRuntimeException.class, cre -> {
                            final Optional<String> originOpt = cre.getDittoHeaders().getOrigin();
                            if (originOpt.isPresent()) {
                                forwardToSessionActor(originOpt.get(), cre);
                            } else {
                                logger.warning("Unhandled DittoRuntimeException: <{}: {}>",
                                        cre.getClass().getSimpleName(),
                                        cre.getMessage());
                            }
                        })
                        .matchAny(any -> logger.warning("Got unknown message: '{}'", any))
                        .build());
    }

    @Override
    public Config getConfig() {
        return websocketConfig.render().getConfig(WebsocketConfig.CONFIG_PATH);
    }

    @Override
    public Config setConfig(final Config config) {
        websocketConfig = DefaultWebsocketConfig.of(
                config.atKey(WebsocketConfig.CONFIG_PATH)
                        .withFallback(websocketConfig.render()));
        // reschedule scrapes: interval may have changed.
        scheduleScrapeStreamSessionsCounter();
        return websocketConfig.render();
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

    private void scheduleScrapeStreamSessionsCounter() {
        getTimers().startPeriodicTimer(Control.SCRAPE_STREAM_COUNTER, Control.SCRAPE_STREAM_COUNTER,
                websocketConfig.getSessionCounterScrapeInterval());
    }

    private void replyWebsocketConfig(final Control trigger) {
        getSender().tell(websocketConfig, getSelf());
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
         * Request the current websocket config.
         */
        RETRIEVE_WEBSOCKET_CONFIG
    }
}
