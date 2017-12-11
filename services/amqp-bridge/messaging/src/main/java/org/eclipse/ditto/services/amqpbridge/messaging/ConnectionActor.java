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

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.Session;
import javax.naming.NamingException;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.ConnectionStatus;
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
import org.eclipse.ditto.signals.events.amqpbridge.AmqpBridgeEvent;
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
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import akka.persistence.SnapshotOffer;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor which manages a connection to AMQP 1.0 server.
 */
final class ConnectionActor extends AbstractPersistentActor implements ExceptionListener {

    private static final String PERSISTENCE_ID_PREFIX = "connection:";

    private static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-connection-journal";
    private static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-connection-snapshots";

    private static final int SHUTDOWN_DELAY_SECONDS = 10;
    private static final FiniteDuration SHUTDOWN_DELAY = Duration.apply(SHUTDOWN_DELAY_SECONDS, TimeUnit.SECONDS);

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final String connectionId;
    private final ActorRef pubSubMediator;
    private final String pubSubTargetActorPath;
    private final JmsConnectionFactory jmsConnectionFactory;

    private final long snapshotThreshold;
    private final SnapshotAdapter<ConnectionData> snapshotAdapter;

    private final Receive connectionCreatedBehaviour;

    private ActorRef commandProcessor;
    private AmqpConnection amqpConnection;
    private ConnectionStatus connectionStatus;
    private Cancellable shutdownCancellable;

    private Connection jmsConnection;
    private Session jmsSession;

    private long lastSnapshotSequenceNr = -1L;
    private boolean snapshotInProgress = false;

    private ConnectionActor(final String connectionId, final ActorRef pubSubMediator,
            final String pubSubTargetActorPath, final JmsConnectionFactory jmsConnectionFactory) {
        this.connectionId = connectionId;
        this.pubSubMediator = pubSubMediator;
        this.pubSubTargetActorPath = pubSubTargetActorPath;
        this.jmsConnectionFactory = jmsConnectionFactory;

        final Config config = getContext().system().settings().config();
        snapshotThreshold = config.getLong(ConfigKeys.Connection.SNAPSHOT_THRESHOLD);
        if (snapshotThreshold < 0) {
            throw new ConfigurationException(String.format("Config setting '%s' must be positive, but is: %d.",
                    ConfigKeys.Connection.SNAPSHOT_THRESHOLD, snapshotThreshold));
        }
        snapshotAdapter = new MongoConnectionSnapshotAdapter(getContext().system());

        connectionCreatedBehaviour = createConnectionCreatedBehaviour();
        connectionStatus = ConnectionStatus.CLOSED;
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param connectionId the identifier of the connection this actor persists.
     * @param pubSubMediator the akka pubsub mediator actor.
     * @param pubSubTargetActorPath the path of the command consuming actor (via pubsub).
     * @param jmsConnectionFactory the context factory to create a context for the amqp driver.
     * @return the Akka configuration Props object.
     */
    public static Props props(final String connectionId, final ActorRef pubSubMediator,
            final String pubSubTargetActorPath, final JmsConnectionFactory jmsConnectionFactory) {
        return Props.create(ConnectionActor.class, new Creator<ConnectionActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ConnectionActor create() {
                return new ConnectionActor(connectionId, pubSubMediator, pubSubTargetActorPath, jmsConnectionFactory);
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
    public void onException(final JMSException exception) {
        log.error("{} occurred: {}", exception.getClass().getName(), exception.getMessage());
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
                        connectionStatus = fromSnapshotStore.getConnectionStatus();
                    }
                    lastSnapshotSequenceNr = ss.metadata().sequenceNr();
                })
                .match(ConnectionCreated.class, event -> {
                    amqpConnection = event.getAmqpConnection();
                    connectionStatus = ConnectionStatus.OPEN;
                })
                .match(ConnectionOpened.class, event -> connectionStatus = ConnectionStatus.OPEN)
                .match(ConnectionClosed.class, event -> connectionStatus = ConnectionStatus.CLOSED)
                .match(ConnectionDeleted.class, event -> {
                    amqpConnection = null;
                    connectionStatus = ConnectionStatus.CLOSED;
                })
                .match(RecoveryCompleted.class, rc -> {
                    if (amqpConnection != null) {
                        log.info("Connection '{}' was recovered.", amqpConnection.getId());

                        if (ConnectionStatus.OPEN.equals(connectionStatus)) {
                            jmsConnection = jmsConnectionFactory.createConnection(amqpConnection, this);
                            startCommandConsumers();
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
                .match(OpenConnection.class, this::openConnection)
                .match(CloseConnection.class, this::closeConnection)
                .match(DeleteConnection.class, this::deleteConnection)
                .match(RetrieveConnection.class, this::retrieveConnection)
                .match(RetrieveConnectionStatus.class, this::retrieveConnectionStatus)
                .match(Shutdown.class, shutdown -> log.debug("Dropping Shutdown in created behaviour state."))
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchAny(m -> {
                    log.debug("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void createConnection(final CreateConnection command) {
        amqpConnection = command.getAmqpConnection();

        try {
            jmsConnection = jmsConnectionFactory.createConnection(amqpConnection, this);
            log.info("Connection '{}' created.", amqpConnection.getId());
        } catch (final JMSRuntimeException | JMSException | NamingException e) {
            final ConnectionFailedException error = ConnectionFailedException.newBuilder(connectionId)
                    .description(e.getMessage())
                    .build();
            getSender().tell(error, getSelf());
            log.error(e, "Failed to create Connection '{}' with Error: '{}'.", amqpConnection.getId(), e.getMessage());
            return;
        }

        final ConnectionCreated connectionCreated = ConnectionCreated.of(amqpConnection, command.getDittoHeaders());
        persistEvent(connectionCreated, persistedEvent -> {
            final boolean success = startCommandConsumersWithErrorHandling("create");
            if (!success) {
                return;
            }

            getSender().tell(CreateConnectionResponse.of(amqpConnection, command.getDittoHeaders()), getSelf());
            getContext().become(connectionCreatedBehaviour);
            getContext().getParent().tell(ConnectionSupervisorActor.ManualReset.getInstance(), getSelf());
        });
    }

    private void openConnection(final OpenConnection command) {

        final ConnectionOpened connectionOpened =
                ConnectionOpened.of(amqpConnection.getId(), command.getDittoHeaders());
        persistEvent(connectionOpened,
                persistedEvent -> {
                    final boolean success = startCommandConsumersWithErrorHandling("open");
                    if (!success) {
                        return;
                    }

                    getSender().tell(OpenConnectionResponse.of(amqpConnection.getId(),
                            command.getDittoHeaders()), getSender());
                });
    }

    private void closeConnection(final CloseConnection command) {

        final ConnectionClosed connectionClosed =
                ConnectionClosed.of(amqpConnection.getId(), command.getDittoHeaders());
        persistEvent(connectionClosed, persistedEvent -> {
            final boolean success = stopCommandConsumersWithErrorHandling("close");
            if (!success) {
                return;
            }

            getSender().tell(CloseConnectionResponse.of(amqpConnection.getId(),
                    command.getDittoHeaders()), getSelf());
        });
    }

    private void deleteConnection(final DeleteConnection command) {

        final ConnectionDeleted connectionDeleted =
                ConnectionDeleted.of(amqpConnection.getId(), command.getDittoHeaders());
        persistEvent(connectionDeleted, persistedEvent -> {
            final boolean success = stopCommandConsumersWithErrorHandling("delete");
            if (!success) {
                return;
            }

            getSender().tell(DeleteConnectionResponse.of(amqpConnection.getId(), command.getDittoHeaders()), getSelf());
            stopSelf();
        });
    }

    private void retrieveConnection(final RetrieveConnection command) {
        getSender().tell(RetrieveConnectionResponse.of(amqpConnection, command.getDittoHeaders()), getSelf());
    }

    private void retrieveConnectionStatus(final RetrieveConnectionStatus command) {
        getSender().tell(RetrieveConnectionStatusResponse.of(amqpConnection.getId(), connectionStatus,
                command.getDittoHeaders()), getSelf());
    }

    private boolean startCommandConsumersWithErrorHandling(final String action) {
        try {
            startCommandConsumers();
            return true;
        } catch (final JMSRuntimeException | JMSException e) {
            final ConnectionFailedException error = ConnectionFailedException.newBuilder(connectionId)
                    .description(e.getMessage())
                    .build();
            getSender().tell(error, getSelf());
            log.error(e, "Failed to <{}> Connection <{}> with Error: <{}>.", action, amqpConnection.getId(), e.getMessage());
            return false;
        }
    }

    private boolean stopCommandConsumersWithErrorHandling(final String action) {
        try {
            stopCommandConsumers();
            return true;
        } catch (final JMSRuntimeException | JMSException e) {
            final ConnectionFailedException error = ConnectionFailedException.newBuilder(connectionId)
                    .description(e.getMessage())
                    .build();
            getSender().tell(error, getSelf());
            log.error(e, "Failed to <{}> Connection <{}> with Error: <{}>.", action, amqpConnection.getId(), e.getMessage());
            return false;
        }
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
            pubSubMediator.tell(new DistributedPubSubMediator.Publish(AmqpBridgeEvent.TYPE_PREFIX, event, true),
                    getSelf());

            // save a snapshot if there were too many changes since the last snapshot
            if ((lastSequenceNr() - lastSnapshotSequenceNr) > snapshotThreshold) {
                doSaveSnapshot();
            }
        });
    }

    private void startCommandConsumers() throws JMSException {
        startConnection();

        final Props amqpCommandProcessorProps =
                CommandProcessorActor.props(pubSubMediator, pubSubTargetActorPath,
                        amqpConnection.getAuthorizationSubject());
        final String amqpCommandProcessorName = CommandProcessorActor.ACTOR_NAME_PREFIX + amqpConnection.getId();
        commandProcessor = startChildActor(amqpCommandProcessorName, amqpCommandProcessorProps);

        for (final String source : amqpConnection.getSources()) {
            startCommandConsumer(source);
        }

        log.info("Subscribed Connection '{}' to sources: {}", amqpConnection.getId(), amqpConnection.getSources());
        connectionStatus = ConnectionStatus.OPEN;
    }

    private void stopCommandConsumers() throws JMSException {
        if (amqpConnection != null) {
            for (final String source : amqpConnection.getSources()) {
                stopChildActor(source);
            }

            stopChildActor(CommandProcessorActor.ACTOR_NAME_PREFIX + amqpConnection.getId());

            log.info("Unsubscribed Connection '{}' from sources: {}", amqpConnection.getId(),
                    amqpConnection.getSources());
            connectionStatus = ConnectionStatus.CLOSED;

            stopConnection();
        }
    }

    private void startCommandConsumer(final String source) {
        final Props props = CommandConsumerActor.props(jmsSession, source, commandProcessor);
        final String name = CommandConsumerActor.ACTOR_NAME_PREFIX + source;
        startChildActor(name, props);
    }

    private void startConnection() throws JMSException {
        if (shutdownCancellable != null) {
            shutdownCancellable.cancel();
        }

        if (jmsConnection != null) {
            jmsConnection.start();
            jmsSession = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            log.info("Connection '{}' opened.", amqpConnection.getId());
        }
    }

    private void stopConnection() throws JMSException {
        if (jmsSession != null) {
            jmsSession.close();
        }
        if (jmsConnection != null) {
            jmsConnection.stop();
            jmsConnection.close();
            log.info("Connection '{}' closed.", amqpConnection.getId());
        }
    }

    private void doSaveSnapshot() {
        if (snapshotInProgress) {
            log.debug("Already requested taking a Snapshot - not doing it again");
        } else {
            snapshotInProgress = true;
            final ConnectionData connectionData = new ConnectionData(amqpConnection, connectionStatus);
            log.info("Attempting to save Snapshot for '{}' ..", connectionData);
            // save a snapshot
            final Object snapshotToStore = snapshotAdapter.toSnapshotStore(connectionData);
            saveSnapshot(snapshotToStore);
        }
    }

    private ActorRef startChildActor(final String name, final Props props) {
        log.debug("Starting child actor '{}'", name);
        final String nameEscaped = name.replace('/', '_');
        return getContext().actorOf(props, nameEscaped);
    }

    private void stopChildActor(final String name) {
        log.debug("Stopping child actor '{}'", name);
        final String nameEscaped = name.replace('/', '_');
        getContext().findChild(nameEscaped).ifPresent(getContext()::stop);
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

        static Shutdown getInstance() {
            return new Shutdown();
        }

    }

}
