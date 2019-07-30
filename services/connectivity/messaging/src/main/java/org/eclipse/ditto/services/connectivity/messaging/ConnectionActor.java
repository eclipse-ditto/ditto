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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.models.connectivity.ConnectivityMessagingConstants.CLUSTER_ROLE;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.FilteredTopic;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.connectivity.messaging.amqp.AmqpValidator;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.SnapshotConfig;
import org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaValidator;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLoggerRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.RetrieveConnectionLogsAggregatorActor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.metrics.RetrieveConnectionMetricsAggregatorActor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.metrics.RetrieveConnectionStatusAggregatorActor;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttValidator;
import org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionMongoSnapshotAdapter;
import org.eclipse.ditto.services.connectivity.messaging.rabbitmq.RabbitMQValidator;
import org.eclipse.ditto.services.connectivity.messaging.validation.CompoundConnectivityCommandInterceptor;
import org.eclipse.ditto.services.connectivity.messaging.validation.ConnectionValidator;
import org.eclipse.ditto.services.connectivity.messaging.validation.DittoConnectivityCommandValidator;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cleanup.AbstractPersistentActorWithTimersAndCleanup;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionConflictException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CheckConnectionLogsActive;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.EnableConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.EnableConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.LoggingExpired;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.ConnectivityQueryCommand;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatusResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.connectivity.ConnectionClosed;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.connectivity.ConnectionDeleted;
import org.eclipse.ditto.signals.events.connectivity.ConnectionModified;
import org.eclipse.ditto.signals.events.connectivity.ConnectionOpened;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.Status;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.routing.ClusterRouterPool;
import akka.cluster.routing.ClusterRouterPoolSettings;
import akka.cluster.sharding.ShardRegion;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.persistence.RecoveryCompleted;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotOffer;
import akka.routing.Broadcast;
import akka.routing.RoundRobinPool;
import scala.concurrent.ExecutionContextExecutor;

/**
 * Handles {@code *Connection} commands and manages the persistence of connection. The actual connection handling to the
 * remote server is delegated to a child actor that uses a specific client (AMQP 1.0 or 0.9.1).
 */
public final class ConnectionActor extends AbstractPersistentActorWithTimersAndCleanup {

    /**
     * Prefix to prepend to the connection ID to construct the persistence ID.
     */
    public static final String PERSISTENCE_ID_PREFIX = "connection:";

    /**
     * The ID of the journal plugin this persistence actor uses.
     */
    public static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-connection-journal";
    /**
     * The ID of the snapshot plugin this persistence actor uses.
     */
    public static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-connection-snapshots";

    private static final Duration DELETED_ACTOR_LIFETIME = Duration.ofSeconds(10);
    private static final long DEFAULT_RETRIEVE_STATUS_TIMEOUT = 500L;

    private static final String PUB_SUB_GROUP_PREFIX = "connection:";

    /**
     * Message to self to trigger termination after deletion.
     */
    private static final Object STOP_SELF_IF_DELETED = new Object();

    /**
     * Validator of all supported connections.
     */
    private static final ConnectionValidator CONNECTION_VALIDATOR = ConnectionValidator.of(
            RabbitMQValidator.newInstance(),
            AmqpValidator.newInstance(),
            MqttValidator.newInstance(),
            KafkaValidator.getInstance());

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final String connectionId;
    private final ActorRef pubSubMediator;
    private final ActorRef conciergeForwarder;
    private final long snapshotThreshold;
    private final SnapshotAdapter<Connection> snapshotAdapter;
    private final ClientActorPropsFactory propsFactory;
    private final Consumer<ConnectivityCommand<?>> commandValidator;
    private final Receive connectionCreatedBehaviour;
    private final ConnectionLogger connectionLogger;
    private Instant connectionClosedAt = Instant.now();

    @Nullable private ActorRef clientActorRouter;
    @Nullable private Connection connection;
    @Nullable private SignalFilter signalFilter = null;

    private long lastSnapshotSequenceNr = -1L;
    private long confirmedSnapshotSequenceNr = -1L;

    private Set<Topic> uniqueTopics = Collections.emptySet();

    private final Duration flushPendingResponsesTimeout;
    private final Duration clientActorAskTimeout;
    @Nullable private Cancellable stopSelfIfDeletedTrigger;

    private final ConnectionMonitorRegistry<ConnectionMonitor> connectionMonitorRegistry;

    @Nullable private Cancellable enabledLoggingChecker;
    private final Duration checkLoggingActiveInterval;

    @Nullable private Instant loggingEnabledUntil;
    private final Duration loggingEnabledDuration;

    @SuppressWarnings("unused")
    private ConnectionActor(final String connectionId,
            final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final ClientActorPropsFactory propsFactory,
            @Nullable final Consumer<ConnectivityCommand<?>> customCommandValidator) {

        this.connectionId = connectionId;
        this.pubSubMediator = pubSubMediator;
        this.conciergeForwarder = conciergeForwarder;
        this.propsFactory = propsFactory;
        final DittoConnectivityCommandValidator dittoCommandValidator =
                new DittoConnectivityCommandValidator(propsFactory, conciergeForwarder, CONNECTION_VALIDATOR);

        if (customCommandValidator != null) {
            commandValidator =
                    new CompoundConnectivityCommandInterceptor(dittoCommandValidator, customCommandValidator);
        } else {
            commandValidator = dittoCommandValidator;
        }

        final ConnectivityConfig connectivityConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        final ConnectionConfig connectionConfig = connectivityConfig.getConnectionConfig();
        final SnapshotConfig snapshotConfig = connectionConfig.getSnapshotConfig();
        snapshotThreshold = snapshotConfig.getThreshold();
        snapshotAdapter = new ConnectionMongoSnapshotAdapter();
        connectionCreatedBehaviour = createConnectionCreatedBehaviour();

        flushPendingResponsesTimeout = connectionConfig.getFlushPendingResponsesTimeout();
        clientActorAskTimeout = connectionConfig.getClientActorAskTimeout();


        final MonitoringConfig monitoringConfig = connectivityConfig.getMonitoringConfig();
        connectionMonitorRegistry =
                DefaultConnectionMonitorRegistry.fromConfig(monitoringConfig);
        final ConnectionLoggerRegistry loggerRegistry =
                ConnectionLoggerRegistry.fromConfig(monitoringConfig.logger());
        connectionLogger = loggerRegistry.forConnection(connectionId);

        ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);

        this.loggingEnabledDuration = monitoringConfig.logger().logDuration();
        this.checkLoggingActiveInterval = monitoringConfig.logger().loggingActiveCheckInterval();
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connectionId the connection ID.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param conciergeForwarder proxy of concierge service.
     * @param propsFactory factory of props of client actors for various protocols.
     * @param commandValidator validator for commands that should throw an exception if a command is invalid.
     * @return the Akka configuration Props object.
     */
    public static Props props(final String connectionId,
            final ActorRef pubSubMediator,
            final ActorRef conciergeForwarder,
            final ClientActorPropsFactory propsFactory,
            @Nullable final Consumer<ConnectivityCommand<?>> commandValidator
    ) {

        return Props.create(ConnectionActor.class, connectionId, pubSubMediator, conciergeForwarder, propsFactory,
                commandValidator);
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
        log.info("stopped connection <{}>", connectionId);
        this.loggingDisabled();
        cancelStopSelfIfDeletedTrigger();
        super.postStop();
    }

    @Override
    public Receive createReceiveRecover() {
        return ReceiveBuilder.create()
                // # Snapshot handling
                .match(SnapshotOffer.class, ss -> {
                    final Connection fromSnapshotStore = snapshotAdapter.fromSnapshotStore(ss);
                    log.info("Received SnapshotOffer containing connection: <{}>", fromSnapshotStore);
                    if (fromSnapshotStore != null) {
                        restoreConnection(fromSnapshotStore);
                    }
                    lastSnapshotSequenceNr = confirmedSnapshotSequenceNr = ss.metadata().sequenceNr();
                })
                .match(ConnectionCreated.class, event -> restoreConnection(event.getConnection()))
                .match(ConnectionModified.class, event -> restoreConnection(event.getConnection()))
                .match(ConnectionOpened.class, event -> restoreConnection(connection != null ? connection.toBuilder()
                        .connectionStatus(ConnectivityStatus.OPEN).build() : null))
                .match(ConnectionClosed.class, event -> restoreConnection(connection != null ? connection.toBuilder()
                        .connectionStatus(ConnectivityStatus.CLOSED).build() : null))
                .match(ConnectionDeleted.class, event -> restoreConnection(null))
                .match(RecoveryCompleted.class, rc -> {
                    log.info("Connection <{}> was recovered: {}", connectionId, connection);
                    if (connection != null) {
                        if (ConnectivityStatus.OPEN.equals(connection.getConnectionStatus())) {
                            log.debug("Opening connection <{}> after recovery.", connectionId);

                            final OpenConnection connect = OpenConnection.of(connectionId, DittoHeaders.empty());

                            final ActorRef origin = getSender();
                            askClientActor(connect,
                                    response -> log.info("OpenConnection result: {}", response),
                                    error -> handleException("recovery-connect", origin, error)
                            );
                            subscribeForEvents();
                        }
                        getContext().become(connectionCreatedBehaviour);
                    } else if (lastSequenceNr() > 0) {
                        // if the last sequence number is already > 0 we can assume that the connection was deleted:
                        stopSelfIfDeletedAfterDelay();
                        // otherwise not - as the connection may just be created!
                    }

                    getContext().getParent().tell(ConnectionSupervisorActor.ManualReset.getInstance(), getSelf());
                })
                .matchAny(m -> {
                    if (m == null) {
                        // connection persistence is corrupted.
                        restoreConnection(null);
                        log.warning("Invalid persistence of Connection <{}>", connectionId);
                    } else {
                        log.warning("Unknown recover message: {}", m);
                    }
                })
                .build();
    }

    /**
     * Keep 1 stale event for cleanup if the connection's desired state is open so that this actor's pid stays
     * in the set of current persistence IDs known to the persistence plugin and will be woken up by the reconnect
     * actor after service restart.
     *
     * @return number of stale events to keep after cleanup.
     */
    @Override
    protected long staleEventsKeptAfterCleanup() {
        final boolean isDesiredStateOpen =
                connection != null && connection.getConnectionStatus() == ConnectivityStatus.OPEN;
        return isDesiredStateOpen ? 1 : 0;
    }

    private void restoreConnection(@Nullable final Connection theConnection) {
        connection = theConnection;
        if (theConnection != null) {
            signalFilter = new SignalFilter(theConnection, connectionMonitorRegistry);
        }
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(TestConnection.class, testConnection -> validateAndForward(testConnection, this::testConnection))
                .match(CreateConnection.class,
                        createConnection -> validateAndForward(createConnection, this::createConnection))
                .match(ConnectivityCommand.class, this::handleCommandDuringInitialization)
                .match(Status.Failure.class, f -> log.warning("Got failure in initial behaviour with cause {}: {}",
                        f.cause().getClass().getSimpleName(), f.cause().getMessage()))
                .match(PerformTask.class, this::performTask)
                .matchEquals(STOP_SELF_IF_DELETED, msg -> stopSelf())
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                })
                .build();
    }

    @Override
    protected long getLatestSnapshotSequenceNumber() {
        return confirmedSnapshotSequenceNr;
    }

    private Receive createConnectionCreatedBehaviour() {
        return super.createReceive().orElse(ReceiveBuilder.create()
                .match(TestConnection.class, testConnection ->
                        getSender().tell(TestConnectionResponse.alreadyCreated(testConnection.getConnectionId(),
                                testConnection.getDittoHeaders()), getSelf()))
                .match(CreateConnection.class, createConnection -> {
                    enhanceLogUtil(createConnection);
                    log.info("Connection <{}> already exists! Responding with conflict.", createConnection.getId());
                    final ConnectionConflictException conflictException =
                            ConnectionConflictException.newBuilder(createConnection.getId())
                                    .dittoHeaders(createConnection.getDittoHeaders())
                                    .build();
                    getSender().tell(conflictException, getSelf());
                })
                .match(ModifyConnection.class,
                        modifyConnection -> validateAndForward(modifyConnection, this::modifyConnection))
                .match(OpenConnection.class, this::openConnection)
                .match(CloseConnection.class, this::closeConnection)
                .match(DeleteConnection.class, this::deleteConnection)
                .match(ResetConnectionMetrics.class, this::resetConnectionMetrics)
                .match(EnableConnectionLogs.class, this::enableConnectionLogs)
                .match(RetrieveConnectionLogs.class, this::retrieveConnectionLogs)
                .match(ResetConnectionLogs.class, this::resetConnectionLogs)
                .match(RetrieveConnection.class, this::retrieveConnection)
                .match(RetrieveConnectionStatus.class, this::retrieveConnectionStatus)
                .match(RetrieveConnectionMetrics.class, this::retrieveConnectionMetrics)
                .match(Signal.class, this::handleSignal)
                .match(DistributedPubSubMediator.SubscribeAck.class, this::handleSubscribeAck)
                .match(DistributedPubSubMediator.UnsubscribeAck.class, this::handleUnsubscribeAck)
                .match(SaveSnapshotSuccess.class, this::handleSnapshotSuccess)
                .match(Status.Failure.class, f -> log.warning("Got failure in connectionCreated behaviour with " +
                        "cause {}: {}", f.cause().getClass().getSimpleName(), f.cause().getMessage()))
                .match(PerformTask.class, this::performTask)
                .match(LoggingExpired.class, this::loggingExpired)
                .matchEquals(STOP_SELF_IF_DELETED, msg ->
                        // do nothing; this connection is not deleted.
                        cancelStopSelfIfDeletedTrigger()
                )
                .matchEquals(CheckLoggingActive.INSTANCE, msg -> this.checkLoggingEnabled())
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build());
    }

    private void enhanceLogUtil(final WithDittoHeaders<?> createConnection) {
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, createConnection, connectionId);
    }

    private void performTask(final PerformTask performTask) {
        log.info("Running <{}>", performTask);
        performTask.run(this);
    }

    private void checkLoggingEnabled() {
        final CheckConnectionLogsActive checkLoggingActive = CheckConnectionLogsActive.of(connectionId, Instant.now());
        tellClientActorIfStarted(checkLoggingActive, getSelf());
    }

    private void handleSignal(final Signal<?> signal) {
        // Do not flush pending responses - pub/sub may not be ready on all nodes

        enhanceLogUtil(signal);
        if (clientActorRouter == null) {
            log.debug("Signal dropped: Client actor not ready.");
            return;
        }
        if (connection == null || signalFilter == null) {
            log.debug("Signal dropped: No Connection or signalFilter configuration available.");
            return;
        }
        if (uniqueTopics.isEmpty()) {
            log.debug("Signal dropped: No topics present.");
            return;
        }
        if (connectionId.equals(signal.getDittoHeaders().getOrigin().orElse(null))) {
            log.debug("Signal dropped: was sent by myself.");
            return;
        }

        final List<Target> subscribedAndAuthorizedTargets = signalFilter.filter(signal);
        if (subscribedAndAuthorizedTargets.isEmpty()) {
            log.debug("Signal dropped: No subscribed and authorized targets present");
            return;
        }

        log.debug("Forwarding signal <{}> to client actor with targets: {}.", signal.getType(),
                subscribedAndAuthorizedTargets);

        final OutboundSignal outbound = OutboundSignalFactory.newOutboundSignal(signal, subscribedAndAuthorizedTargets);
        clientActorRouter.tell(outbound, getSender());
    }

    private void testConnection(final TestConnection command) {
        final ActorRef origin = getSender();
        final ActorRef self = getSelf();
        restoreConnection(command.getConnection());

        if (clientActorRouter != null) {
            // client actor is already running, so either another TestConnection command is currently executed or the
            // connection has been created in the meantime. In either case reject the new TestConnection command to
            // prevent strange behavior.
            origin.tell(TestConnectionResponse.alreadyCreated(connectionId, command.getDittoHeaders()), self);
        } else {
            final PerformTask stopSelfTask = new PerformTask("stop self after test", ConnectionActor::stopSelf);
            askClientActor(command, response -> {
                origin.tell(TestConnectionResponse.success(command.getConnectionId(), response.toString(),
                        command.getDittoHeaders()), self);
                // terminate this actor's supervisor after a connection test again:
                self.tell(stopSelfTask, ActorRef.noSender());
            }, error -> {
                handleException("test", origin, error);
                // terminate this actor's supervisor after a connection test again:
                self.tell(stopSelfTask, ActorRef.noSender());
            });
        }
    }

    private <T extends ConnectivityCommand> void validateAndForward(final T command, final Consumer<T> target) {
        final ActorRef origin = getSender();
        try {
            commandValidator.accept(command);
            target.accept(command);
        } catch (final Exception e) {
            handleException(command.getType(), origin, e);
            stopSelf();
        }
    }

    private void createConnection(final CreateConnection command) {
        final ActorRef origin = getSender();
        final ActorRef self = getSelf();
        final ActorRef parent = getContext().getParent();

        persistEvent(ConnectionCreated.of(command.getConnection(), command.getDittoHeaders()), persistedEvent -> {
            restoreConnection(persistedEvent.getConnection());
            getContext().become(connectionCreatedBehaviour);

            if (ConnectivityStatus.OPEN.equals(connection.getConnectionStatus())) {
                log.debug("Connection <{}> has status <{}> and will therefore be opened.",
                        connection.getId(),
                        connection.getConnectionStatus().getName());
                final OpenConnection openConnection = OpenConnection.of(connectionId, command.getDittoHeaders());
                askClientActor(openConnection,
                        response -> {
                            final ConnectivityCommandResponse commandResponse =
                                    CreateConnectionResponse.of(connection, command.getDittoHeaders());
                            final PerformTask performTask =
                                    new PerformTask("subscribe for events and schedule CreateConnectionResponse",
                                            subscribeForEventsAndScheduleResponse(commandResponse, origin));
                            parent.tell(ConnectionSupervisorActor.ManualReset.getInstance(), self);
                            self.tell(performTask, ActorRef.noSender());
                        },
                        error -> {
                            // log error but send response anyway
                            handleException("connect", origin, error, false);
                            respondWithCreateConnectionResponse(connection, command, origin);
                        }
                );
            } else {
                log.debug("Connection <{}> has status <{}> and will therefore stay closed.",
                        connection.getId(),
                        connection.getConnectionStatus().getName());
                respondWithCreateConnectionResponse(connection, command, origin);
            }
        });
    }

    private void respondWithCreateConnectionResponse(final Connection connection, final CreateConnection command,
            final ActorRef origin) {

        origin.tell(CreateConnectionResponse.of(connection, command.getDittoHeaders()), getSelf());
        getContext().getParent().tell(ConnectionSupervisorActor.ManualReset.getInstance(), getSelf());
    }

    private void modifyConnection(final ModifyConnection command) {
        final ActorRef origin = getSender();
        final ActorRef self = getSelf();

        if (connection != null &&
                !connection.getConnectionType().equals(command.getConnection().getConnectionType())) {
            handleException("modify", origin, ConnectionConfigurationInvalidException
                    .newBuilder("ConnectionType <" + connection.getConnectionType().getName() +
                            "> of existing connection <" + connectionId + "> cannot be changed!")
                    .dittoHeaders(command.getDittoHeaders())
                    .build()
            );
            return;
        }

        persistEvent(ConnectionModified.of(command.getConnection(), command.getDittoHeaders()), persistedEvent -> {
            restoreConnection(persistedEvent.getConnection());
            getContext().become(connectionCreatedBehaviour);

            // if client actor is started: send an artificial CloseConnection command to gracefully disconnect and stop the child actors
            askClientActorIfStarted(CloseConnection.of(connectionId, DittoHeaders.empty()),
                    onSuccess -> {
                        final PerformTask modifyTask = createModifyConnectionTask(true, command, origin);
                        self.tell(modifyTask, ActorRef.noSender());
                    },
                    error -> handleException("connect-after-modify", origin, error),
                    () -> {
                        final PerformTask modifyTask = createModifyConnectionTask(false, command, origin);
                        self.tell(modifyTask, ActorRef.noSender());
                    });
        });
    }

    private PerformTask createModifyConnectionTask(final boolean stopClientActor, final ModifyConnection command,
            final ActorRef origin) {
        final String description = (stopClientActor ? "stop client actor and " : "") + "handle modify connection";
        return new PerformTask(description,
                connectionActor -> {
                    if (stopClientActor) {
                        log.debug("Connection {} was modified, stopping client actor.", connectionId);
                        connectionActor.stopClientActor();
                    }
                    connectionActor.handleModifyConnection(command, origin);
                });
    }

    /*
     * NOT thread-safe.
     */
    private void handleModifyConnection(final ModifyConnection command, final ActorRef origin) {
        checkNotNull(connection, "Connection");
        final ActorRef self = getSelf();
        final ActorRef parent = getContext().getParent();

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final ConnectivityCommandResponse commandResponse =
                ModifyConnectionResponse.modified(connectionId, dittoHeaders);

        if (ConnectivityStatus.OPEN.equals(connection.getConnectionStatus())) {
            final OpenConnection openConnectionAfterModification = OpenConnection.of(connectionId, dittoHeaders);
            log.debug("Desired connection state is {}, forwarding {} to client actor.",
                    connection.getConnectionStatus(), openConnectionAfterModification);
            askClientActor(openConnectionAfterModification,
                    response -> {
                        final PerformTask performTask =
                                new PerformTask("subscribe for events and schedule ModifyConnectionResponse",
                                        subscribeForEventsAndScheduleResponse(commandResponse, origin));
                        parent.tell(ConnectionSupervisorActor.ManualReset.getInstance(), self);
                        self.tell(performTask, ActorRef.noSender());
                        this.updateLoggingIfEnabled();
                    },
                    error -> handleException("connect-after-modify", origin, error)
            );
        } else {
            log.debug("Desired connection state is {}, do not open connection.", connection.getConnectionStatus());
            origin.tell(commandResponse, getSelf());
        }
    }

    private void openConnection(final OpenConnection command) {
        checkConnectionNotNull();

        final ConnectionOpened connectionOpened =
                ConnectionOpened.of(command.getConnectionId(), command.getDittoHeaders());
        final ActorRef origin = getSender();
        final ActorRef self = getSelf();

        persistEvent(connectionOpened, persistedEvent -> {
            restoreConnection(connection.toBuilder().connectionStatus(ConnectivityStatus.OPEN).build());
            askClientActor(command, response -> {
                        final ConnectivityCommandResponse commandResponse =
                                OpenConnectionResponse.of(connectionId, command.getDittoHeaders());
                        final PerformTask performTask = new PerformTask("open connection",
                                subscribeForEventsAndScheduleResponse(commandResponse, origin));
                        self.tell(performTask, ActorRef.noSender());
                        this.startEnabledLoggingChecker(Duration.ofMillis(1));
                    },
                    error -> handleException("open-connection", origin, error)
            );
        });
    }

    private void checkConnectionNotNull() {
        checkNotNull(connection, "Connection");
    }

    private void closeConnection(final CloseConnection command) {
        checkConnectionNotNull();

        final ConnectionClosed connectionClosed =
                ConnectionClosed.of(command.getConnectionId(), command.getDittoHeaders());
        final ActorRef origin = getSender();
        final ActorRef self = getSelf();

        persistEvent(connectionClosed, persistedEvent -> {
            if (connection != null) {
                restoreConnection(connection.toBuilder().connectionStatus(ConnectivityStatus.CLOSED).build());
            }
            final CloseConnectionResponse closeConnectionResponse =
                    CloseConnectionResponse.of(connectionId, command.getDittoHeaders());
            askClientActorIfStarted(command,
                    response -> {
                        final PerformTask performTask =
                                new PerformTask(
                                        "unsubscribe from events on connection closed, stop client actor and " +
                                                "schedule response",
                                        connectionActor -> {
                                            connectionActor.unsubscribeFromEvents();
                                            connectionActor.stopClientActor();
                                            origin.tell(closeConnectionResponse, getSelf());
                                        });
                        self.tell(performTask, ActorRef.noSender());
                        this.cancelEnabledLoggingChecker();
                    },
                    error -> handleException("disconnect", origin, error),
                    () -> {
                        log.debug("Client actor was not started, responding directly.");
                        origin.tell(closeConnectionResponse, getSelf());
                    }
            );
        });
    }

    private void deleteConnection(final DeleteConnection command) {
        final ConnectionDeleted connectionDeleted =
                ConnectionDeleted.of(command.getConnectionId(), command.getDittoHeaders());
        final ActorRef origin = getSender();
        final ActorRef self = getSelf();

        persistEvent(connectionDeleted, persistedEvent -> {
            stopClientActor();
            origin.tell(DeleteConnectionResponse.of(connectionId, command.getDittoHeaders()), self);
            stopSelf();
        });
        this.loggingDisabled();
    }

    private void resetConnectionMetrics(final ResetConnectionMetrics command) {
        tellClientActorIfStarted(command, ActorRef.noSender());

        getSender().tell(ResetConnectionMetricsResponse.of(connectionId, command.getDittoHeaders()), getSelf());
    }

    private void enableConnectionLogs(final EnableConnectionLogs command) {
        tellClientActorIfStarted(command, ActorRef.noSender());

        getSender().tell(EnableConnectionLogsResponse.of(connectionId, command.getDittoHeaders()), getSelf());
        this.loggingEnabled();
    }

    private void loggingExpired(final LoggingExpired ccla) {
        log.debug("Cancelling scheduler checking if logging still active for <{}>", ccla.getConnectionId());
        this.loggingDisabled();
    }

    private void retrieveConnectionLogs(final RetrieveConnectionLogs command) {
        this.updateLoggingIfEnabled();
        broadcastCommandWithDifferentSender(command,
                (existingConnection, timeout) -> RetrieveConnectionLogsAggregatorActor.props(
                        existingConnection, getSender(), command.getDittoHeaders(), timeout),
                () -> respondWithEmptyLogs(command, this.getSender()));
    }

    private boolean isLoggingEnabled() {
        return this.loggingEnabledUntil != null && Instant.now().isBefore(this.loggingEnabledUntil);
    }

    private void loggingEnabled() {
        // start check logging scheduler
        this.startEnabledLoggingChecker();
        this.loggingEnabledUntil = Instant.now().plus(this.loggingEnabledDuration);
    }

    private void updateLoggingIfEnabled() {
        if (this.isLoggingEnabled()) {
            this.loggingEnabledUntil = Instant.now().plus(this.loggingEnabledDuration);
            tellClientActorIfStarted(EnableConnectionLogs.of(connectionId, DittoHeaders.empty()), ActorRef.noSender());
        }
    }

    private void loggingDisabled() {
        this.loggingEnabledUntil = null;
        this.cancelEnabledLoggingChecker();
    }

    private void cancelEnabledLoggingChecker() {
        if (this.enabledLoggingChecker != null && !this.enabledLoggingChecker.isCancelled()) {
            this.enabledLoggingChecker.cancel();
        }
    }

    private void startEnabledLoggingChecker() {
        this.startEnabledLoggingChecker(this.checkLoggingActiveInterval);
    }

    private void startEnabledLoggingChecker(final Duration initialDelay) {
        this.cancelEnabledLoggingChecker();
        this.enabledLoggingChecker = getContext().getSystem().scheduler().schedule(
                initialDelay,
                this.checkLoggingActiveInterval,
                getSelf(),
                CheckLoggingActive.INSTANCE,
                getContext().getSystem().dispatcher(),
                null
        );
    }

    private void respondWithEmptyLogs(final RetrieveConnectionLogs command, final ActorRef origin) {
        log.debug("ClientActor not started, responding with empty connection logs.");
        final RetrieveConnectionLogsResponse logsResponse = RetrieveConnectionLogsResponse.of(
                connectionId,
                Collections.emptyList(),
                null,
                null,
                command.getDittoHeaders()
        );
        origin.tell(logsResponse, getSelf());
    }

    private void resetConnectionLogs(final ResetConnectionLogs command) {
        tellClientActorIfStarted(command, ActorRef.noSender());
        getSender().tell(ResetConnectionLogsResponse.of(connectionId, command.getDittoHeaders()), getSelf());
    }

    /*
     * NOT thread-safe.
     */
    private void askClientActor(final Command<?> cmd, final Consumer<Object> onSuccess,
            final Consumer<Throwable> onError) {

        startClientActorIfRequired();
        // timeout before sending the (partial) response
        final long responseTimeout = Optional.ofNullable(cmd.getDittoHeaders().get("timeout"))
                .map(Long::parseLong)
                .orElse(clientActorAskTimeout.toMillis());
        // wrap in Broadcast message because these management messages must be delivered to each client actor
        if (clientActorRouter != null && connection != null) {
            final ActorRef aggregationActor = getContext().actorOf(
                    AggregateActor.props(clientActorRouter, connection.getClientCount(), responseTimeout));
            Patterns.ask(aggregationActor, cmd, clientActorAskTimeout)
                    .whenComplete((response, exception) -> {
                        log.debug("Got response to {}: {}", cmd.getType(),
                                exception == null ? response : exception);
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
        } else {
            final String message =
                    MessageFormat.format(
                            "NOT asking client actor <{0}> for connection <{1}> because one of them is null.",
                            clientActorRouter, connection);
            final NullPointerException nullPointerException = new NullPointerException(message);
            log.error(message);
            onError.accept(nullPointerException);
        }
    }

    private void tellClientActorIfStarted(final Command<?> cmd, final ActorRef sender) {
        if (clientActorRouter != null && connection != null) {
            clientActorRouter.tell(new Broadcast(cmd), sender);
        }
    }

    /*
     * NOT thread-safe.
     */
    private void askClientActorIfStarted(final Command<?> cmd, final Consumer<Object> onSuccess,
            final Consumer<Throwable> onError, final Runnable onClientActorNotStarted) {
        if (clientActorRouter != null && connection != null) {
            askClientActor(cmd, onSuccess, onError);
        } else {
            onClientActorNotStarted.run();
        }
    }

    private void broadcastCommandWithDifferentSender(final ConnectivityQueryCommand<?> command,
            final BiFunction<Connection, Duration, Props> senderPropsForConnectionWithTimeout,
            final Runnable onClientActorNotStarted) {
        if (clientActorRouter != null && connection != null) {
            // timeout before sending the (partial) response
            final Duration timeout =
                    Duration.ofMillis((long) (extractTimeoutFromCommand(command.getDittoHeaders()) * 0.75));
            final ActorRef aggregator =
                    getContext().actorOf(senderPropsForConnectionWithTimeout.apply(connection, timeout));

            // forward command to all client actors with aggregator as sender
            clientActorRouter.tell(new Broadcast(command), aggregator);
        } else {
            onClientActorNotStarted.run();
        }
    }

    private void forwardToClientActors(final Props aggregatorProps, final Command<?> cmd,
            final Runnable onClientActorNotStarted) {
        if (clientActorRouter != null && connection != null) {
            final ActorRef metricsAggregator = getContext().actorOf(aggregatorProps);

            // forward command to all client actors with aggregator as sender
            clientActorRouter.tell(new Broadcast(cmd), metricsAggregator);
        } else {
            onClientActorNotStarted.run();
        }
    }

    /*
     * Thread-safe because Actor.getSelf() is thread-safe.
     */
    private void handleException(final String action, final ActorRef origin, final Throwable exception) {
        handleException(action, origin, exception, true);
    }

    private void handleException(final String action,
            final ActorRef origin,
            final Throwable exception,
            final boolean sendExceptionResponse) {

        final DittoRuntimeException dre;
        if (exception instanceof DittoRuntimeException) {
            dre = (DittoRuntimeException) exception;
        } else {
            dre = ConnectionFailedException.newBuilder(connectionId)
                    .description(exception.getMessage())
                    .cause(exception)
                    .build();
        }

        if (sendExceptionResponse) {
            origin.tell(dre, getSelf());
        }
        connectionLogger.failure("Operation {0} failed due to {1}", action, dre.getMessage());
        log.warning("Operation <{}> on connection <{}> failed due to {}: {}.", action, connectionId,
                dre.getClass().getSimpleName(), dre.getMessage());
    }

    private void retrieveConnection(final RetrieveConnection command) {
        checkConnectionNotNull();
        getSender().tell(RetrieveConnectionResponse.of(connection, command.getDittoHeaders()), getSelf());
    }

    private void retrieveConnectionStatus(final RetrieveConnectionStatus command) {
        checkNotNull(connection, "Connection");
        // timeout before sending the (partial) response
        final Duration timeout =
                Duration.ofMillis((long) (extractTimeoutFromCommand(command.getDittoHeaders()) * 0.75));
        final Props props = RetrieveConnectionStatusAggregatorActor.props(connection, getSender(),
                command.getDittoHeaders(), timeout);
        forwardToClientActors(props, command, () -> respondWithEmptyStatus(command, this.getSender()));
    }

    private static long extractTimeoutFromCommand(final DittoHeaders headers) {
        return Optional.ofNullable(headers.get("timeout"))
                .map(Long::parseLong)
                .orElse(DEFAULT_RETRIEVE_STATUS_TIMEOUT);
    }

    private void retrieveConnectionMetrics(final RetrieveConnectionMetrics command) {
        broadcastCommandWithDifferentSender(command,
                (existingConnection, timeout) -> RetrieveConnectionMetricsAggregatorActor.props(
                        existingConnection, getSender(), command.getDittoHeaders(), timeout),
                () -> respondWithEmptyMetrics(command, this.getSender()));
    }

    private void respondWithEmptyMetrics(final RetrieveConnectionMetrics command, final ActorRef origin) {
        log.debug("ClientActor not started, responding with empty connection metrics with status closed.");
        final ConnectionMetrics metrics =
                ConnectivityModelFactory.newConnectionMetrics(
                        ConnectivityModelFactory.newAddressMetric(Collections.emptySet()),
                        ConnectivityModelFactory.newAddressMetric(Collections.emptySet())
                );
        final RetrieveConnectionMetricsResponse metricsResponse =
                RetrieveConnectionMetricsResponse.of(connectionId, metrics,
                        ConnectivityModelFactory.emptySourceMetrics(),
                        ConnectivityModelFactory.emptyTargetMetrics(),
                        command.getDittoHeaders());
        origin.tell(metricsResponse, getSelf());
    }

    private void respondWithEmptyStatus(final RetrieveConnectionStatus command, final ActorRef origin) {
        log.debug("ClientActor not started, responding with empty connection status with status closed.");
        final RetrieveConnectionStatusResponse statusResponse =
                RetrieveConnectionStatusResponse.closedResponse(connectionId,
                        InstanceIdentifierSupplier.getInstance().get(),
                        connectionClosedAt == null ? Instant.EPOCH : connectionClosedAt,
                        ConnectivityStatus.CLOSED,
                        "[" + BaseClientState.DISCONNECTED + "] connection is closed",
                        command.getDittoHeaders());
        origin.tell(statusResponse, getSelf());
    }

    private void subscribeForEvents() {
        checkConnectionNotNull();

        // unsubscribe to previously subscribed topics
        unsubscribeFromEvents();

        uniqueTopics = connection.getTargets().stream()
                .flatMap(target -> target.getTopics().stream().map(FilteredTopic::getTopic))
                .collect(Collectors.toSet());

        forEachPubSubTopicDo(pubSubTopic -> {
            final DistributedPubSubMediator.Subscribe subscribe =
                    new DistributedPubSubMediator.Subscribe(pubSubTopic, PUB_SUB_GROUP_PREFIX + connectionId,
                            getSelf());
            log.debug("Subscribing to pub-sub topic <{}> for connection <{}>.", pubSubTopic, connectionId);
            pubSubMediator.tell(subscribe, getSelf());
        });
    }

    private void unsubscribeFromEvents() {
        forEachPubSubTopicDo(pubSubTopic -> {
            log.debug("Unsubscribing from pub-sub topic <{}> for connection <{}>.", pubSubTopic, connectionId);
            final DistributedPubSubMediator.Unsubscribe unsubscribe =
                    new DistributedPubSubMediator.Unsubscribe(pubSubTopic, PUB_SUB_GROUP_PREFIX + connectionId,
                            getSelf());
            pubSubMediator.tell(unsubscribe, getSelf());
        });
    }

    private void forEachPubSubTopicDo(final Consumer<String> topicConsumer) {
        uniqueTopics.stream()
                .map(Topic::getPubSubTopic)
                .forEach(topicConsumer);
    }

    private void handleCommandDuringInitialization(final ConnectivityCommand command) {
        log.debug("Unexpected command during initialization of actor received: {} - "
                        + "Terminating this actor and sending 'ConnectionNotAccessibleException' to requester ...",
                command.getType());
        getSender().tell(ConnectionNotAccessibleException.newBuilder(command.getId())
                .dittoHeaders(command.getDittoHeaders())
                .build(), getSelf());
    }

    private <E extends Event> void persistEvent(final E event, final Consumer<E> consumer) {
        persist(event, persistedEvent -> {
            log.debug("Successfully persisted Event <{}>.", persistedEvent.getType());
            consumer.accept(persistedEvent);
            pubSubMediator.tell(new DistributedPubSubMediator.Publish(event.getType(), event, true), getSelf());

            // save a snapshot if there were too many changes since the last snapshot
            if ((lastSequenceNr() - lastSnapshotSequenceNr) >= snapshotThreshold) {
                doSaveSnapshot();
            }
        });
    }

    private void doSaveSnapshot() {
        if (connection != null) {
            log.info("Attempting to save Snapshot for Connection: <{}> ...", connection);
            // save a snapshot
            final Object snapshotToStore = snapshotAdapter.toSnapshotStore(connection);
            saveSnapshot(snapshotToStore);
            lastSnapshotSequenceNr = lastSequenceNr();
        } else {
            log.warning("Connection and MappingContext must not be null when taking snapshot.");
        }
    }

    private void startClientActorIfRequired() {
        checkNotNull(connectionId, "connectionId");
        checkConnectionNotNull();
        if (clientActorRouter == null) {
            final int clientCount = connection.getClientCount();
            log.info("Starting ClientActor for connection <{}> with <{}> clients.", connectionId, clientCount);
            final Props props = propsFactory.getActorPropsForType(connection, conciergeForwarder);
            final ClusterRouterPoolSettings clusterRouterPoolSettings =
                    new ClusterRouterPoolSettings(clientCount, 1, true,
                            Collections.singleton(CLUSTER_ROLE));
            final RoundRobinPool roundRobinPool = new RoundRobinPool(clientCount);
            final Props clusterRouterPoolProps =
                    new ClusterRouterPool(roundRobinPool, clusterRouterPoolSettings).props(props);

            // start client actor without name so it does not conflict with its previous incarnation
            clientActorRouter = getContext().actorOf(clusterRouterPoolProps);
        } else {
            log.debug("ClientActor already started.");
        }
    }

    private void stopClientActor() {
        if (clientActorRouter != null) {
            connectionClosedAt = Instant.now();
            log.debug("Stopping the client actor.");
            stopChildActor(clientActorRouter);
            clientActorRouter = null;
        }
    }

    private void stopChildActor(final ActorRef actor) {
        log.debug("Stopping child actor <{}>.", actor.path());
        getContext().stop(actor);
    }

    private void stopSelf() {
        log.info("Passivating / shutting down");
        final ShardRegion.Passivate passivateMessage = new ShardRegion.Passivate(PoisonPill.getInstance());
        getContext().getParent().tell(passivateMessage, getSelf());
    }

    private void stopSelfIfDeletedAfterDelay() {
        final ExecutionContextExecutor dispatcher = getContext().dispatcher();
        cancelStopSelfIfDeletedTrigger();
        stopSelfIfDeletedTrigger = getContext().system()
                .scheduler()
                .scheduleOnce(DELETED_ACTOR_LIFETIME, getSelf(), STOP_SELF_IF_DELETED, dispatcher, ActorRef.noSender());
    }

    private void cancelStopSelfIfDeletedTrigger() {
        if (stopSelfIfDeletedTrigger != null) {
            stopSelfIfDeletedTrigger.cancel();
            stopSelfIfDeletedTrigger = null;
        }
    }

    private void handleSubscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
        log.debug("Successfully subscribed to distributed pub/sub on topic <{}>.",
                subscribeAck.subscribe().topic());
    }

    private void handleUnsubscribeAck(final DistributedPubSubMediator.UnsubscribeAck unsubscribeAck) {
        log.debug("Successfully unsubscribed from distributed pub/sub on topic <{}>.",
                unsubscribeAck.unsubscribe().topic());
    }

    private void handleSnapshotSuccess(final SaveSnapshotSuccess sss) {
        log.debug("Snapshot was saved successfully: {}", sss);
        confirmedSnapshotSequenceNr = Math.max(confirmedSnapshotSequenceNr, sss.metadata().sequenceNr());
    }

    private void schedulePendingResponse(final ConnectivityCommandResponse response, final ActorRef sender) {
        getContext().system().scheduler()
                .scheduleOnce(flushPendingResponsesTimeout,
                        sender,
                        response,
                        getContext().dispatcher(),
                        getSelf());
    }

    private static Consumer<ConnectionActor> subscribeForEventsAndScheduleResponse(
            final ConnectivityCommandResponse response,
            final ActorRef sender) {

        return connectionActor -> {
            connectionActor.subscribeForEvents();
            connectionActor.schedulePendingResponse(response, sender);
        };
    }

    /**
     * Message that will be sent by scheduler and indicates a check if logging is still enabled for this connection.
     */
    static final class CheckLoggingActive {

        static final CheckLoggingActive INSTANCE = new CheckLoggingActive();

        private CheckLoggingActive() {
        }

    }

    /**
     * Self-message for future tasks to run synchronously in actor's thread. Minimal wrapping of thread-unsafe
     * operations so that they do not corrupt actor state. The results of such operations are not guaranteed to make
     * sense.
     */
    private static final class PerformTask {

        private final String description;
        private final Consumer<ConnectionActor> task;

        private PerformTask(final String description, final Consumer<ConnectionActor> task) {
            this.description = description;
            this.task = task;
        }

        private void run(final ConnectionActor thisActor) {
            task.accept(thisActor);
        }

        @Override
        public final String toString() {
            return String.format("PerformTask(%s)", description);
        }

    }

    /**
     * Local helper-actor which is started for aggregating several Status sent back by potentially several {@code
     * clientActors} (behind a cluster Router running on different cluster nodes).
     */
    private static final class AggregateActor extends AbstractActor {

        private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

        private final Map<String, Status.Status> aggregatedStatus;

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
        static Props props(final ActorRef clientActor, final int expectedResponses, final long timeout) {
            return Props.create(AggregateActor.class, clientActor, expectedResponses, timeout);
        }

        @SuppressWarnings("unused")
        private AggregateActor(final ActorRef clientActor, final int expectedResponses, final long timeout) {
            this.clientActor = clientActor;
            this.expectedResponses = expectedResponses;
            this.timeout = timeout;
            aggregatedStatus = new HashMap<>();
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(Command.class, command -> {
                        clientActor.tell(new Broadcast(command), getSelf());
                        originHeaders = command.getDittoHeaders();
                        origin = getSender();

                        getContext().setReceiveTimeout(Duration.ofMillis(timeout / 2));
                    })
                    .match(ReceiveTimeout.class, receiveTimeout ->
                            // send back (partially) gathered responses
                            sendBackAggregatedResults()
                    )
                    .matchAny(any -> {
                        if (any instanceof Status.Status) {
                            aggregatedStatus.put(getSender().path().address().hostPort(),
                                    (Status.Status) any);
                        } else {
                            log.error("Could not handle non-Status response: {}", any);
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
            if (origin != null && originHeaders != null && !aggregatedStatus.isEmpty()) {
                log.debug("Aggregated statuses: {}", aggregatedStatus);
                final Optional<Status.Status> failure = aggregatedStatus.entrySet().stream()
                        .filter(s -> s.getValue() instanceof Status.Failure)
                        .map(Map.Entry::getValue)
                        .findFirst();
                if (failure.isPresent()) {
                    origin.tell(failure.get(), getSelf());
                } else {
                    final String aggregatedStatusStr = aggregatedStatus.entrySet().stream()
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
