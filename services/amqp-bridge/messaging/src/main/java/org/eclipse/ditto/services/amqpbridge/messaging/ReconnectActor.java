/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.messaging;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.ditto.model.amqpbridge.ConnectionStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.amqpbridge.messaging.persistence.MongoReconnectSnapshotAdapter;
import org.eclipse.ditto.services.amqpbridge.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.persistence.SnapshotAdapter;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatusResponse;
import org.eclipse.ditto.signals.events.amqpbridge.ConnectionClosed;
import org.eclipse.ditto.signals.events.amqpbridge.ConnectionCreated;
import org.eclipse.ditto.signals.events.amqpbridge.ConnectionDeleted;
import org.eclipse.ditto.signals.events.amqpbridge.ConnectionOpened;
import org.eclipse.ditto.signals.events.base.Event;

import com.typesafe.config.Config;

import akka.ConfigurationException;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import akka.persistence.SnapshotOffer;

/**
 * Actor which restarts a {@link ConnectionActor} with status
 * {@link ConnectionStatus#OPEN} automatically on startup.
 * <p>
 * This Actor must be created as a cluster singleton as it uses a fixed persistence id.
 * </p>
 */
public final class ReconnectActor extends AbstractPersistentActor {

    /**
     * The name of this Actor.
     */
    public static final String ACTOR_NAME = "reconnect";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef connectionShardRegion;
    private final long snapshotThreshold;
    private final SnapshotAdapter<Set<String>> snapshotAdapter;

    private final Set<String> connectionIds;

    private boolean snapshotInProgress = false;
    private long lastSnapshotSequenceNr = -1;

    private ReconnectActor(final ActorRef connectionShardRegion, final ActorRef pubSubMediator) {
        this.connectionShardRegion = connectionShardRegion;

        final Config config = getContext().system().settings().config();
        snapshotThreshold = config.getLong(ConfigKeys.Reconnect.SNAPSHOT_THRESHOLD);
        if (snapshotThreshold < 0) {
            throw new ConfigurationException(String.format("Config setting '%s' must be positive, but is: %d.",
                    ConfigKeys.Reconnect.SNAPSHOT_THRESHOLD, snapshotThreshold));
        }
        snapshotAdapter = new MongoReconnectSnapshotAdapter(getContext().system());

        connectionIds = new HashSet<>();

        pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(ConnectionCreated.TYPE, ACTOR_NAME, getSelf()),
                getSelf());
        pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(ConnectionOpened.TYPE, ACTOR_NAME, getSelf()),
                getSelf());
        pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(ConnectionClosed.TYPE, ACTOR_NAME, getSelf()),
                getSelf());
        pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(ConnectionDeleted.TYPE, ACTOR_NAME, getSelf()),
                getSelf());
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param connectionShardRegion the shard region of connections.
     * @param pubSubMediator the mediator to use for distributed pubsub.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef connectionShardRegion, final ActorRef pubSubMediator) {
        return Props.create(ReconnectActor.class, new Creator<ReconnectActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ReconnectActor create() {
                return new ReconnectActor(connectionShardRegion, pubSubMediator);
            }
        });
    }

    @Override
    public String persistenceId() {
        return ACTOR_NAME;
    }

    @Override
    public String journalPluginId() {
        return "akka-contrib-mongodb-persistence-reconnect-journal";
    }

    @Override
    public String snapshotPluginId() {
        return "akka-contrib-mongodb-persistence-reconnect-snapshots";
    }

    @Override
    public Receive createReceiveRecover() {
        return ReceiveBuilder.create()
                // # Snapshot handling
                .match(SnapshotOffer.class, ss -> {
                    final Set<String> fromSnapshotStore = snapshotAdapter.fromSnapshotStore(ss);
                    log.info("Received SnapshotOffer containing connectionIds: <{}>", fromSnapshotStore);
                    if (fromSnapshotStore != null) {
                        connectionIds.clear();
                        connectionIds.addAll(fromSnapshotStore);
                    }
                    lastSnapshotSequenceNr = ss.metadata().sequenceNr();
                })
                .match(ConnectionCreated.class, event -> connectionIds.add(event.getConnectionId()))
                .match(ConnectionOpened.class, event -> connectionIds.add(event.getConnectionId()))
                .match(ConnectionClosed.class, event -> connectionIds.remove(event.getConnectionId()))
                .match(ConnectionDeleted.class, event -> connectionIds.remove(event.getConnectionId()))
                .match(RecoveryCompleted.class, rc -> connectionIds.forEach(this::reconnect))
                .matchAny(m -> log.warning("Unknown recover message: {}", m))
                .build();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveConnectionStatusResponse.class, command -> {
                    if (!ConnectionStatus.OPEN.equals(command.getConnectionStatus())) {
                        connectionIds.remove(command.getConnectionId());
                    }
                })
                .match(ConnectionCreated.class,
                        event -> persistEvent(event, e -> connectionIds.add(e.getConnectionId())))
                .match(ConnectionOpened.class,
                        event -> persistEvent(event, e -> connectionIds.add(e.getConnectionId())))
                .match(ConnectionClosed.class,
                        event -> persistEvent(event, e -> connectionIds.remove(e.getConnectionId())))
                .match(ConnectionDeleted.class,
                        event -> persistEvent(event, e -> connectionIds.remove(e.getConnectionId())))
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                        log.debug("Successfully subscribed to distributed pub/sub on topic '{}'",
                                subscribeAck.subscribe().topic())
                )
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private <E extends Event> void persistEvent(final E event, final Consumer<E> consumer) {
        persist(event, persistedEvent -> {
            log.info("Successfully persisted Event '{}'", persistedEvent.getType());
            consumer.accept(persistedEvent);

            // save a snapshot if there were too many changes since the last snapshot
            if ((lastSequenceNr() - lastSnapshotSequenceNr) > snapshotThreshold) {
                doSaveSnapshot();
            }
        });
    }

    private void reconnect(final String connectionId) {
        connectionShardRegion.tell(RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty()), getSelf());
    }

    private void doSaveSnapshot() {
        if (snapshotInProgress) {
            log.debug("Already requested taking a Snapshot - not doing it again");
        } else {
            snapshotInProgress = true;
            log.info("Attempting to save Snapshot for '{}' ..", connectionIds);
            // save a snapshot
            final Object snapshotToStore = snapshotAdapter.toSnapshotStore(connectionIds);
            saveSnapshot(snapshotToStore);
        }
    }

}
