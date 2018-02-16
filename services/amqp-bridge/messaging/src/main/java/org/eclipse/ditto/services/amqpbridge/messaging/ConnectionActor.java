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

import static java.util.Collections.singleton;
import static org.eclipse.ditto.services.models.amqpbridge.AmqpBridgeMessagingConstants.CLUSTER_ROLE;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.ConnectionStatus;
import org.eclipse.ditto.model.amqpbridge.MappingContext;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.amqpbridge.messaging.persistence.ConnectionData;
import org.eclipse.ditto.services.amqpbridge.messaging.persistence.MongoConnectionSnapshotAdapter;
import org.eclipse.ditto.services.amqpbridge.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.persistence.SnapshotAdapter;
import org.eclipse.ditto.signals.commands.amqpbridge.AmqpBridgeCommand;
import org.eclipse.ditto.signals.commands.amqpbridge.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.amqpbridge.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.OpenConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatusResponse;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.events.amqpbridge.ConnectionClosed;
import org.eclipse.ditto.signals.events.amqpbridge.ConnectionCreated;
import org.eclipse.ditto.signals.events.amqpbridge.ConnectionDeleted;
import org.eclipse.ditto.signals.events.amqpbridge.ConnectionOpened;
import org.eclipse.ditto.signals.events.base.Event;

import com.typesafe.config.Config;

import akka.ConfigurationException;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.routing.ClusterRouterPool;
import akka.cluster.routing.ClusterRouterPoolSettings;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import akka.persistence.SnapshotOffer;
import akka.routing.BroadcastPool;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

/**
 * Handles {@code *Connection} commands and manages the persistence of connection. The actual connection handling to the
 * remote server is delegated to a child actor that uses a specific client (AMQP 1.0 or 0.9.1).
 */
class ConnectionActor extends AbstractPersistentActor {

    private static final String PERSISTENCE_ID_PREFIX = "connection:";

    private static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-connection-journal";
    private static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-connection-snapshots";

    private static final int SHUTDOWN_DELAY_SECONDS = 10;
    private static final FiniteDuration SHUTDOWN_DELAY = Duration.apply(SHUTDOWN_DELAY_SECONDS, TimeUnit.SECONDS);
    private static final long DEFAULT_TIMEOUT_MS = 5000;

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final String connectionId;
    private final ActorRef pubSubMediator;
    private final long snapshotThreshold;
    private final SnapshotAdapter<ConnectionData> snapshotAdapter;
    private final ConnectionActorPropsFactory propsFactory;
    private final Receive connectionCreatedBehaviour;

    //    private ActorRef commandProcessor;
    private ActorRef clientActor;

    private AmqpConnection amqpConnection;
    private ConnectionStatus connectionStatus;
    private List<MappingContext> mappingContexts;

    private Cancellable shutdownCancellable;
    private long lastSnapshotSequenceNr = -1L;
    private boolean snapshotInProgress = false;

    private ConnectionActor(final String connectionId, final ActorRef pubSubMediator,
            final ConnectionActorPropsFactory propsFactory) {
        this.connectionId = connectionId;
        this.pubSubMediator = pubSubMediator;
        this.propsFactory = propsFactory;

        final Config config = getContext().system().settings().config();
        snapshotThreshold = config.getLong(ConfigKeys.Connection.SNAPSHOT_THRESHOLD);
        if (snapshotThreshold < 0) {
            throw new ConfigurationException(String.format("Config setting '%s' must be positive, but is: %d.",
                    ConfigKeys.Connection.SNAPSHOT_THRESHOLD, snapshotThreshold));
        }
        snapshotAdapter = new MongoConnectionSnapshotAdapter(getContext().system());

        connectionStatus = ConnectionStatus.CLOSED;
        mappingContexts = Collections.emptyList();

        connectionCreatedBehaviour = createConnectionCreatedBehaviour();
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @return the Akka configuration Props object
     */
    public static Props props(final String connectionId, final ActorRef pubSubMediator,
            final ConnectionActorPropsFactory propsFactory) {
        return Props.create(ConnectionActor.class, new Creator<ConnectionActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ConnectionActor create() {
                return new ConnectionActor(connectionId, pubSubMediator, propsFactory);
            }
        });
    }

    @Override
    public String persistenceId() {
        return PERSISTENCE_ID_PREFIX + connectionId;
    }

    @Override
    public String journalPluginId() {
        return JOURNAL_PLUGIN_ID;
    }

    @Override
    public String snapshotPluginId() {
        return SNAPSHOT_PLUGIN_ID;
    }

    @Override
    public void postStop() {
        super.postStop();
        if (shutdownCancellable != null) {
            shutdownCancellable.cancel();
        }
    }

    @Override
    public Receive createReceiveRecover() {
        return ReceiveBuilder.create()
                // # Snapshot handling
                .match(SnapshotOffer.class, ss -> {
                    final ConnectionData fromSnapshotStore = snapshotAdapter.fromSnapshotStore(ss);
                    log.info("Received SnapshotOffer containing connectionStatus: <{}>", fromSnapshotStore);
                    if (fromSnapshotStore != null) {
                        amqpConnection = fromSnapshotStore.getAmqpConnection();
                        mappingContexts = fromSnapshotStore.getMappingContexts();
                        connectionStatus = fromSnapshotStore.getConnectionStatus();
                    }
                    lastSnapshotSequenceNr = ss.metadata().sequenceNr();
                })
                .match(ConnectionCreated.class, event -> {
                    amqpConnection = event.getAmqpConnection();
                    mappingContexts = event.getMappingContexts();
                    connectionStatus = ConnectionStatus.OPEN;
                })
                .match(ConnectionOpened.class, event -> connectionStatus = ConnectionStatus.OPEN)
                .match(ConnectionClosed.class, event -> connectionStatus = ConnectionStatus.CLOSED)
                .match(ConnectionDeleted.class, event -> {
                    amqpConnection = null;
                    mappingContexts = Collections.emptyList();
                    connectionStatus = ConnectionStatus.CLOSED;
                })
                .match(RecoveryCompleted.class, rc -> {
                    if (amqpConnection != null) {
                        log.info("Connection '{}' was recovered.", amqpConnection.getId());
                        if (ConnectionStatus.OPEN.equals(connectionStatus)) {
                            log.debug("Opening connection {} after recovery.", amqpConnection.getId());
                            final CreateConnection connect = CreateConnection.of(amqpConnection, DittoHeaders.empty());
                            askClientActor("recovery-connect", connect,
                                    (origin, response) -> log.info("CreateConnection result: {}", response));
                        }
                        getContext().become(connectionCreatedBehaviour);
                    }

                    scheduleShutdown();
                    getContext().getParent().tell(ConnectionSupervisorActor.ManualReset.getInstance(), getSelf());
                })
                .matchAny(m -> log.warning("Unknown recover message: {}", m))
                .build();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(CreateConnection.class, this::createConnection)
                .match(AmqpBridgeCommand.class, this::handleCommandDuringInitialization)
                .match(Shutdown.class, shutdown -> stopSelf())
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private Receive createConnectionCreatedBehaviour() {
        return ReceiveBuilder.create()
                .match(CreateConnection.class,
                        // TODO DG handle this as "update"?
                        // at least we must send a response
                        created -> log.info("Connection {} already exists. Ignoring.", created.getId()))
                .match(OpenConnection.class, this::openConnection)
                .match(CloseConnection.class, this::closeConnection)
                .match(DeleteConnection.class, this::deleteConnection)
                .match(RetrieveConnection.class, this::retrieveConnection)
                .match(RetrieveConnectionStatus.class, this::retrieveConnectionStatus)
                .match(Shutdown.class, shutdown -> log.debug("Dropping Shutdown in created behaviour state."))
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void createConnection(final CreateConnection command) {
        amqpConnection = command.getAmqpConnection();
        mappingContexts = command.getMappingContexts();

        final ConnectionCreated connectionCreated =
                ConnectionCreated.of(amqpConnection, mappingContexts, command.getDittoHeaders());
        persistEvent(connectionCreated,
                persistedEvent -> {
                    askClientActor("connect", command, (origin, response) -> {
                        origin.tell(
                                CreateConnectionResponse.of(amqpConnection, mappingContexts, command.getDittoHeaders()),
                                getSelf());
                        getContext().become(connectionCreatedBehaviour);
                        getContext().getParent().tell(ConnectionSupervisorActor.ManualReset.getInstance(), getSelf());
                    });
                });
    }

    private void openConnection(final OpenConnection command) {
        final ConnectionOpened connectionOpened =
                ConnectionOpened.of(amqpConnection.getId(), command.getDittoHeaders());
        persistEvent(connectionOpened,
                persistedEvent -> {
                    final CreateConnection connect = CreateConnection.of(amqpConnection, command.getDittoHeaders());
                    askClientActor("connect", connect, (origin, response) -> origin.tell(
                            OpenConnectionResponse.of(amqpConnection.getId(), command.getDittoHeaders()), self()));
                });
    }

    private void closeConnection(final CloseConnection command) {
        final ConnectionClosed connectionClosed =
                ConnectionClosed.of(amqpConnection.getId(), command.getDittoHeaders());
        persistEvent(connectionClosed,
                persistedEvent -> {
                    askClientActor("disconnect", command, (origin, response) -> {
                        origin.tell(CloseConnectionResponse.of(amqpConnection.getId(), command.getDittoHeaders()),
                                self());
                    });
                });
    }

    private void deleteConnection(final DeleteConnection command) {
        final ConnectionDeleted connectionDeleted =
                ConnectionDeleted.of(amqpConnection.getId(), command.getDittoHeaders());
        persistEvent(connectionDeleted,
                persistedEvent -> {
                    askClientActor("disconnect", command, (origin, response) -> {
                        stopClientActor();
                        origin.tell(DeleteConnectionResponse.of(amqpConnection.getId(), command.getDittoHeaders()),
                                self());
                        stopSelf();
                    });
                });
    }

    private void askClientActor(final String action, final Command<?> cmd,
            final BiConsumer<ActorRef, Object> onSuccess) {

        startClientActorIfRequired();

        final ActorRef origin = getSender();
        long timeout = Optional.ofNullable(cmd.getDittoHeaders()
                .get("timeout"))
                .map(Long::parseLong)
                .orElse(DEFAULT_TIMEOUT_MS);
        PatternsCS.ask(clientActor, cmd, timeout)
                .whenComplete((response, exception) -> {
                    log.debug("Got response to {}: {}", cmd.getType(), exception == null ? response : exception);
                    if (exception != null) {
                        handleException(action, origin, exception);
                    } else if (response instanceof Status.Failure) {
                        handleException(action, origin, ((Status.Failure) response).cause());
                    } else {
                        onSuccess.accept(origin, response);
                    }
                });
    }

    private void handleException(final String action, final ActorRef origin, final Throwable exception) {
        final ConnectionFailedException error = ConnectionFailedException.newBuilder(connectionId)
                .description(exception.getMessage())
                .build();

        origin.tell(error, getSelf());
        log.error(exception, "Operation '{}' on connection '{}' failed: {}.", action,
                amqpConnection.getId(),
                exception.getMessage());
    }

    private void retrieveConnection(final RetrieveConnection command) {
        getSender().tell(RetrieveConnectionResponse.of(amqpConnection, mappingContexts, command.getDittoHeaders()),
                getSelf());
    }

    private void retrieveConnectionStatus(final RetrieveConnectionStatus command) {
        getSender().tell(RetrieveConnectionStatusResponse.of(amqpConnection.getId(), connectionStatus,
                command.getDittoHeaders()), getSelf());
    }

    private void handleCommandDuringInitialization(final AmqpBridgeCommand command) {
        log.debug("Unexpected command during initialization of actor received: {} - "
                        + "Terminating this actor and sending 'ConnectionNotAccessibleException' to requester..",
                command.getType());
        getSender().tell(ConnectionNotAccessibleException.newBuilder(command.getId())
                .dittoHeaders(command.getDittoHeaders())
                .build(), getSelf());
    }

    private <E extends Event> void persistEvent(final E event, final Consumer<E> consumer) {
        persist(event, persistedEvent -> {
            log.debug("Successfully persisted Event '{}'", persistedEvent.getType());
            consumer.accept(persistedEvent);
            pubSubMediator.tell(new DistributedPubSubMediator.Publish(event.getType(), event, true), getSelf());

            // save a snapshot if there were too many changes since the last snapshot
            if ((lastSequenceNr() - lastSnapshotSequenceNr) > snapshotThreshold) {
                doSaveSnapshot();
            }
        });
    }

    private void doSaveSnapshot() {
        if (snapshotInProgress) {
            log.debug("Already requested taking a Snapshot - not doing it again");
        } else {
            snapshotInProgress = true;
            final ConnectionData connectionData = new ConnectionData(amqpConnection, connectionStatus, mappingContexts);
            log.info("Attempting to save Snapshot for '{}' ..", connectionData);
            // save a snapshot
            final Object snapshotToStore = snapshotAdapter.toSnapshotStore(connectionData);
            saveSnapshot(snapshotToStore);
        }
    }

    private void startClientActorIfRequired() {
        ConditionChecker.checkNotNull(connectionId, "connectionId");
        ConditionChecker.checkNotNull(amqpConnection, "amqpConnection");
        if (clientActor == null) {
            final int consumerCount = amqpConnection.getConsumerCount();
            log.info("Starting ClientActor for connection <{}> with {} consumers.", connectionId, consumerCount);
            final Props props = propsFactory.getActorPropsForType(self(), connectionId);
            final ClusterRouterPoolSettings clusterRouterPoolSettings =
                    new ClusterRouterPoolSettings(consumerCount, 1, true, singleton(CLUSTER_ROLE));
            final BroadcastPool broadcastPool = new BroadcastPool(consumerCount);
            final Props clusterRouterPoolProps =
                    new ClusterRouterPool(broadcastPool, clusterRouterPoolSettings).props(props);
            clientActor = getContext().actorOf(clusterRouterPoolProps, "rtr-" + connectionId);
        } else {
            log.debug("ClientActor already started.");
        }
    }

    private void stopClientActor() {
        if (clientActor != null) {
            log.debug("Stopping the client actor.");
            stopChildActor(clientActor);
            clientActor = null;
        }
    }

    private ActorRef startChildActor(final String name, final Props props) {
        log.debug("Starting child actor '{}'", name);
        final String nameEscaped = name.replace('/', '_');
        return getContext().actorOf(props, nameEscaped);
    }

    private void stopChildActor(final ActorRef actor) {
        log.debug("Stopping child actor '{}'", actor.path());
        getContext().stop(actor);
    }

    private void stopSelf() {
        log.debug("Shutting down");
        // stop the supervisor (otherwise it'd restart this actor) which causes this actor to stop, too.
        getContext().getParent().tell(PoisonPill.getInstance(), getSelf());
    }

    private void scheduleShutdown() {
        shutdownCancellable = getContext().getSystem().scheduler()
                .scheduleOnce(SHUTDOWN_DELAY,
                        getSelf(),
                        Shutdown.getInstance(),
                        getContext().dispatcher(),
                        ActorRef.noSender());
    }

    private static class Shutdown {

        private Shutdown() {
            // no-op
        }

        private static Shutdown getInstance() {
            return new Shutdown();
        }

    }

}
