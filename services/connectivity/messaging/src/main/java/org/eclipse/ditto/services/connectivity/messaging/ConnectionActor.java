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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionLifecycle;
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
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.services.connectivity.messaging.rabbitmq.RabbitMQValidator;
import org.eclipse.ditto.services.connectivity.messaging.validation.CompoundConnectivityCommandInterceptor;
import org.eclipse.ditto.services.connectivity.messaging.validation.ConnectionValidator;
import org.eclipse.ditto.services.connectivity.messaging.validation.DittoConnectivityCommandValidator;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.services.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.services.utils.persistentactors.AbstractShardedPersistenceActor;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.commands.DefaultContext;
import org.eclipse.ditto.services.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.modify.EnableConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.ConnectivityQueryCommand;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatusResponse;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.Status;
import akka.cluster.routing.ClusterRouterPool;
import akka.cluster.routing.ClusterRouterPoolSettings;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.routing.Broadcast;
import akka.routing.RoundRobinPool;

/**
 * Handles {@code *Connection} commands and manages the persistence of connection. The actual connection handling to the
 * remote server is delegated to a child actor that uses a specific client (AMQP 1.0 or 0.9.1).
 */
public final class ConnectionActor
        extends AbstractShardedPersistenceActor<Signal, Connection, ConnectionId, ConnectionState, ConnectivityEvent> {

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

    private static final long DEFAULT_RETRIEVE_STATUS_TIMEOUT = 500L;

    /**
     * Validator of all supported connections.
     */
    private static final ConnectionValidator CONNECTION_VALIDATOR = ConnectionValidator.of(
            RabbitMQValidator.newInstance(),
            AmqpValidator.newInstance(),
            MqttValidator.newInstance(),
            KafkaValidator.getInstance());

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final DittoProtocolSub dittoProtocolSub;
    private final ActorRef conciergeForwarder;
    private final ClientActorPropsFactory propsFactory;
    private final Consumer<ConnectivityCommand<?>> commandValidator;
    private final ConnectionLogger connectionLogger;
    private Instant connectionClosedAt = Instant.now();

    @Nullable private ActorRef clientActorRouter;
    @Nullable private SignalFilter signalFilter = null;

    private final Duration clientActorAskTimeout;
    private final ConnectionMonitorRegistry<ConnectionMonitor> connectionMonitorRegistry;

    private final Duration checkLoggingActiveInterval;

    @Nullable private Instant loggingEnabledUntil;
    private final Duration loggingEnabledDuration;
    private final ConnectionConfig config;

    @SuppressWarnings("unused")
    private ConnectionActor(final ConnectionId connectionId,
            final DittoProtocolSub dittoProtocolSub,
            final ActorRef conciergeForwarder,
            final ClientActorPropsFactory propsFactory,
            @Nullable final Consumer<ConnectivityCommand<?>> customCommandValidator) {

        super(connectionId, new ConnectionMongoSnapshotAdapter());

        this.dittoProtocolSub = dittoProtocolSub;
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
        config = connectivityConfig.getConnectionConfig();

        clientActorAskTimeout = config.getClientActorAskTimeout();

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
     * @param dittoProtocolSub Ditto protocol sub access.
     * @param conciergeForwarder proxy of concierge service.
     * @param propsFactory factory of props of client actors for various protocols.
     * @param commandValidator validator for commands that should throw an exception if a command is invalid.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ConnectionId connectionId,
            final DittoProtocolSub dittoProtocolSub,
            final ActorRef conciergeForwarder,
            final ClientActorPropsFactory propsFactory,
            @Nullable final Consumer<ConnectivityCommand<?>> commandValidator
    ) {
        return Props.create(ConnectionActor.class, connectionId, dittoProtocolSub, conciergeForwarder, propsFactory,
                commandValidator);
    }

    @Override
    public String persistenceId() {
        return PERSISTENCE_ID_PREFIX + entityId;
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
    protected Class<ConnectivityEvent> getEventClass() {
        return ConnectivityEvent.class;
    }

    @Override
    protected CommandStrategy.Context<ConnectionState> getStrategyContext() {
        return DefaultContext.getInstance(ConnectionState.of(entityId, connectionLogger, commandValidator), log);
    }

    @Override
    protected ConnectionCreatedStrategies getCreatedStrategy() {
        return ConnectionCreatedStrategies.getInstance();
    }

    @Override
    protected ConnectionDeletedStrategies getDeletedStrategy() {
        return ConnectionDeletedStrategies.getInstance();
    }

    @Override
    protected EventStrategy<ConnectivityEvent, Connection> getEventStrategy() {
        return ConnectionEventStrategies.getInstance();
    }

    @Override
    protected ActivityCheckConfig getActivityCheckConfig() {
        return config.getActivityCheckConfig();
    }

    @Override
    protected org.eclipse.ditto.services.utils.persistence.mongo.config.SnapshotConfig getSnapshotConfig() {
        return config.getSnapshotConfig();
    }

    @Override
    protected boolean entityExistsAsDeleted() {
        return entity != null &&
                entity.getLifecycle().orElse(ConnectionLifecycle.ACTIVE) == ConnectionLifecycle.DELETED;
    }

    @Override
    protected DittoRuntimeExceptionBuilder newNotAccessibleExceptionBuilder() {
        return ConnectionNotAccessibleException.newBuilder(entityId);
    }

    @Override
    protected void publishEvent(final ConnectivityEvent event) {
        // Do nothing because nobody subscribes for connectivity events.
    }

    @Override
    protected JsonSchemaVersion getEntitySchemaVersion(final Connection entity) {
        return entity.getImplementedSchemaVersion();
    }

    @Override
    public void postStop() {
        log.info("stopped connection <{}>", entityId);
        this.loggingDisabled();
        super.postStop();
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
        return isDesiredStateOpen() ? 1 : 0;
    }

    @Override
    public void onMutation(final Command command, final ConnectivityEvent event, final WithDittoHeaders response,
            final boolean becomeCreated, final boolean becomeDeleted) {
        if (command instanceof StagedCommand) {
            interpretStagedCommand((StagedCommand) command);
        } else {
            super.onMutation(command, event, response, becomeCreated, becomeDeleted);
        }
    }

    @Override
    protected void checkForActivity(final CheckForActivity trigger) {
        if (isDesiredStateOpen()) {
            // stay in memory forever if desired state is open. check again later in case connection becomes closed.
            scheduleCheckForActivity(getActivityCheckConfig().getInactiveInterval());
        } else {
            super.checkForActivity(trigger);
        }
    }

    private void interpretStagedCommand(final StagedCommand command) {
        if (!command.hasNext()) {
            // execution complete
            return;
        }
        switch (command.nextAction()) {
            case TEST_CONNECTION:
                // TODO
            default:
        }
    }

    private void restoreConnection(@Nullable final Connection theConnection) {
        entity = theConnection;
        if (theConnection != null) {
            signalFilter = new SignalFilter(theConnection, connectionMonitorRegistry);
        }
    }

    private void enhanceLogUtil(final WithDittoHeaders<?> createConnection) {
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, createConnection, entityId);
    }


    private void logDroppedSignal(final String type, final String reason) {
        log.debug("Signal ({}) dropped: {}", type, reason);
    }


    private Connection setLifecycleActive(final Connection connection) {
        if (connection.hasLifecycle(ConnectionLifecycle.ACTIVE)) {
            return connection;
        } else {
            return connection.toBuilder().lifecycle(ConnectionLifecycle.ACTIVE).build();
        }
    }

    private void respondWithCreateConnectionResponse(final Connection connection, final CreateConnection command,
            final ActorRef origin) {

        origin.tell(CreateConnectionResponse.of(connection, command.getDittoHeaders()), getSelf());
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
            tellClientActorsIfStarted(EnableConnectionLogs.of(entityId, DittoHeaders.empty()), ActorRef.noSender());
        }
    }

    private void loggingDisabled() {
        this.loggingEnabledUntil = null;
        this.cancelEnabledLoggingChecker();
    }

    private void cancelEnabledLoggingChecker() {
        timers().cancel(CheckLoggingActive.INSTANCE);
    }

    private void startEnabledLoggingChecker() {
        this.startEnabledLoggingChecker(this.checkLoggingActiveInterval);
    }

    private void startEnabledLoggingChecker(final Duration initialDelay) {
        timers().startPeriodicTimer(CheckLoggingActive.INSTANCE, CheckLoggingActive.INSTANCE,
                checkLoggingActiveInterval);
    }

    private void respondWithEmptyLogs(final RetrieveConnectionLogs command, final ActorRef origin) {
        log.debug("ClientActor not started, responding with empty connection logs.");
        final RetrieveConnectionLogsResponse logsResponse = RetrieveConnectionLogsResponse.of(
                entityId,
                Collections.emptyList(),
                null,
                null,
                command.getDittoHeaders()
        );
        origin.tell(logsResponse, getSelf());
    }

    private void resetConnectionLogs(final ResetConnectionLogs command) {
        tellClientActorsIfStarted(command, ActorRef.noSender());
        getSender().tell(ResetConnectionLogsResponse.of(entityId, command.getDittoHeaders()), getSelf());
    }

    /*
     * NOT thread-safe.
     */
    private CompletionStage<Object> askClientActor(final Command<?> cmd) {

        startClientActorIfRequired();
        // timeout before sending the (partial) response
        final long responseTimeout = Optional.ofNullable(cmd.getDittoHeaders().get("timeout"))
                .map(Long::parseLong)
                .orElse(clientActorAskTimeout.toMillis());
        // wrap in Broadcast message because these management messages must be delivered to each client actor
        if (clientActorRouter != null && entity != null) {
            final ActorRef aggregationActor = getContext().actorOf(
                    AggregateActor.props(clientActorRouter, entity.getClientCount(), responseTimeout,
                            entityId));
            return Patterns.ask(aggregationActor, cmd, clientActorAskTimeout)
                    .thenCompose(response -> {
                        if (response instanceof Status.Failure) {
                            return failedFuture(((Status.Failure) response).cause());
                        } else if (response instanceof DittoRuntimeException) {
                            return failedFuture((DittoRuntimeException) response);
                        } else {
                            return CompletableFuture.completedFuture(response);
                        }
                    });
        } else {
            final String message =
                    MessageFormat.format(
                            "NOT asking client actor <{0}> for connection <{1}> because one of them is null.",
                            clientActorRouter, entity);
            final NullPointerException nullPointerException = new NullPointerException(message);
            log.error(message);
            return failedFuture(nullPointerException);
        }
    }

    private void tellClientActorsIfStarted(final Command<?> cmd, final ActorRef sender) {
        if (clientActorRouter != null && entity != null) {
            clientActorRouter.tell(new Broadcast(cmd), sender);
        }
    }

    /*
     * NOT thread-safe.
     */
    private void askClientActorIfStarted(final Command<?> cmd, final Consumer<Object> onSuccess,
            final Consumer<Throwable> onError, final Runnable onClientActorNotStarted) {
        if (clientActorRouter != null && entity != null) {
            askClientActor(cmd).thenAccept(onSuccess).exceptionally(e -> {
                onError.accept(e);
                return null;
            });
        } else {
            onClientActorNotStarted.run();
        }
    }

    private void broadcastCommandWithDifferentSender(final ConnectivityQueryCommand<?> command,
            final BiFunction<Connection, Duration, Props> senderPropsForConnectionWithTimeout,
            final Runnable onClientActorNotStarted) {
        if (clientActorRouter != null && entity != null) {
            // timeout before sending the (partial) response
            final Duration timeout =
                    Duration.ofMillis((long) (extractTimeoutFromCommand(command.getDittoHeaders()) * 0.75));
            final ActorRef aggregator =
                    getContext().actorOf(senderPropsForConnectionWithTimeout.apply(entity, timeout));

            // forward command to all client actors with aggregator as sender
            clientActorRouter.tell(new Broadcast(command), aggregator);
        } else {
            onClientActorNotStarted.run();
        }
    }

    private void forwardToClientActors(final Props aggregatorProps, final Command<?> cmd,
            final Runnable onClientActorNotStarted) {
        if (clientActorRouter != null && entity != null) {
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
    private Void handleException(final String action, final ActorRef origin, final Throwable exception) {
        handleException(action, origin, exception, true);
        return null;
    }

    private Void handleException(final String action,
            final ActorRef origin,
            final Throwable error,
            final boolean sendExceptionResponse) {

        final Throwable cause = getRootCause(error);
        final DittoRuntimeException dre;
        if (cause instanceof DittoRuntimeException) {
            dre = (DittoRuntimeException) cause;
        } else {
            dre = ConnectionFailedException.newBuilder(entityId)
                    .description(cause.getMessage())
                    .cause(cause)
                    .build();
        }

        if (sendExceptionResponse) {
            origin.tell(dre, getSelf());
        }
        connectionLogger.failure("Operation {0} failed due to {1}", action, dre.getMessage());
        log.warning("Operation <{}> on connection <{}> failed due to {}: {}.", action, entityId,
                dre.getClass().getSimpleName(), dre.getMessage());
        return null;
    }


    private void retrieveConnectionStatus(final RetrieveConnectionStatus command) {
        checkNotNull(entity, "Connection");
        // timeout before sending the (partial) response
        final Duration timeout =
                Duration.ofMillis((long) (extractTimeoutFromCommand(command.getDittoHeaders()) * 0.75));
        final Props props = RetrieveConnectionStatusAggregatorActor.props(entity, getSender(),
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
                RetrieveConnectionMetricsResponse.getBuilder(entityId, command.getDittoHeaders())
                        .connectionMetrics(metrics)
                        .sourceMetrics(ConnectivityModelFactory.emptySourceMetrics())
                        .targetMetrics(ConnectivityModelFactory.emptyTargetMetrics())
                        .build();
        origin.tell(metricsResponse, getSelf());
    }

    private void respondWithEmptyStatus(final RetrieveConnectionStatus command, final ActorRef origin) {
        log.debug("ClientActor not started, responding with empty connection status with status closed.");

        final RetrieveConnectionStatusResponse statusResponse =
                RetrieveConnectionStatusResponse.closedResponse(entityId,
                        InstanceIdentifierSupplier.getInstance().get(),
                        connectionClosedAt == null ? Instant.EPOCH : connectionClosedAt,
                        ConnectivityStatus.CLOSED,
                        "[" + BaseClientState.DISCONNECTED + "] connection is closed",
                        command.getDittoHeaders());
        origin.tell(statusResponse, getSelf());
    }

    private Set<Topic> getUniqueTopics(@Nullable final Connection theConnection) {
        return theConnection != null ? theConnection.getTargets().stream()
                .flatMap(target -> target.getTopics().stream().map(FilteredTopic::getTopic))
                .collect(Collectors.toSet()) : Collections.emptySet();
    }

    private Set<String> getTargetAuthSubjects() {
        if (entity == null || entity.getTargets().isEmpty()) {
            return Collections.emptySet();
        } else {
            return entity.getTargets()
                    .stream()
                    .map(Target::getAuthorizationContext)
                    .map(AuthorizationContext::getAuthorizationSubjectIds)
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());
        }
    }

    private void startClientActorIfRequired() {
        if (entity != null && clientActorRouter == null) {
            final int clientCount = entity.getClientCount();
            log.info("Starting ClientActor for connection <{}> with <{}> clients.", entityId, clientCount);
            final Props props = propsFactory.getActorPropsForType(entity, conciergeForwarder);
            final ClusterRouterPoolSettings clusterRouterPoolSettings =
                    new ClusterRouterPoolSettings(clientCount, 1, true,
                            Collections.singleton(CLUSTER_ROLE));
            final RoundRobinPool roundRobinPool = new RoundRobinPool(clientCount);
            final Props clusterRouterPoolProps =
                    new ClusterRouterPool(roundRobinPool, clusterRouterPoolSettings).props(props);

            // start client actor without name so it does not conflict with its previous incarnation
            clientActorRouter = getContext().actorOf(clusterRouterPoolProps);
        } else if (clientActorRouter != null) {
            log.debug("ClientActor already started.");
        } else {
            log.error(new IllegalStateException(), "Trying to start client actor without a connection");
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

    private boolean isDeleted(@Nullable final Connection connection) {
        return connection == null || connection.hasLifecycle(ConnectionLifecycle.DELETED);
    }

    private boolean isDesiredStateOpen() {
        return entity != null &&
                !entity.hasLifecycle(ConnectionLifecycle.DELETED) &&
                entity.getConnectionStatus() == ConnectivityStatus.OPEN;
    }

    private static Collection<StreamingType> toStreamingTypes(final Set<Topic> uniqueTopics) {
        return uniqueTopics.stream()
                .map(topic -> {
                    switch (topic) {
                        case LIVE_EVENTS:
                            return StreamingType.LIVE_EVENTS;
                        case LIVE_COMMANDS:
                            return StreamingType.LIVE_COMMANDS;
                        case LIVE_MESSAGES:
                            return StreamingType.MESSAGES;
                        case TWIN_EVENTS:
                        default:
                            return StreamingType.EVENTS;
                    }
                })
                .collect(Collectors.toList());
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

        /**
         * Creates Akka configuration object for this actor.
         *
         * @param clientActor the client actor router
         * @param expectedResponses the number of expected responses
         * @param timeout the timeout in milliseconds
         * @param connectionId the connection id
         * @return the Akka configuration Props object
         */
        static Props props(final ActorRef clientActor, final int expectedResponses, final long timeout,
                final ConnectionId connectionId) {
            return Props.create(AggregateActor.class, clientActor, expectedResponses, timeout, connectionId);
        }

        @SuppressWarnings("unused")
        private AggregateActor(final ActorRef clientActor, final int expectedResponses, final long timeout,
                final ConnectionId connectionId) {
            this.clientActor = clientActor;
            this.expectedResponses = expectedResponses;
            this.timeout = timeout;
            aggregatedStatus = new HashMap<>();
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(Command.class, command -> {
                        clientActor.tell(new Broadcast(command), getSelf());
                        origin = getSender();

                        getContext().setReceiveTimeout(Duration.ofMillis(timeout / 2));
                    })
                    .match(ReceiveTimeout.class, receiveTimeout ->
                            // send back (partially) gathered responses
                            sendBackAggregatedResults()
                    )
                    .match(Status.Status.class, status -> {
                        addStatus(status);
                        respondIfExpectedResponsesReceived();
                    })
                    .match(DittoRuntimeException.class, dre -> {
                        addStatus(new Status.Failure(dre));
                        respondIfExpectedResponsesReceived();
                    })
                    .matchAny(any -> {
                        log.info("Could not handle non-Status response: {}", any);
                        respondIfExpectedResponsesReceived();
                    })
                    .build();
        }

        private void addStatus(final Status.Status status) {
            aggregatedStatus.put(getSender().path().address().hostPort(), status);
        }

        private void sendBackAggregatedResults() {
            if (origin != null) {
                if (!aggregatedStatus.isEmpty()) {
                    log.debug("Aggregated statuses: {}", aggregatedStatus);
                    final Optional<Status.Status> failure = aggregatedStatus.values().stream()
                            .filter(status -> status instanceof Status.Failure)
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
                    // no status response received, most likely a timeout
                    log.info("Waiting for status responses timed out.");
                    origin.tell(new RuntimeException("Waiting for status responses timed out."), getSelf());
                }
            } else {
                log.info("No origin was present to send back aggregated results.");
            }
            getContext().stop(getSelf());
        }

        private void respondIfExpectedResponsesReceived() {
            responseCount++;
            if (expectedResponses == responseCount) {
                // send back all gathered responses
                sendBackAggregatedResults();
            }
        }
    }


    private static <T> CompletionStage<T> failedFuture(final Throwable cause) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }

    private static Throwable getRootCause(final Throwable error) {
        return error instanceof CompletionException ? getRootCause(error.getCause()) : error;
    }

    static Optional<DittoRuntimeException> validate(final CommandStrategy.Context<ConnectionState> context,
            final ConnectivityCommand command) {

        try {
            context.getState().getValidator().accept(command);
            return Optional.empty();
        } catch (final Exception error) {
            final Throwable cause = getRootCause(error);
            final DittoRuntimeException dre;
            if (cause instanceof DittoRuntimeException) {
                dre = (DittoRuntimeException) cause;
            } else {
                dre = ConnectionFailedException.newBuilder(context.getState().id())
                        .description(cause.getMessage())
                        .cause(cause)
                        .build();
            }
            context.getLog().info("Operation <{}> failed due to <{}>", command, dre);
            context.getState()
                    .getConnectionLogger()
                    .failure("Operation {0} failed due to {1}", command.getType(), dre.getMessage());
            return Optional.of(dre);
        }
    }
}
