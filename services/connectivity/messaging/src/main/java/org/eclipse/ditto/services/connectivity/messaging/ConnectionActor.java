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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.models.connectivity.ConnectivityMessagingConstants.CLUSTER_ROLE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionMongoSnapshotAdapter;
import org.eclipse.ditto.services.connectivity.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.AggregatedConnectivityCommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityErrorResponse;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionConflictException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatusResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.connectivity.ConnectionClosed;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.connectivity.ConnectionDeleted;
import org.eclipse.ditto.signals.events.connectivity.ConnectionModified;
import org.eclipse.ditto.signals.events.connectivity.ConnectionOpened;

import com.typesafe.config.Config;

import akka.ConfigurationException;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
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
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotOffer;
import akka.routing.Broadcast;
import akka.routing.RoundRobinPool;
import scala.concurrent.duration.Duration;

/**
 * Handles {@code *Connection} commands and manages the persistence of connection. The actual connection handling to the
 * remote server is delegated to a child actor that uses a specific client (AMQP 1.0 or 0.9.1).
 */
final class ConnectionActor extends AbstractPersistentActor {

    private static final String PERSISTENCE_ID_PREFIX = "connection:";

    private static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-connection-journal";
    private static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-connection-snapshots";

    private static final long DEFAULT_TIMEOUT_MS = 5000;

    private static final String PUB_SUB_GROUP_PREFIX = "connection:";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final String connectionId;
    private final ActorRef pubSubMediator;
    private final long snapshotThreshold;
    private final SnapshotAdapter<Connection> snapshotAdapter;
    private final ConnectionActorPropsFactory propsFactory;
    private final Receive connectionCreatedBehaviour;

    @Nullable private ActorRef clientActor;
    @Nullable private Connection connection;

    private long lastSnapshotSequenceNr = -1L;
    private boolean snapshotInProgress = false;

    private Set<String> uniqueTopicPaths = Collections.emptySet();

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
        snapshotAdapter = new ConnectionMongoSnapshotAdapter();
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
        log.info("stopped connection <{}>", connectionId);
    }

    @Override
    public Receive createReceiveRecover() {
        return ReceiveBuilder.create()
                // # Snapshot handling
                .match(SnapshotOffer.class, ss -> {
                    final Connection fromSnapshotStore = snapshotAdapter.fromSnapshotStore(ss);
                    log.info("Received SnapshotOffer containing connection: <{}>", fromSnapshotStore);
                    if (fromSnapshotStore != null) {
                        connection = fromSnapshotStore;
                    }
                    lastSnapshotSequenceNr = ss.metadata().sequenceNr();
                })
                .match(ConnectionCreated.class, event -> {
                    connection = event.getConnection();
                })
                .match(ConnectionOpened.class, event -> connection = connection != null ? connection.toBuilder()
                        .connectionStatus(ConnectionStatus.OPEN).build() : null)
                .match(ConnectionClosed.class, event -> connection = connection != null ? connection.toBuilder()
                        .connectionStatus(ConnectionStatus.CLOSED).build() : null)
                .match(ConnectionDeleted.class, event -> {
                    connection = null;
                })
                .match(RecoveryCompleted.class, rc -> {
                    log.info("Connection '{}' was recovered: {}", connectionId, connection);
                    if (connection != null) {
                        if (ConnectionStatus.OPEN.equals(connection.getConnectionStatus())) {
                            log.debug("Opening connection {} after recovery.", connectionId);

                            final CreateConnection connect = CreateConnection.of(connection, DittoHeaders.empty());

                            final ActorRef origin = getSender();
                            askClientActor(connect,
                                    response -> log.info("CreateConnection result: {}", response),
                                    error -> handleException("recovery-connect", origin, error)
                            );
                            subscribeForEvents();
                        }
                        getContext().become(connectionCreatedBehaviour);
                    }

                    getContext().getParent().tell(ConnectionSupervisorActor.ManualReset.getInstance(), getSelf());
                })
                .matchAny(m -> log.warning("Unknown recover message: {}", m))
                .build();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(TestConnection.class, this::testConnection)
                .match(CreateConnection.class, this::createConnection)
                .match(ConnectivityCommand.class, this::handleCommandDuringInitialization)
                .match(Shutdown.class, shutdown -> stopSelf())
                .match(Status.Failure.class, f -> log.warning("Got failure in initial behaviour with cause {}: {}",
                        f.cause().getClass().getSimpleName(), f.cause().getMessage()))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private Receive createConnectionCreatedBehaviour() {
        return ReceiveBuilder.create()
                .match(TestConnection.class, testConnection ->
                        getSender().tell(
                                TestConnectionResponse.alreadyCreated(testConnection.getConnectionId(),
                                        testConnection.getDittoHeaders()),
                                getSelf()))
                .match(CreateConnection.class, createConnection -> {
                    enhanceLogUtil(createConnection);
                    log.info("Connection <{}> already exists, responding with conflict", createConnection.getId());
                    final ConnectionConflictException conflictException =
                            ConnectionConflictException.newBuilder(createConnection.getId())
                                    .dittoHeaders(createConnection.getDittoHeaders())
                                    .build();
                    getSender().tell(conflictException, getSelf());
                })
                .match(ModifyConnection.class, this::modifyConnection)
                .match(OpenConnection.class, this::openConnection)
                .match(CloseConnection.class, this::closeConnection)
                .match(DeleteConnection.class, this::deleteConnection)
                .match(RetrieveConnection.class, this::retrieveConnection)
                .match(RetrieveConnectionStatus.class, this::retrieveConnectionStatus)
                .match(RetrieveConnectionMetrics.class, this::retrieveConnectionMetrics)
                .match(Signal.class, this::handleSignal)
                .match(DistributedPubSubMediator.SubscribeAck.class, this::handleSubscribeAck)
                .match(DistributedPubSubMediator.UnsubscribeAck.class, this::handleUnsubscribeAck)
                .match(SaveSnapshotSuccess.class, this::handleSnapshotSuccess)
                .match(Shutdown.class, shutdown -> log.debug("Dropping Shutdown in created behaviour state."))
                .match(Status.Failure.class, f -> log.warning("Got failure in connectionCreated behaviour with " +
                        "cause {}: {}", f.cause().getClass().getSimpleName(), f.cause().getMessage()))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void enhanceLogUtil(final WithDittoHeaders<?> createConnection) {
        LogUtil.enhanceLogWithCorrelationId(log, createConnection);
        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId);
    }

    private void handleSignal(final Signal<?> signal) {
        enhanceLogUtil(signal);
        if (clientActor == null) {
            log.debug("Cannot forward thing event, client actor not ready.");
            return;
        }
        if (connection == null) {
            log.debug("No Connection configuration available.");
            return;
        }
        if (uniqueTopicPaths.isEmpty()) {
            log.debug("Not forwarding anything.");
            return;
        }
        if (connectionId.equals(signal.getDittoHeaders().getOrigin().orElse(null))) {
            log.debug("Dropping signal, was sent by myself.");
            return;
        }
        final String topicPath = TopicPathMapper.mapSignalToTopicPath(signal);
        if (!uniqueTopicPaths.contains(topicPath)) {
            log.debug("Dropping signal, topic '{}' is not subscribed.", topicPath);
            return;
        }
        // forward to client actor if topic was subscribed and connection is authorized to read
        if (isAuthorized(signal, connection.getAuthorizationContext())) {
            log.debug("Forwarding signal <{}> to client actor.", signal.getType());
            clientActor.tell(signal, getSelf());
        }
    }

    private boolean isAuthorized(final Signal<?> signal, final AuthorizationContext authorizationContext) {
        final Set<String> authorizedReadSubjects = signal.getDittoHeaders().getReadSubjects();
        final List<String> connectionSubjects = authorizationContext.getAuthorizationSubjectIds();
        return !Collections.disjoint(authorizedReadSubjects, connectionSubjects);
    }

    private void testConnection(final TestConnection command) {
        final ActorRef origin = getSender();
        if (!isConnectionConfigurationValid(command.getConnection(), origin)) {
            return;
        }

        connection = command.getConnection();

        askClientActor(command, response -> {
            origin.tell(
                    TestConnectionResponse.success(command.getConnectionId(), response.toString(),
                            command.getDittoHeaders()),
                    getSelf());
            // terminate this actor's supervisor after a connection test again:
            stopSelf();
        }, error -> {
            handleException("test", origin, error);
            // terminate this actor's supervisor after a connection test again:
            stopSelf();
        });
    }

    private void createConnection(final CreateConnection command) {
        final ActorRef origin = getSender();
        if (!isConnectionConfigurationValid(command.getConnection(), origin)) {
            return;
        }

        final ConnectionCreated connectionCreated =
                ConnectionCreated.of(command.getConnection(), command.getDittoHeaders());

        persistEvent(connectionCreated, persistedEvent -> {
            connection = persistedEvent.getConnection();

            askClientActor(command, response -> {
                        getContext().become(connectionCreatedBehaviour);
                        subscribeForEvents();
                        origin.tell(
                                CreateConnectionResponse.of(connection, command.getDittoHeaders()),
                                getSelf());
                        getContext().getParent().tell(ConnectionSupervisorActor.ManualReset.getInstance(), getSelf());
                    }, error -> {
                        getContext().become(connectionCreatedBehaviour);
                        handleException("connect", origin, error);
                        getContext().getParent().tell(ConnectionSupervisorActor.ManualReset.getInstance(), getSelf());
                    }
            );
        });
    }

    private boolean isConnectionConfigurationValid(final Connection connection, final ActorRef origin) {
        try {
            // try to create actor props before persisting the connection to fail early
            propsFactory.getActorPropsForType(connection);
            return true;
        } catch (final Exception e) {
            handleException("connect", origin, e);
            stopSelf();
            return false;
        }
    }

    private void modifyConnection(final ModifyConnection command) {
        final ActorRef origin = getSender();
        if (!isConnectionConfigurationValid(command.getConnection(), origin)) {
            return;
        }

        if (connection != null && !connection.getConnectionType().equals(command.getConnection().getConnectionType())) {
            handleException("modify", origin, ConnectionConfigurationInvalidException
                    .newBuilder("ConnectionType '" + connection.getConnectionType().getName() +
                            "' of existing connection '" + connectionId + "' cannot be changed")
                    .dittoHeaders(command.getDittoHeaders())
                    .build()
            );
            return;
        }

        final ConnectionModified connectionModified =
                ConnectionModified.of(command.getConnection(), command.getDittoHeaders());

        persistEvent(connectionModified, persistedEvent -> {
            connection = persistedEvent.getConnection();

            askClientActor(command, response -> {
                        getContext().become(connectionCreatedBehaviour);
                        subscribeForEvents();
                        origin.tell(
                                ModifyConnectionResponse.modified(connectionId, command.getDittoHeaders()),
                                getSelf());
                        getContext().getParent().tell(ConnectionSupervisorActor.ManualReset.getInstance(), getSelf());
                    }, error ->
                            handleException("connect-after-modify", origin, error)
            );
        });
    }

    private void openConnection(final OpenConnection command) {
        checkNotNull(connection, "Connection");

        final ConnectionOpened connectionOpened =
                ConnectionOpened.of(command.getConnectionId(), command.getDittoHeaders());
        final ActorRef origin = getSender();

        persistEvent(connectionOpened, persistedEvent -> {
            connection.toBuilder().connectionStatus(ConnectionStatus.OPEN).build();
            askClientActor(command, response -> {
                        subscribeForEvents();
                        origin.tell(OpenConnectionResponse.of(connectionId, command.getDittoHeaders()), getSelf());
                    }, error ->
                            handleException("open-connection", origin, error)
            );
        });
    }

    private void closeConnection(final CloseConnection command) {

        final ConnectionClosed connectionClosed =
                ConnectionClosed.of(command.getConnectionId(), command.getDittoHeaders());
        final ActorRef origin = getSender();

        persistEvent(connectionClosed, persistedEvent -> {
            if (connection != null) {
                connection = connection.toBuilder().connectionStatus(ConnectionStatus.CLOSED).build();
            }
            askClientActor(command, response -> {
                        origin.tell(CloseConnectionResponse.of(connectionId, command.getDittoHeaders()),
                                getSelf());
                        unsubscribeFromEvents();
                    }, error ->
                            handleException("disconnect", origin, error)
            );
        });
    }

    private void deleteConnection(final DeleteConnection command) {

        final ConnectionDeleted connectionDeleted =
                ConnectionDeleted.of(command.getConnectionId(), command.getDittoHeaders());
        final ActorRef origin = getSender();

        persistEvent(connectionDeleted, persistedEvent ->
                askClientActor(command, response -> {
                            unsubscribeFromEvents();
                            stopClientActor();
                            origin.tell(DeleteConnectionResponse.of(connectionId, command.getDittoHeaders()),
                                    getSelf());
                            stopSelf();
                        }, error -> {
                            // we can safely ignore this error and do the same as in the "success" case:
                            unsubscribeFromEvents();
                            stopClientActor();
                            origin.tell(DeleteConnectionResponse.of(connectionId, command.getDittoHeaders()),
                                    getSelf());
                            stopSelf();
                        }
                )
        );
    }

    private void askClientActor(final Command<?> cmd, final Consumer<Object> onSuccess,
            final Consumer<Throwable> onError) {

        startClientActorIfRequired();
        final long timeout = Optional.ofNullable(cmd.getDittoHeaders().get("timeout"))
                .map(Long::parseLong)
                .orElse(DEFAULT_TIMEOUT_MS);
        // wrap in Broadcast message because these management messages must be delivered to each client actor
        if (clientActor != null && connection != null) {
            final ActorRef aggregationActor = getContext().actorOf(
                    AggregateActor.props(connectionId, clientActor, connection.getClientCount(), timeout));
            PatternsCS.ask(aggregationActor, cmd, timeout)
                    .whenComplete((response, exception) -> {
                        log.debug("Got response to {}: {}", cmd.getType(), exception == null ? response : exception);
                        if (exception != null) {
                            onError.accept(exception);
                        } else if (response instanceof Status.Failure) {
                            final Throwable cause = ((Status.Failure) response).cause();
                            onError.accept(cause);
                        } else if (response instanceof DittoRuntimeException) {
                            onError.accept((DittoRuntimeException) response);
                        } else {
                            onSuccess.accept(response);
                        }
                    });
        }
    }

    private void handleException(final String action, final ActorRef origin, final Throwable exception) {
        final DittoRuntimeException dre;
        if (exception instanceof DittoRuntimeException) {
            dre = (DittoRuntimeException) exception;
        } else {
            dre = ConnectionFailedException.newBuilder(connectionId)
                    .description(exception.getMessage())
                    .cause(exception)
                    .build();
        }

        origin.tell(dre, getSelf());
        log.warning("Operation <{}> on connection <{}> failed due to {}: {}.", action, connectionId,
                dre.getClass().getSimpleName(), dre.getMessage());
    }

    private void retrieveConnection(final RetrieveConnection command) {
        checkNotNull(connection, "Connection");
        getSender().tell(RetrieveConnectionResponse.of(connection, command.getDittoHeaders()), getSelf());
    }

    private void retrieveConnectionStatus(final RetrieveConnectionStatus command) {
        checkNotNull(connection, "Connection");
        getSender().tell(RetrieveConnectionStatusResponse.of(connectionId, connection.getConnectionStatus(),
                command.getDittoHeaders()), getSelf());
    }

    private void retrieveConnectionMetrics(final RetrieveConnectionMetrics command) {
        checkNotNull(connection, "Connection");

        final ActorRef origin = getSender();
        askClientActor(command,
                response -> origin.tell(response, getSelf()),
                error -> handleException("retrieve-metrics", origin, error));
    }

    private void subscribeForEvents() {
        checkNotNull(connection, "Connection");
        uniqueTopicPaths = connection.getTargets().stream()
                .flatMap(target -> target.getTopics().stream())
                .collect(Collectors.toSet());

        forEachPubSubTopicDo(pubSubTopic -> {
            final DistributedPubSubMediator.Subscribe subscribe =
                    new DistributedPubSubMediator.Subscribe(pubSubTopic, PUB_SUB_GROUP_PREFIX + connectionId,
                            getSelf());
            log.debug("Subscribing to pubsub topic '{}' for connection '{}'.", pubSubTopic, connectionId);
            pubSubMediator.tell(subscribe, getSelf());
        });
    }

    private void unsubscribeFromEvents() {
        forEachPubSubTopicDo(pubSubTopic -> {
            log.debug("Unsubscribing from pubsub topic '{}' for connection '{}'.", pubSubTopic, connectionId);
            final DistributedPubSubMediator.Unsubscribe unsubscribe =
                    new DistributedPubSubMediator.Unsubscribe(pubSubTopic, PUB_SUB_GROUP_PREFIX + connectionId,
                            getSelf());
            pubSubMediator.tell(unsubscribe, getSelf());
        });
    }

    private void forEachPubSubTopicDo(final Consumer<String> topicConsumer) {
        uniqueTopicPaths.stream()
                .map(TopicPathMapper::mapToPubSubTopic)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(topicConsumer);
    }

    private void handleCommandDuringInitialization(final ConnectivityCommand command) {
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
        } else if (connection != null) {
            snapshotInProgress = true;
            log.info("Attempting to save Snapshot for Connection: <{}> ..", connection);
            // save a snapshot
            final Object snapshotToStore = snapshotAdapter.toSnapshotStore(connection);
            saveSnapshot(snapshotToStore);
        } else {
            log.warning("Connection and MappingContext must not be null when taking snapshot.");
        }
    }

    private void startClientActorIfRequired() {
        checkNotNull(connectionId, "connectionId");
        checkNotNull(connection, "connection");
        if (clientActor == null) {
            final int clientCount = connection.getClientCount();
            log.info("Starting ClientActor for connection <{}> with <{}> clients.", connectionId, clientCount);
            final Props props = propsFactory.getActorPropsForType(connection);
            final ClusterRouterPoolSettings clusterRouterPoolSettings =
                    new ClusterRouterPoolSettings(clientCount, 1, true,
                            Collections.singleton(CLUSTER_ROLE));
            final RoundRobinPool roundRobinPool = new RoundRobinPool(clientCount);
            final Props clusterRouterPoolProps =
                    new ClusterRouterPool(roundRobinPool, clusterRouterPoolSettings).props(props);
            clientActor = getContext().actorOf(clusterRouterPoolProps, "client-router");
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

    private void stopChildActor(final ActorRef actor) {
        log.debug("Stopping child actor '{}'", actor.path());
        getContext().stop(actor);
    }

    private void stopSelf() {
        log.debug("Shutting down");
        // stop the supervisor (otherwise it'd restart this actor) which causes this actor to stop, too.
        getContext().getParent().tell(PoisonPill.getInstance(), getSelf());
    }

    private void handleSubscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
        log.debug("Successfully subscribed to distributed pub/sub on topic '{}'", subscribeAck.subscribe().topic());
    }

    private void handleUnsubscribeAck(final DistributedPubSubMediator.UnsubscribeAck unsubscribeAck) {
        log.debug("Successfully unsubscribed from distributed pub/sub on topic '{}'",
                unsubscribeAck.unsubscribe().topic());
    }

    private void handleSnapshotSuccess(final SaveSnapshotSuccess sss) {
        log.debug("Snapshot was saved successfully: {}", sss);
    }

    private static class Shutdown {

        private Shutdown() {
            // no-op
        }

        private static Shutdown getInstance() {
            return new Shutdown();
        }

    }

    /**
     * Local helper-actor which is started for aggregating several CommandResponses sent back by potentially several
     * {@code clientActors} (behind a cluster Router running on different cluster nodes).
     */
    private static class AggregateActor extends AbstractActor {

        private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

        private final List<CommandResponse<?>> aggregatedResults;
        private final Map<String, Status.Status> aggregatedStatus;

        private final String connectionId;
        private final ActorRef clientActor;
        private final int expectedResponses;
        private final long timeout;

        private int responseCount = 0;
        @Nullable private ActorRef origin;
        @Nullable private DittoHeaders originHeaders;

        /**
         * Creates Akka configuration object for this actor.
         *
         * @return the Akka configuration Props object
         */
        static Props props(final String connectionId, final ActorRef clientActor, final int expectedResponses,
                final long timeout) {
            return Props.create(AggregateActor.class, connectionId, clientActor, expectedResponses, timeout);
        }

        private AggregateActor(final String connectionId, final ActorRef clientActor, final int expectedResponses,
                final long timeout) {
            this.connectionId = connectionId;
            this.clientActor = clientActor;
            this.expectedResponses = expectedResponses;
            this.timeout = timeout;
            aggregatedResults = new ArrayList<>();
            aggregatedStatus = new HashMap<>();
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(Command.class, command -> {
                        clientActor.tell(new Broadcast(command), getSelf());
                        originHeaders = command.getDittoHeaders();
                        origin = getSender();
                        getContext().setReceiveTimeout(Duration.create(timeout / 2.0, TimeUnit.MILLISECONDS));
                    })
                    .match(ReceiveTimeout.class, timeout -> {
                        // send back (partially) gathered responses
                        sendBackAggregatedResults();
                    })
                    .matchAny(any -> {
                        if (any instanceof CommandResponse) {
                            aggregatedResults.add((CommandResponse<?>) any);
                        } else if (any instanceof Status.Status) {
                            aggregatedStatus.put(getSender().path().address().hostPort(), (Status.Status) any);
                        } else if (any instanceof DittoRuntimeException) {
                            aggregatedResults.add(ConnectivityErrorResponse.of((DittoRuntimeException) any));
                        } else {
                            log.error("Could not handle non-Jsonifiable non-Status response: {}", any);
                        }
                        responseCount++;
                        if (expectedResponses == responseCount) {
                            // send back all gathered responses
                            sendBackAggregatedResults();
                        }
                    })
                    .build();
        }

        private void sendBackAggregatedResults() {
            if (origin != null && originHeaders != null && !aggregatedResults.isEmpty()) {
                final String responseType = aggregatedResults.get(0).getType();
                final AggregatedConnectivityCommandResponse response =
                        AggregatedConnectivityCommandResponse.of(connectionId, aggregatedResults, responseType,
                                HttpStatusCode.OK, originHeaders);
                log.debug("Aggregated response: {}", response);
                origin.tell(response, getSelf());
            } else if (origin != null && originHeaders != null && !aggregatedStatus.isEmpty()) {
                log.debug("Aggregated stati: {}", this.aggregatedStatus);
                final Optional<Status.Status> failure = this.aggregatedStatus.entrySet().stream()
                        .filter(s -> s.getValue() instanceof Status.Failure)
                        .map(Map.Entry::getValue)
                        .findFirst();
                if (failure.isPresent()) {
                    origin.tell(failure.get(), getSelf());
                } else {
                    final String aggregatedStatusStr = this.aggregatedStatus.entrySet().stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(","));
                    origin.tell(new Status.Success(aggregatedStatusStr), getSelf());
                }
            } else {
                log.warning("No origin was present or results were empty in order to send back aggregated results to");
            }
            getContext().stop(getSelf());
        }
    }

}
