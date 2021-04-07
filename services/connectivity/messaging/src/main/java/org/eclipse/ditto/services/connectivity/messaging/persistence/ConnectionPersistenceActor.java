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
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionAction.UPDATE_SUBSCRIPTIONS;
import static org.eclipse.ditto.services.models.connectivity.ConnectivityMessagingConstants.CLUSTER_ROLE;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonValue;
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
import org.eclipse.ditto.services.connectivity.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.config.ConnectivityConfigProvider;
import org.eclipse.ditto.services.connectivity.config.ConnectivityConfigProviderFactory;
import org.eclipse.ditto.services.connectivity.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.messaging.ClientActorPropsFactory;
import org.eclipse.ditto.services.connectivity.messaging.ClientActorRefs;
import org.eclipse.ditto.services.connectivity.messaging.amqp.AmqpValidator;
import org.eclipse.ditto.services.connectivity.messaging.httppush.HttpPushValidator;
import org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaValidator;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLoggerRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.RetrieveConnectionLogsAggregatorActor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.metrics.RetrieveConnectionMetricsAggregatorActor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.metrics.RetrieveConnectionStatusAggregatorActor;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.Mqtt3Validator;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.Mqtt5Validator;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.ConnectionState;
import org.eclipse.ditto.services.connectivity.messaging.persistence.stages.StagedCommand;
import org.eclipse.ditto.services.connectivity.messaging.persistence.strategies.commands.ConnectionCreatedStrategies;
import org.eclipse.ditto.services.connectivity.messaging.persistence.strategies.commands.ConnectionDeletedStrategies;
import org.eclipse.ditto.services.connectivity.messaging.persistence.strategies.events.ConnectionEventStrategies;
import org.eclipse.ditto.services.connectivity.messaging.rabbitmq.RabbitMQValidator;
import org.eclipse.ditto.services.connectivity.messaging.validation.CompoundConnectivityCommandInterceptor;
import org.eclipse.ditto.services.connectivity.messaging.validation.ConnectionValidator;
import org.eclipse.ditto.services.connectivity.messaging.validation.DittoConnectivityCommandValidator;
import org.eclipse.ditto.services.connectivity.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.services.models.connectivity.BaseClientState;
import org.eclipse.ditto.services.utils.akka.PingCommand;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.services.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.services.utils.persistentactors.AbstractShardedPersistenceActor;
import org.eclipse.ditto.services.utils.persistentactors.EmptyEvent;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.commands.DefaultContext;
import org.eclipse.ditto.services.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.signals.base.SignalWithEntityId;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandInterceptor;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CheckConnectionLogsActive;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.EnableConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.ConnectivityQueryCommand;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatusResponse;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.events.connectivity.ConnectionClosed;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.connectivity.ConnectionDeleted;
import org.eclipse.ditto.signals.events.connectivity.ConnectionModified;
import org.eclipse.ditto.signals.events.connectivity.ConnectionOpened;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.Terminated;
import akka.cluster.routing.ClusterRouterPool;
import akka.cluster.routing.ClusterRouterPoolSettings;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.persistence.RecoveryCompleted;
import akka.routing.Broadcast;
import akka.routing.ConsistentHashingPool;
import akka.routing.ConsistentHashingRouter;
import akka.routing.Pool;

/**
 * Handles {@code *Connection} commands and manages the persistence of connection. The actual connection handling to the
 * remote server is delegated to a child actor that uses a specific client (AMQP 1.0 or 0.9.1).
 */
public final class ConnectionPersistenceActor
        extends AbstractShardedPersistenceActor<ConnectivityCommand<?>, Connection, ConnectionId, ConnectionState,
        ConnectivityEvent<?>> {

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

    private static final Duration DEFAULT_RETRIEVE_STATUS_TIMEOUT = Duration.ofMillis(500L);

    private final ActorRef proxyActor;
    private final ClientActorPropsFactory propsFactory;
    private final boolean allClientActorsOnOneNode;
    private final ConnectivityCommandInterceptor commandValidator;
    private final ConnectionLogger connectionLogger;
    private final Duration clientActorAskTimeout;
    private final Duration checkLoggingActiveInterval;
    private final Duration loggingEnabledDuration;
    private final ConnectionConfig config;
    private final MonitoringConfig monitoringConfig;
    private final ClientActorRefs clientActorRefs = ClientActorRefs.empty();

    private int subscriptionCounter = 0;
    private Instant connectionClosedAt = Instant.now();
    @Nullable private Instant loggingEnabledUntil;
    @Nullable private ActorRef clientActorRouter;

    ConnectionPersistenceActor(final ConnectionId connectionId,
            final ActorRef proxyActor,
            final ClientActorPropsFactory propsFactory,
            @Nullable final ConnectivityCommandInterceptor customCommandValidator,
            final Trilean allClientActorsOnOneNode) {

        super(connectionId, new ConnectionMongoSnapshotAdapter());

        this.proxyActor = proxyActor;
        this.propsFactory = propsFactory;

        final ActorSystem actorSystem = getContext().getSystem();
        final ConnectivityConfigProvider configProvider = ConnectivityConfigProviderFactory.getInstance(actorSystem);
        final ConnectivityConfig connectivityConfig = configProvider.getConnectivityConfig(connectionId);
        config = connectivityConfig.getConnectionConfig();
        this.allClientActorsOnOneNode = allClientActorsOnOneNode.orElse(config.areAllClientActorsOnOneNode());

        final ConnectionValidator connectionValidator =
                ConnectionValidator.of(
                        configProvider,
                        actorSystem.log(),
                        RabbitMQValidator.newInstance(),
                        AmqpValidator.newInstance(),
                        Mqtt3Validator.newInstance(),
                        Mqtt5Validator.newInstance(),
                        KafkaValidator.getInstance(),
                        HttpPushValidator.newInstance());

        final DittoConnectivityCommandValidator dittoCommandValidator =
                new DittoConnectivityCommandValidator(propsFactory, proxyActor, getSelf(), connectionValidator,
                        actorSystem);

        if (customCommandValidator != null) {
            commandValidator =
                    new CompoundConnectivityCommandInterceptor(dittoCommandValidator, customCommandValidator);
        } else {
            commandValidator = dittoCommandValidator;
        }

        clientActorAskTimeout = config.getClientActorAskTimeout();

        monitoringConfig = connectivityConfig.getMonitoringConfig();
        final ConnectionLoggerRegistry loggerRegistry =
                ConnectionLoggerRegistry.fromConfig(monitoringConfig.logger());
        connectionLogger = loggerRegistry.forConnection(connectionId);


        this.loggingEnabledDuration = monitoringConfig.logger().logDuration();
        this.checkLoggingActiveInterval = monitoringConfig.logger().loggingActiveCheckInterval();
    }

    @Override
    protected DittoDiagnosticLoggingAdapter createLogger() {
        return DittoLoggerFactory.getDiagnosticLoggingAdapter(this)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID.toString(), entityId);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connectionId the connection ID.
     * @param proxyActor the actor used to send signals into the ditto cluster..
     * @param propsFactory factory of props of client actors for various protocols.
     * @param commandValidator validator for commands that should throw an exception if a command is invalid.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ConnectionId connectionId,
            final ActorRef proxyActor,
            final ClientActorPropsFactory propsFactory,
            @Nullable final ConnectivityCommandInterceptor commandValidator
    ) {
        return Props.create(ConnectionPersistenceActor.class, connectionId, proxyActor, propsFactory, commandValidator,
                Trilean.UNKNOWN);
    }

    /**
     * Compute the length of the subscription ID prefix to identify the client actor owning a search session.
     *
     * @param clientCount the client count of the connection.
     * @return the length of the subscription prefix.
     */
    public static int getSubscriptionPrefixLength(final int clientCount) {
        return Integer.toHexString(clientCount).length();
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
    protected Class<?> getEventClass() {
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
    protected EventStrategy<ConnectivityEvent<?>, Connection> getEventStrategy() {
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
    protected DittoRuntimeExceptionBuilder<?> newNotAccessibleExceptionBuilder() {
        return ConnectionNotAccessibleException.newBuilder(entityId);
    }

    @Override
    protected void publishEvent(final ConnectivityEvent<?> event) {
        // Do nothing because nobody subscribes for connectivity events.
    }

    @Override
    protected JsonSchemaVersion getEntitySchemaVersion(final Connection entity) {
        return entity.getImplementedSchemaVersion();
    }

    @Override
    protected boolean shouldSendResponse(final DittoHeaders dittoHeaders) {
        return dittoHeaders.isResponseRequired();
    }

    @Override
    protected boolean isEntityAlwaysAlive() {
        return isDesiredStateOpen();
    }

    @Override
    public void postStop() throws Exception {
        log.info("stopped connection <{}>", entityId);
        super.postStop();
    }

    @Override
    protected void recoveryCompleted(final RecoveryCompleted event) {
        log.info("Connection <{}> was recovered: {}", entityId, entity);
        if (entity != null && entity.getLifecycle().isEmpty()) {
            entity = entity.toBuilder().lifecycle(ConnectionLifecycle.ACTIVE).build();
        }
        if (isDesiredStateOpen()) {
            log.debug("Opening connection <{}> after recovery.", entityId);
            restoreOpenConnection();
        }
        super.recoveryCompleted(event);
    }

    @Override
    protected ConnectivityEvent<?> modifyEventBeforePersist(final ConnectivityEvent<?> event) {
        final ConnectivityEvent<?> superEvent = super.modifyEventBeforePersist(event);

        final ConnectivityStatus targetConnectionStatus;
        if (event instanceof ConnectionCreated) {
            targetConnectionStatus = ((ConnectionCreated) event).getConnection().getConnectionStatus();
        } else if (event instanceof ConnectionModified) {
            targetConnectionStatus = ((ConnectionModified) event).getConnection().getConnectionStatus();
        } else if (event instanceof ConnectionDeleted) {
            targetConnectionStatus = ConnectivityStatus.CLOSED;
        } else if (event instanceof ConnectionOpened) {
            targetConnectionStatus = ConnectivityStatus.OPEN;
        } else if (event instanceof ConnectionClosed) {
            targetConnectionStatus = ConnectivityStatus.CLOSED;
        } else if (null != entity) {
            targetConnectionStatus = entity.getConnectionStatus();
        } else {
            targetConnectionStatus = ConnectivityStatus.UNKNOWN;
        }

        if (alwaysAlive = (targetConnectionStatus == ConnectivityStatus.OPEN)) {
            final DittoHeaders headersWithJournalTags = superEvent.getDittoHeaders().toBuilder()
                    .journalTags(Set.of(JOURNAL_TAG_ALWAYS_ALIVE))
                    .build();
            return superEvent.setDittoHeaders(headersWithJournalTags);
        }
        return superEvent;
    }

    @Override
    protected void processPingCommand(final PingCommand ping) {

        super.processPingCommand(ping);
        final String journalTag = ping.getPayload()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .orElse(null);

        if (journalTag != null && journalTag.isEmpty() && isDesiredStateOpen()) {
            // persistence actor was sent a "ping" with empty journal tag:
            //  build in adding the "always-alive" tag here by persisting an "empty" event which is just tagged to be
            //  "always alive".  Stop persisting the empty event once every open connection has a tagged event, when
            // the persistence ping actor will have a non-empty journal tag configured.
            final EmptyEvent
                    emptyEvent = new EmptyEvent(entityId, EmptyEvent.EFFECT_ALWAYS_ALIVE, getRevisionNumber() + 1,
                    DittoHeaders.newBuilder()
                            .correlationId(ping.getCorrelationId().orElse(null))
                            .journalTags(Set.of(JOURNAL_TAG_ALWAYS_ALIVE))
                            .build());
            getSelf().tell(new PersistEmptyEvent(emptyEvent), ActorRef.noSender());
        }
    }

    @Override
    public void onMutation(final Command<?> command, final ConnectivityEvent<?> event,
            final WithDittoHeaders response, final boolean becomeCreated, final boolean becomeDeleted) {
        if (command instanceof StagedCommand) {
            interpretStagedCommand(((StagedCommand) command).withSenderUnlessDefined(getSender()));
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

    /**
     * Carry out the actions in a staged command. Synchronous actions are performed immediately followed by recursion
     * onto the next action. Asynchronous action are scheduled with the sending of the next staged command at the end.
     * Failures abort all asynchronous actions except OPEN_CONNECTION_IGNORE_ERRORS.
     *
     * @param command the staged command.
     */
    private void interpretStagedCommand(final StagedCommand command) {
        if (!command.hasNext()) {
            // execution complete
            return;
        }
        switch (command.nextAction()) {
            case TEST_CONNECTION:
                testConnection(command.next());
                break;
            case APPLY_EVENT:
                command.getEvent()
                        .ifPresentOrElse(event -> {
                                    entity = getEventStrategy().handle(event, entity, getRevisionNumber());
                                    interpretStagedCommand(command.next());
                                },
                                () -> log.error(
                                        "Failed to handle staged command because required event wasn't present: <{}>",
                                        command));
                break;
            case PERSIST_AND_APPLY_EVENT:
                command.getEvent().ifPresentOrElse(
                        event -> persistAndApplyEvent(event,
                                (unusedEvent, connection) -> interpretStagedCommand(command.next())),
                        () -> log.error("Failed to handle staged command because required event wasn't present: <{}>",
                                command));
                break;
            case SEND_RESPONSE:
                command.getSender().tell(command.getResponse(), getSelf());
                interpretStagedCommand(command.next());
                break;
            case PASSIVATE:
                // This actor will stop. Subsequent actions are ignored.
                passivate();
                break;
            case OPEN_CONNECTION:
                openConnection(command.next(), false);
                break;
            case OPEN_CONNECTION_IGNORE_ERRORS:
                openConnection(command.next(), true);
                break;
            case CLOSE_CONNECTION:
                closeConnection(command.next());
                break;
            case STOP_CLIENT_ACTORS:
                stopClientActors();
                interpretStagedCommand(command.next());
                break;
            case BECOME_CREATED:
                becomeCreatedHandler();
                interpretStagedCommand(command.next());
                break;
            case BECOME_DELETED:
                becomeDeletedHandler();
                interpretStagedCommand(command.next());
                break;
            case UPDATE_SUBSCRIPTIONS:
                prepareForSignalForwarding(command.next());
                break;
            case BROADCAST_TO_CLIENT_ACTORS_IF_STARTED:
                broadcastToClientActorsIfStarted(command.getCommand(), getSelf());
                interpretStagedCommand(command.next());
                break;
            case RETRIEVE_CONNECTION_LOGS:
                retrieveConnectionLogs((RetrieveConnectionLogs) command.getCommand(), command.getSender());
                interpretStagedCommand(command.next());
                break;
            case RETRIEVE_CONNECTION_STATUS:
                retrieveConnectionStatus((RetrieveConnectionStatus) command.getCommand(), command.getSender());
                interpretStagedCommand(command.next());
                break;
            case RETRIEVE_CONNECTION_METRICS:
                retrieveConnectionMetrics((RetrieveConnectionMetrics) command.getCommand(), command.getSender());
                interpretStagedCommand(command.next());
                break;
            case ENABLE_LOGGING:
                loggingEnabled();
                interpretStagedCommand(command.next());
                break;
            case DISABLE_LOGGING:
                loggingDisabled();
                interpretStagedCommand(command.next());
                break;
            default:
                log.error("Failed to handle staged command: <{}>", command);
        }
    }

    @Override
    protected Receive matchAnyAfterInitialization() {
        return ReceiveBuilder.create()
                .match(CreateSubscription.class, this::startThingSearchSession)
                .matchEquals(Control.CHECK_LOGGING_ACTIVE, this::checkLoggingEnabled)

                // maintain client actor refs
                .match(ActorRef.class, this::addClientActor)
                .match(Terminated.class, this::removeClientActor)

                .matchAny(message -> log.warning("Unknown message: {}", message))
                .build();
    }

    /**
     * Route search commands according to subscription ID prefix. This is necessary so that for connections with
     * client count > 1, all commands related to 1 search session are routed to the same client actor. This is achieved
     * by using a prefix of fixed length of subscription IDs as the hash key of search commands. The length of the
     * prefix depends on the client count configured in the connection; it is 1 for connections with client count <= 15.
     * <p>
     * For the search protocol, all incoming messages are commands (ThingSearchCommand) and all outgoing messages are
     * events (SubscriptionEvent).
     * <p>
     * Message path for incoming search commands:
     * <pre>
     * ConsumerActor -> MessageMappingProcessorActor -> ConnectionPersistenceActor -> ClientActor -> SubscriptionManager
     * </pre>
     * Message path for outgoing search events:
     * <pre>
     * SubscriptionActor -> MessageMappingProcessorActor -> PublisherActor
     * </pre>
     *
     * @param command the command to route.
     */
    private void startThingSearchSession(final CreateSubscription command) {
        if (clientActorRouter == null) {
            logDroppedSignal(command, command.getType(), "Client actor not ready.");
            return;
        }
        if (entity == null) {
            logDroppedSignal(command, command.getType(), "No Connection configuration available.");
            return;
        }
        log.debug("Forwarding <{}> to client actors.", command);
        // compute the next prefix according to subscriptionCounter and the currently configured client actor count
        // ignore any "prefix" field from the command
        augmentWithPrefixAndForward(command, entity.getClientCount(), clientActorRouter);
    }

    private void augmentWithPrefixAndForward(final CreateSubscription createSubscription, final int clientCount,
            final ActorRef clientActorRouter) {
        subscriptionCounter = (subscriptionCounter + 1) % Math.max(1, clientCount);
        final int prefixLength = getSubscriptionPrefixLength(clientCount);
        final String prefix = String.format("%0" + prefixLength + "X", subscriptionCounter);
        final Optional<ActorRef> receiver = clientActorRefs.get(subscriptionCounter);
        final CreateSubscription commandWithPrefix = createSubscription.setPrefix(prefix);
        if (clientCount == 1) {
            clientActorRouter.tell(consistentHashableEnvelope(commandWithPrefix, prefix), ActorRef.noSender());
        } else if (receiver.isPresent()) {
            receiver.get().tell(commandWithPrefix, ActorRef.noSender());
        } else {
            logDroppedSignal(createSubscription, createSubscription.getType(), "Client actor not ready.");
        }
    }

    private void checkLoggingEnabled(final Control message) {
        final CheckConnectionLogsActive checkLoggingActive = CheckConnectionLogsActive.of(entityId, Instant.now());
        broadcastToClientActorsIfStarted(checkLoggingActive, getSelf());
    }

    private void prepareForSignalForwarding(final StagedCommand command) {
        if (isDesiredStateOpen()) {
            startEnabledLoggingChecker();
            updateLoggingIfEnabled();
        }
        interpretStagedCommand(command);
    }

    private void testConnection(final StagedCommand command) {
        final ActorRef origin = command.getSender();
        final ActorRef self = getSelf();
        final TestConnection testConnection = (TestConnection) command.getCommand();

        if (clientActorRouter != null) {
            // client actor is already running, so either another TestConnection command is currently executed or the
            // connection has been created in the meantime. In either case reject the new TestConnection command to
            // prevent strange behavior.
            origin.tell(TestConnectionResponse.alreadyCreated(entityId, command.getDittoHeaders()), self);
        } else {
            // no need to start more than 1 client for tests
            // set connection status to CLOSED so that client actors will not try to connect on startup
            setConnectionStatusClosedForTestConnection();
            startAndAskClientActors(testConnection, 1)
                    .thenAccept(response -> self.tell(
                            command.withResponse(TestConnectionResponse.success(testConnection.getEntityId(),
                                    response.toString(), command.getDittoHeaders())),
                            ActorRef.noSender()))
                    .exceptionally(error -> {
                        self.tell(
                                command.withResponse(
                                        toDittoRuntimeException(error, entityId, command.getDittoHeaders())),
                                ActorRef.noSender());
                        return null;
                    });
        }
    }

    private void setConnectionStatusClosedForTestConnection() {
        if (entity != null) {
            entity = entity.toBuilder().connectionStatus(ConnectivityStatus.CLOSED).build();
        }
    }

    private void openConnection(final StagedCommand command, final boolean ignoreErrors) {
        final OpenConnection openConnection = OpenConnection.of(entityId, command.getDittoHeaders());
        final Consumer<Object> successConsumer = response -> getSelf().tell(command, ActorRef.noSender());
        startAndAskClientActors(openConnection, getClientCount())
                .thenAccept(successConsumer)
                .exceptionally(error -> {
                    if (ignoreErrors) {
                        // log the exception and proceed
                        handleException("open-connection", command.getSender(), error, false);
                        successConsumer.accept(error);
                        return null;
                    } else {
                        return handleException("open-connection", command.getSender(), error);
                    }
                });
    }

    private void closeConnection(final StagedCommand command) {
        final CloseConnection closeConnection = CloseConnection.of(entityId, command.getDittoHeaders());
        broadcastToClientActorsIfStarted(closeConnection)
                .thenAccept(response -> getSelf().tell(command, ActorRef.noSender()))
                .exceptionally(error -> {
                    // stop client actors anyway---the closed status is already persisted.
                    stopClientActors();
                    return handleException("disconnect", command.getSender(), error);
                });
    }

    private void logDroppedSignal(final WithDittoHeaders withDittoHeaders, final String type, final String reason) {
        log.withCorrelationId(withDittoHeaders).debug("Signal ({}) dropped: {}", type, reason);
    }

    private void retrieveConnectionLogs(final RetrieveConnectionLogs command, final ActorRef sender) {
        this.updateLoggingIfEnabled();
        broadcastCommandWithDifferentSender(command,
                (existingConnection, timeout) -> RetrieveConnectionLogsAggregatorActor.props(
                        existingConnection, sender, command.getDittoHeaders(), timeout,
                        monitoringConfig.logger().maxLogSizeInBytes()),
                () -> respondWithEmptyLogs(command, sender));
    }

    private boolean isLoggingEnabled() {
        return loggingEnabledUntil != null && Instant.now().isBefore(loggingEnabledUntil);
    }

    private void loggingEnabled() {
        // start check logging scheduler
        startEnabledLoggingChecker();
        loggingEnabledUntil = Instant.now().plus(this.loggingEnabledDuration);
    }

    private void updateLoggingIfEnabled() {
        if (isLoggingEnabled()) {
            loggingEnabledUntil = Instant.now().plus(loggingEnabledDuration);
            broadcastToClientActorsIfStarted(EnableConnectionLogs.of(entityId, DittoHeaders.empty()),
                    ActorRef.noSender());
        }
    }

    private void loggingDisabled() {
        loggingEnabledUntil = null;
        cancelEnabledLoggingChecker();
    }

    private void cancelEnabledLoggingChecker() {
        timers().cancel(Control.CHECK_LOGGING_ACTIVE);
    }

    private void startEnabledLoggingChecker() {
        timers().startTimerWithFixedDelay(Control.CHECK_LOGGING_ACTIVE, Control.CHECK_LOGGING_ACTIVE,
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

    private CompletionStage<Object> startAndAskClientActors(final SignalWithEntityId<?> cmd, final int clientCount) {
        startClientActorsIfRequired(clientCount);
        final Object msg = consistentHashableEnvelope(cmd, cmd.getEntityId().toString());
        return processClientAskResult(Patterns.ask(clientActorRouter, msg, clientActorAskTimeout));
    }

    private Object consistentHashableEnvelope(final Object message, final String hashKey) {
        return new ConsistentHashingRouter.ConsistentHashableEnvelope(message, hashKey);
    }

    private void broadcastToClientActorsIfStarted(final Command<?> cmd, final ActorRef sender) {
        if (clientActorRouter != null && entity != null) {
            clientActorRouter.tell(new Broadcast(cmd), sender);
        }
    }

    /*
     * NOT thread-safe.
     */
    private CompletionStage<Object> broadcastToClientActorsIfStarted(final Command<?> cmd) {
        if (clientActorRouter != null && entity != null) {
            // wrap in Broadcast message because these management messages must be delivered to each client actor
            final Broadcast broadcast = new Broadcast(cmd);
            return processClientAskResult(Patterns.ask(clientActorRouter, broadcast, clientActorAskTimeout));
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private void broadcastCommandWithDifferentSender(final ConnectivityQueryCommand<?> command,
            final BiFunction<Connection, Duration, Props> senderPropsForConnectionWithTimeout,
            final Runnable onClientActorNotStarted) {
        if (clientActorRouter != null && entity != null) {
            // timeout before sending the (partial) response
            final Duration timeout = extractTimeoutFromCommand(command.getDittoHeaders());
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
    private Void handleException(final String action, @Nullable final ActorRef origin, final Throwable exception) {
        return handleException(action, origin, exception, true);
    }

    private Void handleException(final String action,
            @Nullable final ActorRef origin,
            final Throwable error,
            final boolean sendExceptionResponse) {

        final DittoRuntimeException dre = toDittoRuntimeException(error, entityId, DittoHeaders.empty());

        if (sendExceptionResponse && origin != null) {
            origin.tell(dre, getSelf());
        }
        connectionLogger.failure("Operation {0} failed due to {1}", action, dre.getMessage());
        log.warning("Operation <{}> on connection <{}> failed due to {}: {}.", action, entityId,
                dre.getClass().getSimpleName(), dre.getMessage());
        return null;
    }


    private void retrieveConnectionStatus(final RetrieveConnectionStatus command, final ActorRef sender) {
        checkNotNull(entity, "Connection");
        // timeout before sending the (partial) response
        final Duration timeout = extractTimeoutFromCommand(command.getDittoHeaders());
        final Props props = RetrieveConnectionStatusAggregatorActor.props(entity, sender,
                command.getDittoHeaders(), timeout);
        forwardToClientActors(props, command, () -> respondWithEmptyStatus(command, sender));
    }

    private static Duration extractTimeoutFromCommand(final DittoHeaders headers) {
        return Duration.ofMillis(
                (long) (headers.getTimeout().orElse(DEFAULT_RETRIEVE_STATUS_TIMEOUT).toMillis() * 0.75)
        );
    }

    private void retrieveConnectionMetrics(final RetrieveConnectionMetrics command, final ActorRef sender) {
        broadcastCommandWithDifferentSender(command,
                (existingConnection, timeout) -> RetrieveConnectionMetricsAggregatorActor.props(
                        existingConnection, sender, command.getDittoHeaders(), timeout),
                () -> respondWithEmptyMetrics(command, sender));
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

    private void startClientActorsIfRequired(final int clientCount) {
        if (entity != null && clientActorRouter == null && clientCount > 0) {
            log.info("Starting ClientActor for connection <{}> with <{}> clients.", entityId, clientCount);
            final Props props = propsFactory.getActorPropsForType(entity, proxyActor, getSelf());
            final ClusterRouterPoolSettings clusterRouterPoolSettings =
                    new ClusterRouterPoolSettings(clientCount, clientActorsPerNode(clientCount), true,
                            Set.of(CLUSTER_ROLE));
            final Pool pool = new ConsistentHashingPool(clientCount);
            final Props clusterRouterPoolProps =
                    new ClusterRouterPool(pool, clusterRouterPoolSettings).props(props);

            // start client actor without name so it does not conflict with its previous incarnation
            clientActorRouter = getContext().actorOf(clusterRouterPoolProps);
        } else if (clientActorRouter != null) {
            log.debug("ClientActor already started.");
        } else {
            log.error(new IllegalStateException(), "Trying to start client actor without a connection");
        }
    }

    private int clientActorsPerNode(final int clientCount) {
        return allClientActorsOnOneNode ? clientCount : 1;
    }

    private int getClientCount() {
        return entity == null ? 0 : entity.getClientCount();
    }

    private void stopClientActors() {
        clientActorRefs.clear();
        if (clientActorRouter != null) {
            connectionClosedAt = Instant.now();
            log.debug("Stopping the client actor.");
            stopChildActor(clientActorRouter);
            clientActorRouter = null;
        }
    }

    private void addClientActor(final ActorRef newClientActor) {
        getContext().watch(newClientActor);
        clientActorRefs.add(newClientActor);
        final List<ActorRef> otherClientActors = clientActorRefs.getOtherActors(newClientActor);
        otherClientActors.forEach(otherClientActor -> {
            otherClientActor.tell(newClientActor, ActorRef.noSender());
            newClientActor.tell(otherClientActor, ActorRef.noSender());
        });
    }

    private void removeClientActor(final Terminated terminated) {
        clientActorRefs.remove(terminated.getActor());
    }

    private void stopChildActor(final ActorRef actor) {
        log.debug("Stopping child actor <{}>.", actor.path());
        getContext().stop(actor);
    }

    private boolean isDesiredStateOpen() {
        return entity != null &&
                !entity.hasLifecycle(ConnectionLifecycle.DELETED) &&
                entity.getConnectionStatus() == ConnectivityStatus.OPEN;
    }

    private void restoreOpenConnection() {
        final OpenConnection connect = OpenConnection.of(entityId, DittoHeaders.empty());
        final ConnectionOpened connectionOpened = ConnectionOpened.of(entityId, Instant.now(), DittoHeaders.empty());
        final StagedCommand stagedCommand = StagedCommand.of(connect, connectionOpened, connect,
                Collections.singletonList(UPDATE_SUBSCRIPTIONS));
        openConnection(stagedCommand, false);
    }

    private static DittoRuntimeException toDittoRuntimeException(final Throwable error, final ConnectionId id,
            final DittoHeaders headers) {

        return DittoRuntimeException.asDittoRuntimeException(error,
                cause -> ConnectionFailedException.newBuilder(id)
                        .description(cause.getMessage())
                        .cause(cause)
                        .dittoHeaders(headers)
                        .build());
    }

    private static CompletionStage<Object> processClientAskResult(final CompletionStage<Object> askResultFuture) {
        return askResultFuture.thenCompose(response -> {
            if (response instanceof Status.Failure) {
                return CompletableFuture.failedStage(((Status.Failure) response).cause());
            } else if (response instanceof DittoRuntimeException) {
                return CompletableFuture.failedStage((DittoRuntimeException) response);
            } else {
                return CompletableFuture.completedFuture(response);
            }
        });
    }

    /**
     * Message that will be sent by scheduler.
     */
    enum Control {

        /**
         * Indicates a check if logging is still enabled for this connection.
         */
        CHECK_LOGGING_ACTIVE
    }

}
