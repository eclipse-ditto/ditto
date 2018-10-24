/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.connectivity.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatusResponse;

import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Source;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor which wakes up {@link ConnectionActor}s automatically on startup. The {@link ConnectionActor} then
 * decides if the connection will be opened or stay closed depending on the persisted connection status.
 */
public final class ReconnectActor extends AbstractActor {

    /**
     * The name of this Actor.
     */
    public static final String ACTOR_NAME = "reconnect";

    private static final String CORRELATION_ID_PREFIX = "reconnect-actor-triggered:";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorMaterializer materializer;
    private final ActorRef connectionShardRegion;
    private final Supplier<Source<String, NotUsed>> currentPersistenceIdsSourceSupplier;

    private final Duration reconnectInitialDelay;
    private final Duration reconnectInterval;
    private final Duration reconnectRateFrequency;
    private final int reconnectRateEntities;

    private Cancellable reconnectCheck;
    private boolean reconnectInProgress = false;

    private ReconnectActor(final ActorRef connectionShardRegion,
            final Supplier<Source<String, NotUsed>> currentPersistenceIdsSourceSupplier) {
        this.connectionShardRegion = connectionShardRegion;
        this.currentPersistenceIdsSourceSupplier = currentPersistenceIdsSourceSupplier;

        final Config config = getContext().system().settings().config();
        materializer = ActorMaterializer.create(getContext().getSystem());

        reconnectInitialDelay = config.getDuration(ConfigKeys.Reconnect.RECONNECT_INITIAL_DELAY);
        reconnectInterval = config.getDuration(ConfigKeys.Reconnect.RECONNECT_INTERVAL);
        reconnectRateFrequency = config.getDuration(ConfigKeys.Reconnect.RECONNECT_RATE_FREQUENCY);
        reconnectRateEntities = config.getInt(ConfigKeys.Reconnect.RECONNECT_RATE_ENTITIES);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param connectionShardRegion the shard region of connections.
     * @param currentPersistenceIdsSourceSupplier supplier of persistence id sources
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef connectionShardRegion,
            final Supplier<Source<String, NotUsed>> currentPersistenceIdsSourceSupplier) {
        return Props.create(ReconnectActor.class, connectionShardRegion, currentPersistenceIdsSourceSupplier);
    }

    private Cancellable scheduleReconnect() {
        final FiniteDuration initialDelay =
                FiniteDuration.apply(reconnectInitialDelay.toMillis(), TimeUnit.MILLISECONDS);
        final FiniteDuration interval = FiniteDuration.apply(reconnectInterval.toMillis(), TimeUnit.MILLISECONDS);
        final ReconnectConnections message = ReconnectConnections.INSTANCE;
        log.info("Scheduling reconnect for all connections with initial delay {} and interval {}.",
                reconnectInitialDelay, reconnectInterval);
        return getContext().getSystem()
                .scheduler()
                .schedule(initialDelay, interval, getSelf(), message, getContext().dispatcher(), ActorRef.noSender());
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        reconnectCheck = scheduleReconnect();
    }

    @Override
    public void postStop() throws Exception {
        if (null != reconnectCheck) {
            reconnectCheck.cancel();
        }
        super.postStop();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveConnectionStatusResponse.class,
                        command -> log.debug("Retrieved connection status response for connection {} with status: {}",
                                command.getConnectionId(), command.getConnectionStatus()))
                .match(ConnectionNotAccessibleException.class,
                        exception -> log.debug("Received ConnectionNotAccessibleException for connection {} " +
                                        "(most likely, the connection was deleted): {}",
                                exception.getDittoHeaders().getCorrelationId().orElse("<unknown>"),
                                exception.getMessage()))
                .match(DittoRuntimeException.class,
                        exception -> log.debug("Received {} for connection {} : {}",
                                exception.getClass().getSimpleName(),
                                exception.getDittoHeaders().getCorrelationId().orElse("<unknown>"),
                                exception.getMessage()))
                .matchEquals(ReconnectConnections.INSTANCE, rc -> this.handleReconnectConnections())
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handleReconnectConnections() {
        if (reconnectInProgress) {
            log.info("Another reconnect iteration is currently in progress. Next iteration will be started after {}.",
                    reconnectInterval);
        } else {
            log.info("Sending reconnects for Connections. Will be sent again after the configured Interval of {}.",
                    reconnectInterval);
            reconnectInProgress = true;
            final Source<String, NotUsed> currentPersistenceIdsSource = currentPersistenceIdsSourceSupplier.get();
            if (currentPersistenceIdsSource != null) {
                currentPersistenceIdsSource
                        .throttle(reconnectRateEntities, reconnectRateFrequency)
                        .runForeach(this::reconnect, materializer)
                        .thenRun(() -> {
                            log.info("Sending reconnects completed.");
                            reconnectInProgress = false;
                        });
            } else {
                log.warning("Failed to create new persistence id source for connection recovery.");
            }
        }
    }

    private void reconnect(final String persistenceId) {
        // yes, this is intentionally a RetrieveConnectionStatus instead of OpenConnection.
        // ConnectionActor manages its own reconnection on recovery.
        // OpenConnection would set desired state to OPEN even for deleted connections.

        if (persistenceId.startsWith(ConnectionActor.PERSISTENCE_ID_PREFIX)) {
            final String connectionId = persistenceId.substring(ConnectionActor.PERSISTENCE_ID_PREFIX.length());
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .correlationId(toCorrelationId(connectionId))
                    .build();
            log.debug("Sending a reconnect for Connection {}", connectionId);
            connectionShardRegion.tell(RetrieveConnectionStatus.of(connectionId, dittoHeaders), getSelf());
        } else {
            log.debug("Unknown persistence id '{}', ignoring.", persistenceId);
        }
    }

    static String toCorrelationId(final String connectionId) {
        return CORRELATION_ID_PREFIX + connectionId;
    }

    static Optional<String> toConnectionId(final String correlationId) {
        return correlationId.startsWith(CORRELATION_ID_PREFIX)
                ? Optional.of(correlationId.replace(CORRELATION_ID_PREFIX, ""))
                : Optional.empty();
    }

    enum ReconnectConnections {
        INSTANCE
    }

}
