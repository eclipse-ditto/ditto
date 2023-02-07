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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.connectivity.api.BaseClientState.CONNECTED;
import static org.eclipse.ditto.connectivity.api.BaseClientState.CONNECTING;
import static org.eclipse.ditto.connectivity.api.BaseClientState.DISCONNECTED;
import static org.eclipse.ditto.connectivity.api.BaseClientState.DISCONNECTING;
import static org.eclipse.ditto.connectivity.api.BaseClientState.INITIALIZED;
import static org.eclipse.ditto.connectivity.api.BaseClientState.TESTING;
import static org.eclipse.ditto.connectivity.api.BaseClientState.UNKNOWN;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.FatalPubSubException;
import org.eclipse.ditto.base.model.acks.PubSubTerminatedException;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithStreamingSubscriptionId;
import org.eclipse.ditto.base.model.signals.WithType;
import org.eclipse.ditto.base.model.signals.commands.streaming.StreamingSubscriptionCommand;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.api.InboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.messaging.monitoring.logs.LogEntryFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.FilteredTopic;
import org.eclipse.ditto.connectivity.model.RecoveryStatus;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.SshTunnel;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionClosedAnnouncement;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectionOpenedAnnouncement;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionFailedException;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionSignalIllegalException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CheckConnectionLogsActive;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.EnableConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.LoggingExpired;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ResetConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ResetConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogsResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionStatus;
import org.eclipse.ditto.connectivity.service.config.ClientConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectionThrottlingConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientConnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLoggerRegistry;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.ConnectivityCounterRegistry;
import org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionPersistenceActor;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelActor;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.eclipse.ditto.connectivity.service.messaging.validation.ConnectionValidator;
import org.eclipse.ditto.connectivity.service.util.ConnectionPubSub;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.edge.service.headers.DittoHeadersValidator;
import org.eclipse.ditto.edge.service.streaming.StreamingSubscriptionManager;
import org.eclipse.ditto.internal.models.signal.correlation.MatchingValidationResult;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.internal.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.pubsubthings.DittoProtocolSub;
import org.eclipse.ditto.internal.utils.search.SubscriptionManager;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.WithSubscriptionId;

import com.typesafe.config.Config;

import akka.Done;
import akka.NotUsed;
import akka.actor.AbstractFSMWithStash;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.CoordinatedShutdown;
import akka.actor.FSM;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.pubsub.DistributedPubSub;
import akka.japi.Pair;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.Patterns;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;

/**
 * Base class for ClientActors which implement the connection handling for various connectivity protocols.
 * <p>
 * The actor expects to receive a {@link CreateConnection} command after it was started. If this command is not received
 * within timeout (can be the case when this actor is remotely deployed after the command was sent) the actor requests
 * the required information from ConnectionActor.
 * </p>
 */
public abstract class BaseClientActor extends AbstractFSMWithStash<BaseClientState, BaseClientData> {

    /**
     * Common logger for all sub-classes of BaseClientActor as its MDC already contains the connection ID.
     */
    protected final ThreadSafeDittoLoggingAdapter logger;
    protected static final Status.Success DONE = new Status.Success(Done.getInstance());

    protected final ConnectionLogger connectionLogger;
    protected final ConnectivityStatusResolver connectivityStatusResolver;

    private static final String CONNECTION_STATUS_DETAILS_CONNECTED = "CONNECTED";
    /**
     * The name of the dispatcher that will be used for async mapping.
     */
    private static final String MESSAGE_MAPPING_PROCESSOR_DISPATCHER = "message-mapping-processor-dispatcher";
    private static final Pattern EXCLUDED_ADDRESS_REPORTING_CHILD_NAME_PATTERN = Pattern.compile(
            OutboundMappingProcessorActor.ACTOR_NAME + "|" + OutboundDispatchingActor.ACTOR_NAME + "|" +
                    "ackr.*" + "|" + "StreamSupervisor-.*|" +
                    SubscriptionManager.ACTOR_NAME  + "|" + StreamingSubscriptionManager.ACTOR_NAME);
    private static final String DITTO_STATE_TIMEOUT_TIMER = "dittoStateTimeout";

    private static final int SOCKET_CHECK_TIMEOUT_MS = 2000;
    private static final String CLOSED_BECAUSE_OF_UNKNOWN_FAILURE_MISCONFIGURATION_STATUS_IN_CLIENT =
            "Closed because of unknown/failure/misconfiguration status in client.";

    private final Connection connection;
    private final ConnectivityConfig connectivityConfig;
    private final ActorRef connectionActor;
    private final ActorSelection commandForwarderActorSelection;
    private final Gauge clientGauge;
    private final Gauge clientConnectingGauge;
    private final ReconnectTimeoutStrategy reconnectTimeoutStrategy;
    private final SupervisorStrategy supervisorStrategy;
    private final ConnectionPubSub connectionPubSub;
    private final DittoProtocolSub dittoProtocolSub;
    private final int subscriptionIdPrefixLength;
    protected final UUID actorUuid;
    private final ProtocolAdapter protocolAdapter;
    protected final ConnectivityCounterRegistry connectionCounterRegistry;
    protected final ConnectionLoggerRegistry connectionLoggerRegistry;
    protected final ChildActorNanny childActorNanny;
    private final DittoHeadersValidator dittoHeadersValidator;
    private final boolean dryRun;
    private final List<Cancellable> cancelOnStopTasks = new ArrayList<>();

    private Sink<Object, NotUsed> inboundMappingSink;
    private ActorRef outboundDispatchingActor;
    private ActorRef subscriptionManager;
    private ActorRef streamingSubscriptionManager;
    private ActorRef tunnelActor;
    private int ackregatorCount = 0;
    private boolean shuttingDown = false;

    protected BaseClientActor(final Connection connection,
            final ActorRef commandForwarderActor,
            final ActorRef connectionActor,
            final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {

        this.connection = checkNotNull(connection, "connection");
        final ActorSystem system = getContext().getSystem();
        final Config config = system.settings().config();
        final Config withOverwrites = connectivityConfigOverwrites.withFallback(config);
        connectivityConfig = ConnectivityConfig.of(withOverwrites);
        this.connectionActor = connectionActor;

        // this is retrieve via the extension for each baseClientActor in order to not pass it as constructor arg
        //  as all constructor arguments need to be serializable as the BaseClientActor is started behind a cluster
        //  router
        dittoProtocolSub = DittoProtocolSub.get(system);
        actorUuid = UUID.randomUUID();

        final var connectionId = connection.getId();
        logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connection.getId());
        // log the default client ID for tracing
        logger.info("Using default client ID <{}>", getDefaultClientId());

        commandForwarderActorSelection = getLocalActorOfSamePath(commandForwarderActor);
        childActorNanny = ChildActorNanny.newInstance(getContext(), logger);
        dittoHeadersValidator = DittoHeadersValidator.get(system, ScopedConfig.dittoExtension(config));

        final UserIndicatedErrors userIndicatedErrors = UserIndicatedErrors.of(config);
        connectivityStatusResolver = ConnectivityStatusResolver.of(userIndicatedErrors);

        final ClientConfig clientConfig = connectivityConfig().getClientConfig();
        final var protocolAdapterProvider =
                ProtocolAdapterProvider.load(connectivityConfig().getProtocolConfig(), system);
        protocolAdapter = protocolAdapterProvider.getProtocolAdapter(null);
        final var monitoringConfig = connectivityConfig().getMonitoringConfig();
        connectionCounterRegistry = ConnectivityCounterRegistry.newInstance(connectivityConfig);
        connectionLoggerRegistry = ConnectionLoggerRegistry.fromConfig(monitoringConfig.logger());
        connectionLoggerRegistry.initForConnection(connection);
        connectionLogger = connectionLoggerRegistry.forConnection(connection.getId());
        connectionCounterRegistry.initForConnection(connection);
        clientGauge = DittoMetrics.gauge("connection_client")
                .tag("id", connectionId.toString())
                .tag("type", connection.getConnectionType().getName());
        clientConnectingGauge = DittoMetrics.gauge("connecting_client")
                .tag("id", connectionId.toString())
                .tag("type", connection.getConnectionType().getName());

        reconnectTimeoutStrategy = DuplicationReconnectTimeoutStrategy.fromConfig(clientConfig);
        supervisorStrategy = createSupervisorStrategy(getSelf());
        connectionPubSub = ConnectionPubSub.get(system);
        subscriptionIdPrefixLength =
                ConnectionPersistenceActor.getSubscriptionPrefixLength(connection.getClientCount());

        // Send init message to allow for unsafe initialization of subclasses.
        dryRun = dittoHeaders.isDryRun();
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return supervisorStrategy;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();

        // stable states
        when(UNKNOWN, inUnknownState());
        when(INITIALIZED, inInitializedState());
        when(CONNECTED, inConnectedState());
        when(DISCONNECTED, inDisconnectedState());

        // volatile states
        //
        // DO NOT use state timeout:
        // FSM state timeout gets reset by any message, AND cannot be longer than 5 minutes (Akka v2.5.23).
        when(DISCONNECTING, inDisconnectingState());
        when(CONNECTING, inConnectingState());
        when(TESTING, inTestingState());

        // start with UNKNOWN state but send self OpenConnection because client actors are never created closed
        final var startingData = BaseClientData.BaseClientDataBuilder.initialized(connection).build();
        startWith(UNKNOWN, startingData);

        onTransition(this::onTransition);

        whenUnhandled(inAnyState().anyEvent(this::onUnknownEvent));

        initialize();

        // register this client actor at connection pub-sub
        registerConnectionPubSub();
        addCoordinatedShutdownTasks();

        init();
    }

    protected ConnectivityConfig connectivityConfig() {
        return connectivityConfig;
    }

    /**
     * Initialize child actors.
     */
    protected void init() {
        final var actorPair = startOutboundActors(protocolAdapter);
        outboundDispatchingActor = actorPair.first();

        final var inboundDispatchingSink = getInboundDispatchingSink(actorPair.second());
        inboundMappingSink = getInboundMappingSink(protocolAdapter, inboundDispatchingSink);
        subscriptionManager =
                startSubscriptionManager(commandForwarderActorSelection, connectivityConfig().getClientConfig());
        streamingSubscriptionManager = startStreamingSubscriptionManager(commandForwarderActorSelection, connectivityConfig().getClientConfig());

        if (connection.getSshTunnel().map(SshTunnel::isEnabled).orElse(false)) {
            tunnelActor = childActorNanny.startChildActor(SshTunnelActor.ACTOR_NAME,
                    SshTunnelActor.props(connection, connectivityStatusResolver, connectionLogger));
        } else {
            tunnelActor = null;
        }
        getSelf().tell(Control.INIT_COMPLETE, ActorRef.noSender());
    }

    private void registerConnectionPubSub() {
        if (dryRun) {
            // no need to modify distributed data if it is a dry run
            return;
        }
        final var connectionId = connection.getId();
        connectionPubSub.subscribe(connectionId, getSelf(), false).whenComplete((consistent, error) -> {
            if (error != null) {
                logger.error(error, "Failed to register client actor for connection <{}>", connectionId);
            } else {
                logger.info("Client actor registered: <{}>", connectionId);
            }
        });

    }

    private void addCoordinatedShutdownTasks() {
        final var system = getContext().getSystem();
        final var coordinatedShutdown = CoordinatedShutdown.get(system);
        if (shouldAnyTargetSendConnectionAnnouncements()) {
            // only add clients of connections to Coordinated shutdown having connection announcements configured
            coordinatedShutdown.addActorTerminationTask(
                    CoordinatedShutdown.PhaseServiceRequestsDone(),
                    "closeConnectionAndShutdown",
                    getSelf(),
                    Optional.of(CloseConnectionAndShutdown.INSTANCE)
            );
        }
        final var id = getSelf().path().toString();
        cancelOnStopTasks.add(coordinatedShutdown.addCancellableTask(CoordinatedShutdown.PhaseServiceUnbind(),
                "service-unbind-" + id, askSelfShutdownTask(Control.SERVICE_UNBIND)));
        coordinatedShutdown.addActorTerminationTask(CoordinatedShutdown.PhaseServiceRequestsDone(),
                "service-requests-done" + id, getSelf(), Optional.of(Control.SERVICE_REQUESTS_DONE));
    }

    private boolean shouldAnyTargetSendConnectionAnnouncements() {
        return connection().getTargets().stream()
                .anyMatch(target -> target.getTopics().stream()
                        .map(FilteredTopic::getTopic)
                        .anyMatch(Topic.CONNECTION_ANNOUNCEMENTS::equals));
    }

    @Override
    public void postStop() {
        cancelOnStopTasks.forEach(Cancellable::cancel);
        clientGauge.reset();
        clientConnectingGauge.reset();
        stopChildActor(tunnelActor);
        logger.debug("Stopped client with id - <{}>", getDefaultClientId());
        try {
            connectionLogger.close();
        } catch (final IOException e) {
            logger.warning("Exception during closing connection logger <{}>: {}", e.getClass().getSimpleName(),
                    e.getMessage());
        }
        try {
            super.postStop();
        } catch (final Exception e) {
            logger.error(e, "An error occurred post stop.");
        }
    }

    /**
     * Compute the client ID for this actor. The format of the client ID is prefix-uuid, where the uuid is unique to
     * each incarnation of this actor.
     *
     * @param prefix the prefix.
     * @return the client ID.
     */
    protected String getClientId(final CharSequence prefix) {
        if (connection.getClientCount() == 1) {
            return prefix.toString();
        } else {
            return prefix + "_" + actorUuid;
        }
    }

    /**
     * Get the default client ID, which identifies this actor regardless of configuration.
     *
     * @return the default client ID.
     */
    protected String getDefaultClientId() {
        return getClientId(connection.getId());
    }

    private FSM.State<BaseClientState, BaseClientData> completeInitialization() {

        final State<BaseClientState, BaseClientData> state = goTo(INITIALIZED);

        // Always open connection right away when desired---this actor may be deployed onto other instances and
        // will not be directly controlled by the connection persistence actor.
        if (connection.getConnectionStatus() == ConnectivityStatus.OPEN) {
            getSelf().tell(OpenConnection.of(connection.getId(), DittoHeaders.empty()), getSelf());
        }

        unstashAll();

        return state;
    }

    /**
     * Handles {@link TestConnection} commands by returning a CompletionState of {@link akka.actor.Status.Status Status}
     * which may be {@link akka.actor.Status.Success Success} or {@link akka.actor.Status.Failure Failure}.
     *
     * @param testConnectionCommand the Connection to test
     * @return the CompletionStage with the test result
     */
    protected abstract CompletionStage<Status.Status> doTestConnection(TestConnection testConnectionCommand);

    /**
     * Stop consuming messages from the connection.
     *
     * @return the CompletionStage that completes or fails according to whether consumer disconnection is successful.
     */
    protected abstract CompletionStage<Void> stopConsuming();

    /**
     * Subclasses should allocate resources (publishers and consumers) in the implementation. This method is called once
     * this {@code Client} connected successfully.
     *
     * @param clientConnected the ClientConnected message which may be subclassed and thus adding more information
     */
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        // do nothing by default
    }

    /**
     * Clean up everything spawned in {@code allocateResourcesOnConnection}. It should be idempotent.
     */
    protected abstract void cleanupResourcesForConnection();

    /**
     * Invoked when this {@code Client} should connect.
     *
     * @param connection the Connection to use for connecting.
     * @param origin the ActorRef which caused the ConnectClient command.
     */
    protected abstract void doConnectClient(Connection connection, @Nullable ActorRef origin);

    /**
     * Invoked when this {@code Client} should disconnect.
     *
     * @param connection the Connection to use for disconnecting.
     * @param origin the ActorRef which caused the DisconnectClient command.
     * @param shutdownAfterDisconnect whether the base client actor should terminate itself after disconnection.
     */
    protected abstract void doDisconnectClient(Connection connection, @Nullable ActorRef origin,
            final boolean shutdownAfterDisconnect);

    /**
     * Release any temporary resources allocated during a connection operation when the operation times out. Do nothing
     * by default.
     *
     * @param state current state of the client actor.
     */
    protected void cleanupFurtherResourcesOnConnectionTimeout(final BaseClientState state) {
        // do nothing by default
    }

    /**
     * Creates the handler for messages common to all states.
     * <p>
     * Overwrite and extend by additional matchers.
     * </p>
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inAnyState() {
        return matchEvent(RetrieveConnectionMetrics.class, (command, data) -> retrieveConnectionMetrics(command))
                .event(StreamingSubscriptionCommand.class, this::forwardStreamingSubscriptionCommand)
                .event(ThingSearchCommand.class, this::forwardThingSearchCommand)
                .event(RetrieveConnectionStatus.class, this::retrieveConnectionStatus)
                .event(ResetConnectionMetrics.class, this::resetConnectionMetrics)
                .event(EnableConnectionLogs.class, (command, data) -> enableConnectionLogs(command))
                .event(RetrieveConnectionLogs.class, (command, data) -> retrieveConnectionLogs(command))
                .event(ResetConnectionLogs.class, this::resetConnectionLogs)
                .event(CheckConnectionLogsActive.class, (command, data) -> checkLoggingActive(command))
                .event(InboundSignal.class, (signal, d) -> signal.isDispatched(), this::handleDispatchedInboundSignal)
                .event(InboundSignal.class, this::handleInboundSignal)
                .event(PublishMappedMessage.class, this::publishMappedMessage)
                .event(ConnectivityCommand.class, this::onUnknownEvent) // relevant connectivity commands were handled
                .event(Signal.class, this::handleSignal)
                .event(FatalPubSubException.class, this::failConnectionDueToPubSubException)
                .eventEquals(Control.ACKREGATOR_STARTED, this::ackregatorStarted)
                .eventEquals(Control.ACKREGATOR_STOPPED, this::ackregatorStopped)
                .eventEquals(Control.SERVICE_REQUESTS_DONE, this::serviceRequestsDone)
                .eventEquals(Control.SHUTDOWN_TIMEOUT, this::shutdownTimeout);
    }

    /**
     * @return the inbound mapping sink defined by {@link InboundMappingSink}.
     */
    protected final Sink<Object, NotUsed> getInboundMappingSink() {
        return inboundMappingSink;
    }

    /**
     * Escapes the passed actorName in a actorName valid way. Actor name should be a valid URL with ASCII letters, see
     * also {@code akka.actor.ActorPath#isValidPathElement}, therefore we encode the name as an ASCII URL.
     *
     * @param name the actorName to escape.
     * @return the escaped name.
     */
    protected static String escapeActorName(final String name) {
        return URLEncoder.encode(name, StandardCharsets.US_ASCII);
    }

    private FSM.State<BaseClientState, BaseClientData> failConnectionDueToPubSubException(
            final FatalPubSubException exception,
            final BaseClientData data) {

        final boolean isPubSubTerminated = exception instanceof PubSubTerminatedException;
        if (isPubSubTerminated && stateName() != CONNECTED) {
            // Do not fail connection on abnormal termination of any pubsub actor while not in connected state.
            // Each surviving pubsub actor will send an error. It suffices to react to the first error.
            logger.error(exception.asDittoRuntimeException(), "BugAlert Bug found in Ditto pubsub");
        } else {
            final String description = "The connection experienced a transient failure in the distributed " +
                    "publish/subscribe infrastructure. Failing the connection to try again later.";
            getSelf().tell(
                    // not setting "cause" to put the description literally in the error log
                    ConnectionFailure.internal(null, exception.asDittoRuntimeException(), description),
                    getSelf());
        }
        return stay();
    }

    /**
     * Start a child actor whose name is guaranteed to be different from all other child actors started by this method.
     *
     * @param prefix prefix of the child actor name.
     * @param props props of the child actor.
     * @return the created ActorRef.
     */
    protected final ActorRef startChildActorConflictFree(final CharSequence prefix, final Props props) {
        return childActorNanny.startChildActorConflictFree(prefix, props);
    }

    /**
     * Stops a child actor.
     *
     * @param actor the ActorRef
     */
    protected final void stopChildActor(@Nullable final ActorRef actor) {
        if (null != actor) {
            childActorNanny.stopChildActor(actor);
        }
    }

    /**
     * @return whether this client is consuming at all
     */
    protected final boolean isConsuming() {
        return !connection().getSources().isEmpty();
    }

    /**
     * @return the currently managed Connection
     */
    protected final Connection connection() {
        return stateData().getConnection();
    }

    /**
     * @return the Connection Id
     */
    protected final ConnectionId connectionId() {
        return stateData().getConnectionId();
    }

    /**
     * @return the sources configured for this connection or an empty list if no sources were configured.
     */
    protected final List<Source> getSourcesOrEmptyList() {
        return connection().getSources();
    }

    /**
     * Invoked on each transition {@code from} a {@link BaseClientState} {@code to} another.
     * <p>
     * May be extended to react on special transitions.
     * </p>
     *
     * @param from the previous State
     * @param to the next State
     */
    private void onTransition(final BaseClientState from, final BaseClientState to) {
        logger.debug("Transition: {} -> {}", from, to);
        if (to == CONNECTED) {
            clientGauge.set(1L);
            reconnectTimeoutStrategy.reset();
            publishConnectionOpenedAnnouncement();
        }
        if (to == DISCONNECTED) {
            clientGauge.reset();
        }
        if (to == CONNECTING) {
            clientConnectingGauge.set(1L);
        }
        // do not use else if since we might use goTo(CONNECTING) if in CONNECTING state. This will cause another onTransition.
        if (from == CONNECTING) {
            clientConnectingGauge.reset();
        }
        // cancel our own state timeout if target state is stable
        if (to == CONNECTED || to == DISCONNECTED || to == INITIALIZED) {
            cancelStateTimeout();
        }
        maintainResubscribeTimer(to);
    }

    private void publishConnectionOpenedAnnouncement() {
        if (shouldAnyTargetSendConnectionAnnouncements()) {
            getSelf().tell(ConnectionOpenedAnnouncement.of(connectionId(), Instant.now(), DittoHeaders.empty()),
                    getSelf());
        }
    }

    /*
     * For each volatile state, use the special goTo methods for timer management.
     */
    private FSM.State<BaseClientState, BaseClientData> goToConnecting(final Duration timeout) {
        scheduleStateTimeout(timeout);
        return goTo(CONNECTING);
    }

    private FSM.State<BaseClientState, BaseClientData> goToDisconnecting(final Duration timeout) {
        scheduleStateTimeout(timeout);
        return goTo(DISCONNECTING);
    }

    private FSM.State<BaseClientState, BaseClientData> goToTesting() {
        scheduleStateTimeout(connectivityConfig().getClientConfig().getTestingTimeout());
        return goTo(TESTING);
    }

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inUnknownState() {
        return matchEventEquals(Control.INIT_COMPLETE, (init, baseClientData) -> completeInitialization())
                .eventEquals(Control.SERVICE_UNBIND, this::serviceUnbindWhenDisconnected)
                .event(RuntimeException.class, BaseClientActor::failInitialization)
                .anyEvent((o, baseClientData) -> {
                    stash();
                    return stay();
                });
    }

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inInitializedState() {
        return matchEvent(OpenConnection.class, this::openConnection)
                .event(CloseConnection.class, this::closeConnection)
                .event(CloseConnectionAndShutdown.class, this::closeConnectionAndShutdown)
                .event(TestConnection.class, this::testConnection)
                .eventEquals(Control.SERVICE_UNBIND, this::serviceUnbindWhenWaitingForCommand);
    }

    /**
     * Creates the handler for messages in disconnected state. Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inDisconnectedState() {
        return matchEvent(OpenConnection.class, this::openConnection)
                .event(CloseConnection.class, this::connectionAlreadyClosed)
                .event(TestConnection.class, this::testConnection)
                .eventEquals(Control.SERVICE_UNBIND, this::serviceUnbindWhenDisconnected);
    }

    /**
     * Creates the handler for messages in connecting state. Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectingState() {
        return matchEventEquals(StateTimeout(), (event, data) -> connectionTimedOut(data))
                .event(ConnectionFailure.class, this::connectingConnectionFailed)
                .event(ClientConnected.class, this::clientConnectedInConnectingState)
                .event(InitializationResult.class, this::handleInitializationResult)
                .event(CloseConnection.class, this::closeConnection)
                .event(CloseConnectionAndShutdown.class, this::closeConnectionAndShutdown)
                .event(SshTunnelActor.TunnelStarted.class, this::tunnelStarted)
                .eventEquals(Control.CONNECT_AFTER_TUNNEL_ESTABLISHED, this::connectAfterTunnelStarted)
                .eventEquals(Control.GOTO_CONNECTED_AFTER_INITIALIZATION, this::gotoConnectedAfterInitialization)
                .event(SshTunnelActor.TunnelClosed.class, this::tunnelClosed)
                .event(OpenConnection.class, this::openConnectionInConnectingState)
                .eventEquals(Control.SERVICE_UNBIND, this::serviceUnbindWhenDisconnected);
    }

    /**
     * Creates the handler for messages in connected state. Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectedState() {
        return matchEvent(CloseConnection.class, this::closeConnection)
                .event(CloseConnectionAndShutdown.class, this::closeConnectionAndShutdown)
                .event(SshTunnelActor.TunnelClosed.class, this::tunnelClosed)
                .event(OpenConnection.class, this::connectionAlreadyOpen)
                .event(ConnectionFailure.class, this::connectedConnectionFailed)
                .event(ReportConnectionStatusSuccess.class, this::updateConnectionStatusSuccess)
                .event(ReportConnectionStatusError.class, this::updateConnectionStatusError)
                .eventEquals(Control.RESUBSCRIBE, this::resubscribe)
                .eventEquals(Control.SERVICE_UNBIND, this::serviceUnbindWhenConnected)
                .eventEquals(SEND_DISCONNECT_ANNOUNCEMENT, (event, data) -> sendDisconnectAnnouncement(data));
    }

    private State<BaseClientState, BaseClientData> updateConnectionStatusSuccess(
            final ReportConnectionStatusSuccess reportConnectionStatusSuccess,
            BaseClientData baseClientData) {
        BaseClientData nextClientData = baseClientData.setConnectionStatus(ConnectivityStatus.OPEN)
                .setRecoveryStatus(RecoveryStatus.SUCCEEDED)
                .setConnectionStatusDetails(CONNECTION_STATUS_DETAILS_CONNECTED)
                .setInConnectionStatusSince(Instant.now());
        return stay().using(nextClientData);
    }

    private State<BaseClientState, BaseClientData> updateConnectionStatusError(
            final ReportConnectionStatusError reportConnectionStatus,
            final BaseClientData baseClientData) {
        BaseClientData nextClientData =
                baseClientData.setConnectionStatus(connectivityStatusResolver.resolve(reportConnectionStatus.cause()))
                        .setRecoveryStatus(RecoveryStatus.ONGOING)
                        .setConnectionStatusDetails(
                                ConnectionFailure.determineFailureDescription(null, reportConnectionStatus.cause(),
                                        null))
                        .setInConnectionStatusSince(Instant.now());
        return stay().using(nextClientData);
    }

    @Nullable
    protected abstract ActorRef getPublisherActor();

    private FSM.State<BaseClientState, BaseClientData> publishMappedMessage(final PublishMappedMessage message,
            final BaseClientData data) {

        final var publisherActor = getPublisherActor();
        final var outboundSignal = message.getOutboundSignal();
        if (publisherActor != null) {
            publisherActor.forward(outboundSignal, getContext());
        } else {
            logger.withCorrelationId(outboundSignal.getSource())
                    .error("No publisher actor available, dropping message: {}", message);
        }
        return stay();
    }

    /**
     * Creates the handler for messages in disconnected state. Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inDisconnectingState() {
        return matchEventEquals(StateTimeout(), (event, data) -> connectionTimedOut(data))
                .eventEquals(SEND_DISCONNECT_ANNOUNCEMENT, (event, data) -> sendDisconnectAnnouncement(data))
                .eventEquals(Control.SERVICE_UNBIND, this::serviceUnbindWhenDisconnected)
                .event(Disconnect.class, this::disconnect)
                .event(ConnectionFailure.class, this::connectingConnectionFailed)
                .event(ClientDisconnected.class, this::clientDisconnected);
    }

    /**
     * Creates the handler for messages in testing state. Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inTestingState() {
        return matchEvent(Status.Status.class, (e, d) -> Objects.equals(getSender(), getSelf()),
                (status, data) -> {
                    logger.info("{} status: <{}>", stateName(), status);
                    data.getSessionSenders().forEach(sender ->
                            sender.first().tell(getStatusToReport(status, sender.second()), getSelf()));
                    return stop();
                })
                .event(SshTunnelActor.TunnelStarted.class, this::tunnelStarted)
                .eventEquals(Control.CONNECT_AFTER_TUNNEL_ESTABLISHED, this::testConnectionAfterTunnelStarted)
                .event(TestConnection.class, this::testConnection)
                .event(SshTunnelActor.TunnelClosed.class, this::tunnelClosed)
                .event(ConnectionFailure.class, this::testingConnectionFailed)
                .eventEquals(StateTimeout(), (stats, data) -> {
                    logger.info("test timed out.");
                    data.getSessionSenders().forEach(sender -> {
                        final DittoRuntimeException error = ConnectionFailedException.newBuilder(connectionId())
                                .description(String.format("Failed to open requested connection within <%d> seconds!",
                                        connectivityConfig()
                                                .getClientConfig()
                                                .getTestingTimeout()
                                                .getSeconds()))
                                .dittoHeaders(sender.second())
                                .build();
                        sender.first().tell(new Status.Failure(error), getSelf());
                    });
                    return stop();
                })
                .eventEquals(Control.SERVICE_UNBIND, this::serviceUnbindWhenTesting);
    }

    private State<BaseClientState, BaseClientData> onUnknownEvent(final Object event, final BaseClientData state) {
        Object message = event;
        if (event instanceof Failure failure) {
            message = failure.cause();
        } else if (event instanceof Status.Failure statusFailure) {
            message = statusFailure.cause();
        }

        if (message instanceof Throwable throwable) {
            logger.warning(throwable, "received Exception {} in state {} - status: {} - sender: {}",
                    message,
                    stateName(),
                    state.getConnectionStatus() + ": " + state.getConnectionStatusDetails().orElse(""),
                    getSender());
        } else {
            logger.warning("received unknown/unsupported message {} in state {} - status: {} - sender: {}",
                    message,
                    stateName(),
                    state.getConnectionStatus() + ": " + state.getConnectionStatusDetails().orElse(""),
                    getSender());
        }

        final ActorRef sender = getSender();
        if (!Objects.equals(sender, getSelf()) && !Objects.equals(sender, getContext().system().deadLetters())) {
            sender.tell(unhandledExceptionForSignalInState(event, stateName()), getSelf());
        }

        return stay();
    }

    private static FSM.State<BaseClientState, BaseClientData> failInitialization(final RuntimeException error,
            final BaseClientData data) {

        // throw the error to trigger escalating supervision strategy
        throw error;
    }

    private FSM.State<BaseClientState, BaseClientData> serviceUnbindWhenConnected(final Control serviceUnbind,
            final BaseClientData data) {
        log().info("ServiceUnbind State=<{}> unsubscribing from pubsub and stopping consumers", stateName());
        final var dittoProtocolUnsub = dittoProtocolSub.removeSubscriber(getSelf(), getTargetAuthSubjects());
        final var connectionUnsub = connectionPubSub.unsubscribe(connectionId(), getSelf());
        final var stopConsumers = stopConsuming();
        final var resultFuture = dittoProtocolUnsub.thenCompose(_void -> connectionUnsub)
                .thenCompose(_void -> stopConsumers)
                .handle((result, error) -> Done.getInstance());
        Patterns.pipe(resultFuture, getContext().dispatcher()).to(getSender());
        if (shouldAnyTargetSendConnectionAnnouncements()) {
            getSelf().tell(SEND_DISCONNECT_ANNOUNCEMENT, getSender());
        }
        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> serviceUnbindWhenWaitingForCommand(final Control serviceUnbind,
            final BaseClientData data) {
        log().warning("ServiceUnbind waiting for command. Ongoing command is discarded.");
        getSender().tell(Done.getInstance(), getSelf());
        return stop();
    }

    private FSM.State<BaseClientState, BaseClientData> serviceUnbindWhenDisconnected(final Control serviceUnbind,
            final BaseClientData data) {
        log().info("ServiceUnbind State=<{}>", stateName());
        getSender().tell(Done.getInstance(), getSelf());
        return stop();
    }

    private FSM.State<BaseClientState, BaseClientData> serviceUnbindWhenTesting(final Control serviceUnbind,
            final BaseClientData data) {
        log().info("ServiceUnbind testing. Wait for ongoing test.");
        getSender().tell(Done.getInstance(), getSelf());
        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> closeConnectionAndShutdown(
            final CloseConnectionAndShutdown closeConnectionAndShutdown,
            final BaseClientData data) {

        return closeConnection(CloseConnection.of(connectionId(), DittoHeaders.empty()), data, true);
    }

    private FSM.State<BaseClientState, BaseClientData> closeConnection(final WithDittoHeaders closeConnection,
            final BaseClientData data) {
        return closeConnection(closeConnection, data, false);
    }

    private FSM.State<BaseClientState, BaseClientData> closeConnection(final WithDittoHeaders closeConnection,
            final BaseClientData data, final boolean shutdownAfterDisconnect) {

        final ActorRef sender = getSender();

        final Duration timeoutUntilDisconnectCompletes;
        final ClientConfig clientConfig = connectivityConfig().getClientConfig();
        if (shouldAnyTargetSendConnectionAnnouncements()) {
            final Duration disconnectAnnouncementTimeout = clientConfig.getDisconnectAnnouncementTimeout();
            timeoutUntilDisconnectCompletes =
                    clientConfig.getDisconnectingMaxTimeout().plus(disconnectAnnouncementTimeout);
            getSelf().tell(SEND_DISCONNECT_ANNOUNCEMENT, sender);
            startSingleTimer("startDisconnect", new Disconnect(sender, shutdownAfterDisconnect),
                    disconnectAnnouncementTimeout);
        } else {
            timeoutUntilDisconnectCompletes = clientConfig.getDisconnectingMaxTimeout();
            getSelf().tell(new Disconnect(sender, shutdownAfterDisconnect), sender);
        }

        dittoProtocolSub.removeSubscriber(getSelf());

        return goToDisconnecting(timeoutUntilDisconnectCompletes).using(
                setSession(data, sender, closeConnection.getDittoHeaders())
                        .setDesiredConnectionStatus(ConnectivityStatus.CLOSED)
                        .setConnectionStatusDetails(
                                "cleaning up before closing or deleting connection at " + Instant.now()));
    }

    private State<BaseClientState, BaseClientData> sendDisconnectAnnouncement(final BaseClientData data) {
        final var announcement =
                ConnectionClosedAnnouncement.of(data.getConnectionId(), Instant.now(), DittoHeaders.empty());
        // need to tell the announcement directly to the dispatching actor since the state == DISCONNECTING
        outboundDispatchingActor.tell(announcement, getSender());
        return stay();
    }

    private State<BaseClientState, BaseClientData> disconnect(final Disconnect disconnect, final BaseClientData data) {
        doDisconnectClient(connection(), disconnect.getSender(), disconnect.shutdownAfterDisconnect());
        return stay().using(data.setConnectionStatusDetails("disconnecting connection at " + Instant.now()));
    }

    private FSM.State<BaseClientState, BaseClientData> openConnection(final WithDittoHeaders openConnection,
            final BaseClientData data) {

        final ActorRef sender = getSender();
        final var dittoHeaders = openConnection.getDittoHeaders();

        reconnectTimeoutStrategy.reset();
        final Duration connectingTimeout = connectivityConfig().getClientConfig().getConnectingMinTimeout();

        if (stateData().getSshTunnelState().isEnabled()) {
            logger.info("Connection requires SSH tunnel, starting tunnel.");
            tellTunnelActor(SshTunnelActor.TunnelControl.START_TUNNEL);
            return goToConnecting(connectingTimeout).using(setSession(data, sender, dittoHeaders));
        } else {
            return doOpenConnection(data, sender, dittoHeaders);
        }
    }

    private FSM.State<BaseClientState, BaseClientData> doOpenConnection(final BaseClientData data,
            final ActorRef sender, final DittoHeaders dittoHeaders) {
        final Duration connectingTimeout = connectivityConfig().getClientConfig().getConnectingMinTimeout();
        if (canConnectViaSocket(connection)) {
            doConnectClient(connection, sender);
            return goToConnecting(connectingTimeout).using(setSession(data, sender, dittoHeaders).resetFailureCount());
        } else {
            cleanupResourcesForConnection();
            final DittoRuntimeException error = newConnectionFailedException(dittoHeaders);
            sender.tell(new Status.Failure(error), getSelf());
            return goToConnecting(connectingTimeout)
                    .using(data.resetSession()
                            .resetFailureCount()
                            .setConnectionStatus(ConnectivityStatus.MISCONFIGURED)
                            .setRecoveryStatus(RecoveryStatus.ONGOING)
                            .setConnectionStatusDetails(
                                    ConnectionFailure.determineFailureDescription(Instant.now(), error, null))
                    );
        }
    }

    private FSM.State<BaseClientState, BaseClientData> connectionAlreadyOpen(final OpenConnection openConnection,
            final BaseClientData data) {

        getSender().tell(new Status.Success(CONNECTED), getSelf());
        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> connectionAlreadyClosed(final CloseConnection closeConnection,
            final BaseClientData data) {

        getSender().tell(new Status.Success(DISCONNECTED), getSelf());
        return stay();
    }

    private void reconnect() {
        logger.debug("Trying to reconnect");
        connectionLogger.success("Trying to reconnect");
        if (canConnectViaSocket(connection)) {
            doConnectClient(connection, null);
        } else {
            logger.info("Socket is closed, scheduling a reconnect");
            cleanupResourcesForConnection();
            throw newConnectionFailedException(DittoHeaders.empty());
        }
    }

    private FSM.State<BaseClientState, BaseClientData> testConnection(final TestConnection testConnection,
            final BaseClientData data) {

        final ActorRef self = getSelf();
        final ActorRef sender = getSender();
        final var connectionToBeTested = testConnection.getConnection();

        if (stateData().getSshTunnelState().isEnabled() && !stateData().getSshTunnelState().isEstablished()) {
            logger.info("Connection requires SSH tunnel, starting tunnel.");
            tellTunnelActor(SshTunnelActor.TunnelControl.START_TUNNEL);
        } else if (!canConnectViaSocket(connectionToBeTested)) {
            final var connectionFailedException =
                    newConnectionFailedException(testConnection.getDittoHeaders());
            final Status.Status failure = new Status.Failure(connectionFailedException);
            getSelf().tell(failure, self);
        } else {
            final CompletionStage<Status.Status> connectionStatusStage = doTestConnection(testConnection);
            final CompletionStage<Status.Status> mappingStatusStage = testMessageMappingProcessor();

            connectionStatusStage.toCompletableFuture()
                    .thenCombine(mappingStatusStage, (connectionStatus, mappingStatus) -> {
                        if (connectionStatus instanceof Status.Success &&
                                mappingStatus instanceof Status.Success) {
                            return new Status.Success("successfully connected + initialized mapper");
                        } else if (connectionStatus instanceof Status.Failure) {
                            return connectionStatus;
                        } else {
                            return mappingStatus;
                        }
                    })
                    .thenAccept(testStatus -> self.tell(testStatus, self))
                    .exceptionally(error -> {
                        self.tell(new Status.Failure(error), self);
                        return null;
                    });
        }

        return goToTesting().using(setSession(data, sender, testConnection.getDittoHeaders())
                .setConnection(connectionToBeTested)
                .setConnectionStatusDetails("Testing connection since " + Instant.now()));
    }

    private State<BaseClientState, BaseClientData> testingConnectionFailed(final ConnectionFailure event,
            final BaseClientData data) {
        logger.info("{} failed: <{}>", stateName(), event);
        cleanupResourcesForConnection();
        data.getSessionSenders().forEach(sender ->
                sender.first().tell(getStatusToReport(event.getFailure(), sender.second()), getSelf()));
        return stop();
    }

    private FSM.State<BaseClientState, BaseClientData> connectionTimedOut(final BaseClientData data) {
        data.getSessionSenders().forEach(sender -> {
            final DittoRuntimeException error = ConnectionFailedException.newBuilder(connectionId())
                    .description("Connection attempt timed out.")
                    .dittoHeaders(sender.second())
                    .build();
            sender.first().tell(new Status.Failure(error), getSelf());
        });
        cleanupResourcesForConnection();
        cleanupFurtherResourcesOnConnectionTimeout(stateName());

        final String timeoutMessage = "Connection timed out at " + Instant.now() + " while " + stateName() + ".";

        if (ConnectivityStatus.OPEN.equals(data.getDesiredConnectionStatus())) {
            if (reconnectTimeoutStrategy.canReconnect()) {
                if (stateData().getSshTunnelState().isEnabled()) {
                    logger.info("Connection requires SSH tunnel, start tunnel.");
                    tunnelActor.tell(SshTunnelActor.TunnelControl.START_TUNNEL, getSender());
                } else {
                    try {
                        reconnect();
                    } catch (final ConnectionFailedException e) {
                        return goToConnecting(reconnectTimeoutStrategy.getNextTimeout())
                                .using(data.resetSession()
                                        .resetFailureCount()
                                        .setConnectionStatus(ConnectivityStatus.MISCONFIGURED)
                                        .setRecoveryStatus(RecoveryStatus.ONGOING)
                                        .setConnectionStatusDetails(
                                                ConnectionFailure.determineFailureDescription(Instant.now(), e, null)));
                    }
                }
                return goToConnecting(reconnectTimeoutStrategy.getNextTimeout())
                        .using(data.resetSession()  // don't set the state, preserve the old one (e.g. MISCONFIGURED)
                                .resetFailureCount()
                                .setConnectionStatusDetails(timeoutMessage + " Will try to reconnect."));
            } else {
                connectionLogger.failure(
                        "Connection timed out. Reached maximum tries and thus will no longer try to reconnect");
                logger.info(
                        "Connection <{}> - connection timed out - " +
                                "reached maximum retries for reconnecting and thus will no longer try to reconnect",
                        connectionId());

                return goTo(INITIALIZED)
                        .using(data.resetSession()
                                .resetFailureCount()
                                // don't set the state, preserve the old one (e.g. MISCONFIGURED)
                                // Preserve old status details
                                .setRecoveryStatus(RecoveryStatus.BACK_OFF_LIMIT_REACHED)
                                .setConnectionStatusDetails(data.getConnectionStatusDetails().orElse(timeoutMessage) +
                                        " Reached maximum retries and thus will not try to reconnect any longer.")
                        );
            }
        } else {
            // this is unexpected, as a desired "closed" connection should not get this far (trying to connect)
            connectionLogger.failure("Connection timed out, however desired state was {0}",
                    data.getDesiredConnectionStatus());
            return goTo(INITIALIZED)
                    .using(data.resetSession()
                            .resetFailureCount()
                            .setConnectionStatus(ConnectivityStatus.FAILED)
                            .setRecoveryStatus(RecoveryStatus.UNKNOWN)
                            .setConnectionStatusDetails(timeoutMessage + " Desired state was: " +
                                    data.getDesiredConnectionStatus())
                    );
        }
    }

    private State<BaseClientState, BaseClientData> tunnelStarted(final SshTunnelActor.TunnelStarted tunnelStarted,
            final BaseClientData data) {
        logger.info("SSH tunnel established. Connecting via tunnel at localhost: {}", tunnelStarted.getLocalPort());
        final SshTunnelState established = data.getSshTunnelState().established(tunnelStarted.getLocalPort());
        getSelf().tell(Control.CONNECT_AFTER_TUNNEL_ESTABLISHED, getSelf());
        return stay().using(data.setSshTunnelState(established));
    }

    private State<BaseClientState, BaseClientData> connectAfterTunnelStarted(final Control control,
            final BaseClientData data) {

        if (data.getSessionSenders().isEmpty()) {
            logger.info("Reconnecting after ssh tunnel was established.");
            try {
                reconnect();
                return stay();
            } catch (final ConnectionFailedException e) {
                return goToConnecting(reconnectTimeoutStrategy.getNextTimeout())
                        .using(data.setConnectionStatus(ConnectivityStatus.MISCONFIGURED)
                                .setRecoveryStatus(RecoveryStatus.ONGOING)
                                .setConnectionStatusDetails(
                                        ConnectionFailure.determineFailureDescription(Instant.now(), e, null))
                                .resetSession());
            }
        } else {
            logger.info("Connecting initially after tunnel was established.");
            final ActorRef sender = data.getSessionSenders().get(0).first();
            final DittoHeaders dittoHeaders = data.getSessionSenders().get(0).second();
            return doOpenConnection(data, sender, dittoHeaders);
        }
    }

    private State<BaseClientState, BaseClientData> testConnectionAfterTunnelStarted(final Control control,
            final BaseClientData data) {
        logger.info("Testing connection after tunnel was established.");

        data.getSessionSenders().forEach(p -> {
            final ActorRef sender = p.first();
            final DittoHeaders dittoHeaders = p.second();
            final TestConnection testConnection = TestConnection.of(data.getConnection(), dittoHeaders);
            getSelf().tell(testConnection, sender);
        });

        return stay().using(data);
    }

    private State<BaseClientState, BaseClientData> tunnelClosed(final SshTunnelActor.TunnelClosed tunnelClosed,
            final BaseClientData data) {
        logger.info("SSH tunnel closed: {}", tunnelClosed.getMessage());
        final var failure =
                ConnectionFailure.userRelated(null, tunnelClosed.getError(), tunnelClosed.getMessage());
        getSelf().tell(failure, getSelf());
        final SshTunnelState closedState = data.getSshTunnelState().failed(tunnelClosed.getError());
        return stay().using(data.setSshTunnelState(closedState));
    }

    protected final SshTunnelState getSshTunnelState() {
        return stateData().getSshTunnelState();
    }

    private State<BaseClientState, BaseClientData> openConnectionInConnectingState(
            final WithDittoHeaders openConnection,
            final BaseClientData data) {

        final ActorRef origin = getSender();
        if (!getSelf().equals(origin) && !getContext().getSystem().deadLetters().equals(origin)) {
            // add this sender to list of actors to respond to once connection succeeds.
            return stay().using(data.addSessionSender(origin, openConnection.getDittoHeaders()));
        } else {
            return stay();
        }
    }

    /**
     * Handle the event ClientConnected in state CONNECTING.
     * By default, allocate resources, and then start publisher and consumer actors.
     */
    protected State<BaseClientState, BaseClientData> clientConnectedInConnectingState(
            final ClientConnected clientConnected,
            final BaseClientData data) {

        allocateResourcesOnConnection(clientConnected);
        Patterns.pipe(startPublisherAndConsumerActors(clientConnected), getContext().getDispatcher())
                .to(getSelf());
        return stay().using(data);
    }

    /**
     * Start publisher and consumer actors.
     * <p>
     * NOT thread-safe! Only invoke as a part of message handling.
     *
     * @return Future that completes with the result of starting publisher and consumer actors.
     */
    protected CompletionStage<InitializationResult> startPublisherAndConsumerActors(
            @Nullable final ClientConnected clientConnected) {

        logger.info("Starting publisher and consumers.");

        // All these method calls are NOT thread-safe. Do NOT inline.
        final CompletionStage<Status.Status> publisherReady = startPublisherActor();
        final CompletionStage<Status.Status> consumersReady = startConsumerActors(clientConnected);

        return publisherReady
                .thenCompose(unused -> consumersReady)
                .thenCompose(unused -> subscribeAndDeclareAcknowledgementLabels(dryRun, false))
                .thenApply(unused -> InitializationResult.success())
                .exceptionally(InitializationResult::failed);
    }

    private State<BaseClientState, BaseClientData> handleInitializationResult(
            final InitializationResult initializationResult, final BaseClientData data) {
        if (initializationResult.isSuccess()) {
            getSelf().tell(Control.GOTO_CONNECTED_AFTER_INITIALIZATION, ActorRef.noSender());
        } else {
            logger.info("Initialization of consumers, publisher and subscriptions failed: {}. Staying in CONNECTING " +
                    "state to continue with connection recovery after backoff.", initializationResult.getFailure());
            getSelf().tell(initializationResult.getFailure(), ActorRef.noSender());
        }
        return stay();
    }

    private State<BaseClientState, BaseClientData> gotoConnectedAfterInitialization(final Control message,
            final BaseClientData data) {
        if (data.getFailureCount() == 0) {
            logger.info("Initialization of consumers, publisher and subscriptions successful, going to CONNECTED.");
            connectionLogger.success("Connection successful");
            data.getSessionSenders().forEach(origin -> origin.first().tell(new Status.Success(CONNECTED), getSelf()));
            return goTo(CONNECTED).using(data.resetSession()
                    .resetFailureCount()
                    .setConnectionStatus(ConnectivityStatus.OPEN)
                    .setRecoveryStatus(RecoveryStatus.SUCCEEDED)
                    .setConnectionStatusDetails(CONNECTION_STATUS_DETAILS_CONNECTED)
                    .setInConnectionStatusSince(Instant.now())
            );
        } else {
            logger.info("Initialization of consumers, publisher and subscriptions successful, but failures were " +
                    "received meanwhile. Staying in CONNECTING state to continue with connection recovery after backoff.");
            return stay();
        }
    }

    /**
     * Subclasses should start their publisher actor in the implementation of this method and report success or
     * failure in the returned {@link CompletionStage}. {@code BaseClientActor} calls this method when the client is
     * connected.
     * <p>
     * NOT thread-safe! Only invoke as a part of message handling.
     *
     * @return a completion stage that completes either successfully when the publisher actor was started
     * successfully or exceptionally when the publisher actor could not be started successfully
     */
    protected abstract CompletionStage<Status.Status> startPublisherActor();

    /**
     * Subclasses should start their consumer actors in the implementation of this method and report success or
     * failure in the returned {@link CompletionStage}.
     * <p>
     * NOT thread-safe if it starts any actors! Only invoke as a part of message handling.
     *
     * @param clientConnected message indicating that the client has successfully been connected to the external system,
     * or null if consumer actors are to be started before the client becomes connected.
     * @return a completion stage that completes either successfully when all consumers were started
     * successfully or exceptionally when starting a consumer actor failed
     */
    protected CompletionStage<Status.Status> startConsumerActors(@Nullable final ClientConnected clientConnected) {
        return CompletableFuture.completedFuture(new Status.Success(Done.getInstance()));
    }

    private State<BaseClientState, BaseClientData> clientDisconnected(final ClientDisconnected event,
            final BaseClientData data) {

        connectionLogger.success("Disconnected successfully");

        cleanupResourcesForConnection();
        tellTunnelActor(SshTunnelActor.TunnelControl.STOP_TUNNEL);
        data.getSessionSenders()
                .forEach(sender -> sender.first().tell(new Status.Success(DISCONNECTED), getSelf()));

        final BaseClientData nextStateData = data.resetSession()
                .setConnectionStatus(ConnectivityStatus.CLOSED)
                .setRecoveryStatus(RecoveryStatus.UNKNOWN)
                .setConnectionStatusDetails("Disconnected at " + Instant.now());

        if (event.shutdownAfterDisconnected()) {
            return stop(Normal(), nextStateData);
        } else {
            return goTo(DISCONNECTED).using(nextStateData);
        }
    }

    private void tellTunnelActor(final SshTunnelActor.TunnelControl control) {
        if (tunnelActor != null) {
            tunnelActor.tell(control, getSelf());
        } else {
            logger.debug("Tunnel actor not started.");
        }
    }

    private State<BaseClientState, BaseClientData> connectingConnectionFailed(final ConnectionFailure event,
            final BaseClientData data) {

        logger.info("{} failed: <{}>", stateName(), event);

        cleanupResourcesForConnection();
        data.getSessionSenders().forEach(sender ->
                sender.first().tell(getStatusToReport(event.getFailure(), sender.second()), getSelf()));

        return backoffAfterFailure(event, data.increaseFailureCount());
    }

    private State<BaseClientState, BaseClientData> connectedConnectionFailed(final ConnectionFailure event,
            final BaseClientData data) {

        // do not bother to disconnect gracefully - the other end of the connection is probably dead
        cleanupResourcesForConnection();
        cleanupFurtherResourcesOnConnectionTimeout(stateName());

        return backoffAfterFailure(event, data);
    }

    /**
     * Attempt to reconnect after a failure. Ensure resources were cleaned up before calling it.
     * Enter state CONNECTING without actually attempting reconnection.
     * Actual reconnection happens after the state times out.
     *
     * @param event the failure event
     * @param data the current client data
     */
    private State<BaseClientState, BaseClientData> backoffAfterFailure(final ConnectionFailure event,
            final BaseClientData data) {

        dittoProtocolSub.removeSubscriber(getSelf());
        if (ConnectivityStatus.OPEN.equals(data.getDesiredConnectionStatus())) {
            if (reconnectTimeoutStrategy.canReconnect()) {
                if (data.getFailureCount() > 1) {
                    connectionLogger.failure(
                            "Received {0} subsequent failures during backoff: {1}. Reconnect after backoff was " +
                                    "already triggered", data.getFailureCount(), event.getFailureDescription());
                    logger.info("Received {} subsequent failures during backoff: {}. Reconnect was already triggered",
                            data.getFailureCount(), event);
                    return stay();
                } else {
                    final Duration nextBackoff = reconnectTimeoutStrategy.getNextBackoff();
                    final var errorMessage =
                            String.format("Connection failed due to: {0}. Will reconnect after %s", nextBackoff);
                    connectionLogger.failure(errorMessage, event.getFailureDescription());
                    final ConnectivityStatus resolvedStatus = connectivityStatusResolver.resolve(event);
                    logger.info("Connection failed: {}. Reconnect after: {}. Resolved status: {}. " +
                            "Going to 'CONNECTING'", event, nextBackoff, resolvedStatus);
                    return goToConnecting(nextBackoff).using(data.resetSession()
                            .setConnectionStatus(resolvedStatus)
                            .setRecoveryStatus(RecoveryStatus.ONGOING)
                            .setConnectionStatusDetails(event.getFailureDescription())
                    );
                }
            } else {
                connectionLogger.failure(
                        "Connection failed due to: {0}. Reached maximum tries and thus will no longer try to reconnect",
                        event.getFailureDescription());
                logger.info(
                        "Connection <{}> - backoff after failure - " +
                                "reached maximum retries for reconnecting and thus will no longer try to reconnect",
                        connectionId());

                // stay in INITIALIZED state until re-opened manually
                return goTo(INITIALIZED)
                        .using(data.resetSession()
                                .resetFailureCount()
                                .setConnectionStatus(connectivityStatusResolver.resolve(event))
                                .setRecoveryStatus(RecoveryStatus.BACK_OFF_LIMIT_REACHED)
                                .setConnectionStatusDetails(event.getFailureDescription()
                                        + " Reached maximum retries after backing off after failure and thus will " +
                                        "not try to reconnect any longer.")
                        );
            }
        }

        connectionLogger.failure("Connection failed due to: {0}", event.getFailureDescription());
        return goTo(INITIALIZED)
                .using(data.resetSession()
                        .setConnectionStatus(connectivityStatusResolver.resolve(event))
                        .setRecoveryStatus(RecoveryStatus.UNKNOWN)
                        .setConnectionStatusDetails(event.getFailureDescription())
                );
    }

    private FSM.State<BaseClientState, BaseClientData> retrieveConnectionStatus(final RetrieveConnectionStatus command,
            final BaseClientData data) {

        final ActorRef sender = getSender();
        logger.withCorrelationId(command)
                .debug("Received RetrieveConnectionStatus for connection <{}> message from <{}>." +
                                " Forwarding to consumers and publishers.", command.getEntityId(),
                        sender);

        // only one PublisherActor is started for all targets (if targets are present)
        final int numberOfProducers = connection.getTargets().isEmpty() ? 0 : 1;
        final int numberOfConsumers = determineNumberOfConsumers();
        int expectedNumberOfChildren = numberOfProducers + numberOfConsumers;
        if (getSshTunnelState().isEnabled()) {
            expectedNumberOfChildren++;
        }
        final Set<Pattern> noAddressReportingChildNamePatterns = getExcludedAddressReportingChildNamePatterns();
        final List<ActorRef> childrenToAsk = StreamSupport.stream(getContext().getChildren().spliterator(), false)
                .filter(child -> noAddressReportingChildNamePatterns.stream()
                        .noneMatch(p -> p.matcher(child.path().name()).matches()))
                .toList();
        final ConnectivityStatus clientConnectionStatus = data.getConnectionStatus();
        if (childrenToAsk.size() != expectedNumberOfChildren) {
            if (clientConnectionStatus.isFailure() || clientConnectionStatus == ConnectivityStatus.UNKNOWN) {
                logger.withCorrelationId(command)
                        .info("Responding early with static 'CLOSED' ResourceStatus for all sub-sources and " +
                                "-targets and SSH tunnel, because some children could not be started, due to a " +
                                "live status <{}> in the client actor.", clientConnectionStatus);
                getSourceAddresses()
                        .map(sourceAddress -> ConnectivityModelFactory.newSourceStatus(getInstanceIdentifier(),
                                ConnectivityStatus.CLOSED,
                                sourceAddress,
                                CLOSED_BECAUSE_OF_UNKNOWN_FAILURE_MISCONFIGURATION_STATUS_IN_CLIENT))
                        .forEach(resourceStatus -> sender.tell(resourceStatus, ActorRef.noSender()));
                connection.getTargets().stream()
                        .map(Target::getAddress)
                        .map(targetAddress -> ConnectivityModelFactory.newTargetStatus(getInstanceIdentifier(),
                                ConnectivityStatus.CLOSED,
                                targetAddress,
                                CLOSED_BECAUSE_OF_UNKNOWN_FAILURE_MISCONFIGURATION_STATUS_IN_CLIENT))
                        .forEach(resourceStatus -> sender.tell(resourceStatus, ActorRef.noSender()));
                connection.getSshTunnel().ifPresent(sshTunnel -> sender.tell(
                        ConnectivityModelFactory.newSshTunnelStatus(getInstanceIdentifier(),
                                ConnectivityStatus.CLOSED,
                                CLOSED_BECAUSE_OF_UNKNOWN_FAILURE_MISCONFIGURATION_STATUS_IN_CLIENT,
                                Instant.now()), ActorRef.noSender())
                );
            } else {
                logger.withCorrelationId(command)
                        .warning("NOT responding early with ResourceStatus for all sub-sources and " +
                                "-targets and SSH tunnel, having a live status <{}> in the client actor which likely " +
                                "will cause timeout 'failures' in some of the resources.", clientConnectionStatus);
                retrieveAddressStatusFromChildren(command, sender, childrenToAsk);
            }
        } else {
            retrieveAddressStatusFromChildren(command, sender, childrenToAsk);
        }
        final ResourceStatus clientStatus =
                ConnectivityModelFactory.newClientStatus(getInstanceIdentifier(),
                        clientConnectionStatus,
                        data.getRecoveryStatus(),
                        data.getConnectionStatusDetails().orElse(""),
                        getInConnectionStatusSince());
        sender.tell(clientStatus, getSelf());

        return stay();
    }

    /**
     * Determine the number of consumers.
     *
     * @return the number of consumers.
     */
    protected int determineNumberOfConsumers() {
        return connection.getSources()
                .stream()
                .mapToInt(source -> source.getConsumerCount() * source.getAddresses().size())
                .sum();
    }

    /**
     * Get the source addresses as stream of strings.
     *
     * @return the stream of source addresses.
     */
    protected Stream<String> getSourceAddresses() {
        return connection.getSources().stream()
                .map(Source::getAddresses)
                .flatMap(Collection::stream);
    }

    private void retrieveAddressStatusFromChildren(final RetrieveConnectionStatus command, final ActorRef sender,
            final List<ActorRef> childrenToAsk) {
        childrenToAsk.forEach(child -> {
            logger.withCorrelationId(command)
                    .debug("Forwarding RetrieveAddressStatus to child <{}>.", child.path());
            child.tell(RetrieveAddressStatus.getInstance(), sender);
        });
    }

    /**
     * Set of regex patterns including client actor names which should not receive {@link RetrieveAddressStatus}
     * messages in order to retrieve the current live status.
     * May be overwritten by clients to add more actor name patterns which are started as children of the client actor.
     *
     * @return the set of patterns to exclude.
     */
    protected Set<Pattern> getExcludedAddressReportingChildNamePatterns() {
        return Set.of(EXCLUDED_ADDRESS_REPORTING_CHILD_NAME_PATTERN);
    }

    private static String getInstanceIdentifier() {
        return InstanceIdentifierSupplier.getInstance().get();
    }

    private FSM.State<BaseClientState, BaseClientData> retrieveConnectionMetrics(
            final RetrieveConnectionMetrics command) {

        logger.withCorrelationId(command)
                .debug("Received RetrieveConnectionMetrics message for connection <{}>. Gathering metrics.",
                        command.getEntityId());
        final var dittoHeaders = command.getDittoHeaders();

        final var sourceMetrics = connectionCounterRegistry.aggregateSourceMetrics(connectionId());
        final var targetMetrics = connectionCounterRegistry.aggregateTargetMetrics(connectionId());

        final var connectionMetrics =
                connectionCounterRegistry.aggregateConnectionMetrics(sourceMetrics, targetMetrics);

        final var retrieveConnectionMetricsResponse =
                RetrieveConnectionMetricsResponse.getBuilder(connectionId(), dittoHeaders)
                        .connectionMetrics(connectionMetrics)
                        .sourceMetrics(sourceMetrics)
                        .targetMetrics(targetMetrics)
                        .build();

        getSender().tell(retrieveConnectionMetricsResponse, getSelf());
        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> resetConnectionMetrics(final ResetConnectionMetrics command,
            final BaseClientData data) {

        logger.withCorrelationId(command)
                .debug("Received ResetConnectionMetrics message for connection <{}>. Resetting metrics.",
                        command.getEntityId());
        connectionCounterRegistry.resetForConnection(data.getConnection());

        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> enableConnectionLogs(final EnableConnectionLogs command) {
        final var connectionId = command.getEntityId();
        logger.withCorrelationId(command)
                .debug("Received EnableConnectionLogs message for connection <{}>. Enabling logs.", connectionId);

        connectionLoggerRegistry.unmuteForConnection(connectionId);

        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> checkLoggingActive(final CheckConnectionLogsActive command) {
        final var connectionId = command.getEntityId();
        logger.withCorrelationId(command)
                .debug("Received checkLoggingActive message for connection <{}>." +
                        " Checking if logging for connection is expired.", connectionId);

        if (ConnectionLoggerRegistry.isLoggingExpired(connectionId, command.getTimestamp())) {
            ConnectionLoggerRegistry.muteForConnection(connectionId);
            getSender().tell(LoggingExpired.of(connectionId), ActorRef.noSender());
        }

        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> retrieveConnectionLogs(final RetrieveConnectionLogs command) {
        logger.withCorrelationId(command)
                .debug("Received RetrieveConnectionLogs message for connection <{}>. Gathering metrics.",
                        command.getEntityId());

        final ConnectionLoggerRegistry.ConnectionLogs connectionLogs =
                connectionLoggerRegistry.aggregateLogs(connectionId());

        getSender().tell(RetrieveConnectionLogsResponse.of(connectionId(), connectionLogs.getLogs(),
                        connectionLogs.getEnabledSince(), connectionLogs.getEnabledUntil(), command.getDittoHeaders()),
                getSelf());

        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> resetConnectionLogs(final ResetConnectionLogs command,
            final BaseClientData data) {

        logger.debug("Received ResetConnectionLogs message, resetting logs.");

        connectionLoggerRegistry.resetForConnection(data.getConnection());

        connectionLoggerRegistry.forConnection(data.getConnectionId())
                .success(InfoProviderFactory.forSignal(command), "Successfully reset the logs.");

        return stay();
    }

    private ConnectionFailedException newConnectionFailedException(final DittoHeaders dittoHeaders) {
        return ConnectionFailedException
                .newBuilder(connection.getId())
                .dittoHeaders(dittoHeaders)
                .description("Could not establish a connection on '" +
                        connection.getHostname() + ":" + connection.getPort() + "'. Make sure the " +
                        "endpoint is reachable and that no firewall prevents the connection.")
                .build();
    }

    private DittoRuntimeException unhandledExceptionForSignalInState(final Object signal,
            final BaseClientState state) {
        final DittoHeaders headers = signal instanceof WithDittoHeaders withDittoHeaders
                ? withDittoHeaders.getDittoHeaders()
                : DittoHeaders.empty();
        switch (state) {
            case CONNECTING:
            case DISCONNECTING:
                return ConnectionSignalIllegalException.newBuilder(connectionId())
                        .operationName(state.name().toLowerCase())
                        .timeout(connectivityConfig().getClientConfig().getConnectingMinTimeout())
                        .dittoHeaders(headers)
                        .build();
            default:
                final String signalType = signal instanceof Signal
                        ? ((WithType) signal).getType()
                        : "unknown"; // no need to disclose Java class of signal to clients
                return ConnectionSignalIllegalException.newBuilder(connectionId())
                        .illegalSignalForState(signalType, state.name().toLowerCase())
                        .dittoHeaders(headers)
                        .build();
        }
    }

    protected boolean canConnectViaSocket(final Connection connection) {
        final var tunnelState = stateData().getSshTunnelState();
        if (tunnelState.isEnabled()) {
            final var uri = connection.getSshTunnel().map(SshTunnel::getUri).map(URI::create).orElseThrow();
            final String sshHost = uri.getHost();
            final int sshPort = uri.getPort();
            final int localTunnelPort = tunnelState.getLocalPort();
            logger.info("Check connection to {}:{} and {}:{}.", sshHost, sshPort, "localhost", localTunnelPort);
            return checkHostAndPortForAvailability(sshHost, sshPort)
                    && checkHostAndPortForAvailability("localhost", localTunnelPort);
        } else {
            return checkHostAndPortForAvailability(connection.getHostname(), connection.getPort());
        }
    }

    private boolean checkHostAndPortForAvailability(final String host, final int port) {
        try (final Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), SOCKET_CHECK_TIMEOUT_MS);
            return true;
        } catch (final IOException | IllegalArgumentException ex) {
            connectionLogger.failure("Socket could not be opened for {0}:{1,number,#} due to {2}", host, port,
                    ex.getMessage());

            logger.warning("Socket could not be opened for <{}:{}> due to {}: {}", host, port,
                    ex.getClass().getCanonicalName(), ex.getMessage());
        }
        return false;
    }

    private FSM.State<BaseClientState, BaseClientData> handleSignal(final WithDittoHeaders signal,
            final BaseClientData data) {
        if (stateName() == CONNECTED) {
            outboundDispatchingActor.tell(signal, getSender());
        } else {
            logger.withCorrelationId(signal)
                    .debug("Client state <{}> is not CONNECTED; dropping <{}>", stateName(), signal);
        }
        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> handleDispatchedInboundSignal(final InboundSignal inboundSignal,
            final BaseClientData data) {
        // another actor dispatched this inbound signal to this actor. forward to outboundDispatchingActor.
        outboundDispatchingActor.forward(inboundSignal, getContext());
        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> handleInboundSignal(final InboundSignal inboundSignal,
            final BaseClientData data) {
        // dispatch signal to other client actors according to entity ID
        final Signal<?> signal = inboundSignal.getSignal();
        if (signal instanceof WithSubscriptionId<?>) {
            dispatchSearchCommand((WithSubscriptionId<?>) signal);
        } else if (signal instanceof WithStreamingSubscriptionId<?>) {
            dispatchStreamingSubscriptionCommand((WithStreamingSubscriptionId<?>) signal);
        } else {
            final var entityId = tryExtractEntityId(signal).orElseThrow();
            connectionPubSub.publishSignal(inboundSignal.asDispatched(), connectionId(), entityId, getSender());
        }
        return stay();
    }

    private static Optional<EntityId> tryExtractEntityId(final Signal<?> signal) {
        if (signal instanceof final WithEntityId withEntityId) {
            return Optional.of(withEntityId.getEntityId());
        } else {
            return Optional.empty();
        }
    }

    private void dispatchSearchCommand(final WithSubscriptionId<?> searchCommand) {
        final String subscriptionId = searchCommand.getSubscriptionId();
        if (subscriptionId.length() >= subscriptionIdPrefixLength) {
            final var prefix = subscriptionId.substring(0, subscriptionIdPrefixLength);
            connectionPubSub.publishSignal(searchCommand, connectionId(), prefix, ActorRef.noSender());
        } else {
            // command is invalid or outdated, dropping.
            logger.withCorrelationId(searchCommand)
                    .info("Dropping search command with invalid subscription ID: <{}>", searchCommand);
            connectionLogger.failure(InfoProviderFactory.forSignal(searchCommand),
                    "Dropping search command with invalid subscription ID: " +
                            searchCommand.getSubscriptionId());
        }
    }

    private void dispatchStreamingSubscriptionCommand(
            final WithStreamingSubscriptionId<?> streamingSubscriptionCommand) {

        final String subscriptionId = streamingSubscriptionCommand.getSubscriptionId();
        if (subscriptionId.length() > subscriptionIdPrefixLength) {
            final var prefix = subscriptionId.substring(0, subscriptionIdPrefixLength);
            connectionPubSub.publishSignal(streamingSubscriptionCommand, connectionId(), prefix, ActorRef.noSender());
        } else {
            // command is invalid or outdated, dropping.
            logger.withCorrelationId(streamingSubscriptionCommand)
                    .info("Dropping streaming subscription command with invalid subscription ID: <{}>",
                            streamingSubscriptionCommand);
            connectionLogger.failure(InfoProviderFactory.forSignal(streamingSubscriptionCommand),
                    "Dropping streaming subscription command with invalid subscription ID: " +
                            streamingSubscriptionCommand.getSubscriptionId());
        }
    }

    private Instant getInConnectionStatusSince() {
        return stateData().getInConnectionStatusSince();
    }

    private CompletionStage<Status.Status> testMessageMappingProcessor() {
        try {
            return tryToConfigureMessageMappingProcessor();
        } catch (final DittoRuntimeException dre) {
            final String logMessage = MessageFormat.format(
                    "Got DittoRuntimeException during initialization of MessageMappingProcessor: {0} {1} - desc: {2}",
                    dre.getClass().getSimpleName(), dre.getMessage(), dre.getDescription().orElse(""));
            connectionLogger.failure(logMessage);
            logger.withCorrelationId(dre).info(logMessage);
            return CompletableFuture.completedFuture(new Status.Failure(dre));
        }
    }

    private CompletionStage<Status.Status> tryToConfigureMessageMappingProcessor() {
        final ActorSystem actorSystem = getContext().getSystem();

        // this one throws DittoRuntimeExceptions when the mapper could not be configured
        InboundMappingProcessor.of(connection, connectivityConfig, actorSystem, protocolAdapter, logger);
        OutboundMappingProcessor.of(connection, connectivityConfig, actorSystem, protocolAdapter, logger);
        return CompletableFuture.completedFuture(new Status.Success("mapping"));
    }

    private Pair<ActorRef, ActorRef> startOutboundActors(final ProtocolAdapter protocolAdapter) {
        final OutboundMappingSettings settings;
        final List<OutboundMappingProcessor> outboundMappingProcessors;
        final int processorPoolSize = connection.getProcessorPoolSize();
        try {
            // this one throws DittoRuntimeExceptions when the mapper could not be configured
            settings = OutboundMappingSettings.of(connection, connectivityConfig, getContext().getSystem(),
                    commandForwarderActorSelection, protocolAdapter, logger);
            outboundMappingProcessors = IntStream.range(0, processorPoolSize)
                    .mapToObj(i -> OutboundMappingProcessor.of(settings))
                    .toList();
        } catch (final DittoRuntimeException dre) {
            connectionLogger.failure("Failed to start message mapping processor due to: {0}", dre.getMessage());
            logger.info("Got DittoRuntimeException during initialization of MessageMappingProcessor: {} {} - desc: {}",
                    dre.getClass().getSimpleName(), dre.getMessage(), dre.getDescription().orElse(""));
            throw dre;
        }

        logger.debug("Starting mapping processor actors with pool size of <{}>.", processorPoolSize);
        final Props outboundMappingProcessorActorProps =
                OutboundMappingProcessorActor.props(getSelf(), outboundMappingProcessors, connection,
                        connectivityConfig, processorPoolSize);

        final ActorRef processorActor = childActorNanny.startChildActor(OutboundMappingProcessorActor.ACTOR_NAME,
                outboundMappingProcessorActorProps);

        final Props outboundDispatchingProcessorActorProps = OutboundDispatchingActor.props(
                settings, processorActor);
        final ActorRef dispatchingActor =
                getContext().actorOf(outboundDispatchingProcessorActorProps, OutboundDispatchingActor.ACTOR_NAME);

        return Pair.create(dispatchingActor, processorActor);
    }

    /**
     * Gets the {@link InboundDispatchingSink} responsible for signal de-multiplexing and acknowledgement
     * aggregation.
     *
     * @return the ref to the started {@link InboundDispatchingSink}
     * @throws DittoRuntimeException when mapping processor could not get started.
     */
    private Sink<Object, NotUsed> getInboundDispatchingSink(final ActorRef outboundMappingProcessorActor) {
        return InboundDispatchingSink.createSink(connection,
                protocolAdapter.headerTranslator(),
                commandForwarderActorSelection,
                connectionActor,
                outboundMappingProcessorActor,
                getSelf(),
                getContext(),
                connectivityConfig,
                dittoHeadersValidator,
                getResponseValidationFailureConsumer());
    }

    private Consumer<MatchingValidationResult.Failure> getResponseValidationFailureConsumer() {
        return failure -> connectionLogger.logEntry(
                LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(failure.getCommand(),
                        failure.getCommandResponse(),
                        failure.getDetailMessage())
        );
    }

    /**
     * Gets the {@link InboundMappingSink} responsible for payload transformation/mapping.
     *
     * @param protocolAdapter the protocol adapter.
     * @param inboundDispatchingSink the sink to hand mapping outcomes to.
     * @return the Sink.
     * @throws DittoRuntimeException when mapping processor could not get started.
     */
    private Sink<Object, NotUsed> getInboundMappingSink(final ProtocolAdapter protocolAdapter,
            final Sink<Object, NotUsed> inboundDispatchingSink) {

        final List<InboundMappingProcessor> inboundMappingProcessors;
        final var context = getContext();
        final var actorSystem = context.getSystem();
        final int processorPoolSize = connection.getProcessorPoolSize();
        try {
            // this one throws DittoRuntimeExceptions when the mapper could not be configured
            inboundMappingProcessors = IntStream.range(0, processorPoolSize)
                    .mapToObj(i -> InboundMappingProcessor.of(connection, connectivityConfig, actorSystem,
                            protocolAdapter, logger))
                    .toList();
        } catch (final DittoRuntimeException dre) {
            connectionLogger.failure("Failed to start message mapping processor due to: {0}", dre.getMessage());
            logger.info("Got DittoRuntimeException during initialization of MessageMappingProcessor: {} {} - desc: {}",
                    dre.getClass().getSimpleName(), dre.getMessage(), dre.getDescription().orElse(""));
            throw dre;
        }

        logger.debug("Starting inbound mapping processor actors with pool size of <{}>.", processorPoolSize);

        return InboundMappingSink.createSink(inboundMappingProcessors,
                connection.getId(),
                processorPoolSize,
                inboundDispatchingSink,
                connectivityConfig.getMappingConfig(),
                getThrottlingConfig().orElse(null),
                actorSystem.dispatchers().lookup(MESSAGE_MAPPING_PROCESSOR_DISPATCHER));
    }

    protected Optional<ConnectionThrottlingConfig> getThrottlingConfig() {
        return Optional.empty();
    }

    /**
     * Start the subscription manager. Requires MessageMappingProcessorActor to be started to work.
     * Creates an actor materializer.
     *
     * @return reference of the subscription manager.
     */
    private ActorRef startSubscriptionManager(final ActorSelection proxyActor, final ClientConfig clientConfig) {
        final ActorRef pubSubMediator = DistributedPubSub.get(getContext().getSystem()).mediator();
        final var mat = Materializer.createMaterializer(this::getContext);
        final var props = SubscriptionManager.props(clientConfig.getSubscriptionManagerTimeout(), pubSubMediator,
                proxyActor, mat);
        return getContext().actorOf(props, SubscriptionManager.ACTOR_NAME);
    }

    /**
     * Start the streaming subscription manager. Requires MessageMappingProcessorActor to be started to work.
     * Creates an actor materializer.
     *
     * @return reference of the streaming subscription manager.
     */
    private ActorRef startStreamingSubscriptionManager(final ActorSelection proxyActor, final ClientConfig clientConfig) {
        final var mat = Materializer.createMaterializer(this::getContext);
        final var props = StreamingSubscriptionManager.props(clientConfig.getSubscriptionManagerTimeout(),
                proxyActor, mat);
        return getContext().actorOf(props, StreamingSubscriptionManager.ACTOR_NAME);
    }

    private FSM.State<BaseClientState, BaseClientData> forwardThingSearchCommand(final WithDittoHeaders command,
            final BaseClientData data) {
        // Tell subscriptionManager to send search events to messageMappingProcessorActor.
        // See javadoc of
        //   ConnectionPersistentActor#forwardThingSearchCommandToClientActors(ThingSearchCommand)
        // for the message path of the search protocol.
        if (stateName() == CONNECTED) {
            subscriptionManager.tell(command, outboundDispatchingActor);
        } else {
            logger.withCorrelationId(command)
                    .debug("Client state <{}> is not CONNECTED; dropping <{}>", stateName(), command);
        }
        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> forwardStreamingSubscriptionCommand(
            final StreamingSubscriptionCommand<?> command,
            final BaseClientData data) {
        // Tell subscriptionManager to send streaming subscription events to messageMappingProcessorActor.
        if (stateName() == CONNECTED) {
            streamingSubscriptionManager.tell(command, outboundDispatchingActor);
        } else {
            logger.withCorrelationId(command)
                    .debug("Client state <{}> is not CONNECTED; dropping <{}>", stateName(), command);
        }
        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> resubscribe(final Control trigger, final BaseClientData data) {
        subscribeAndDeclareAcknowledgementLabels(dryRun, true);
        startSubscriptionRefreshTimer();
        return stay();
    }

    private BaseClientData setSession(final BaseClientData data, @Nullable final ActorRef sender,
            final DittoHeaders headers) {

        if (!Objects.equals(sender, getSelf()) && !Objects.equals(sender, getContext().system().deadLetters())) {
            return data.resetSession().addSessionSender(sender, headers);
        } else {
            return data.resetSession();
        }
    }

    private void cancelStateTimeout() {
        cancelTimer(DITTO_STATE_TIMEOUT_TIMER);
    }

    private void scheduleStateTimeout(final Duration duration) {
        startSingleTimer(DITTO_STATE_TIMEOUT_TIMER, StateTimeout(), duration);
    }

    /**
     * Add meaningful message to status for reporting.
     *
     * @param status status to report.
     * @return status with meaningful message.
     */
    private Status.Status getStatusToReport(final Status.Status status, final DittoHeaders dittoHeaders) {
        final Status.Status result;
        if (status instanceof Status.Failure failure) {
            final var failureCause = failure.cause();
            if (failureCause instanceof DittoRuntimeException) {
                result = status;
            } else {
                result = new Status.Failure(ConnectionFailedException.newBuilder(connectionId())
                        .description(describeEventualCause(failureCause))
                        .dittoHeaders(dittoHeaders)
                        .cause(failureCause)
                        .build());
            }
        } else {
            result = status;
        }
        return result;
    }

    private ActorSelection getLocalActorOfSamePath(@Nullable final ActorRef exampleActor) {
        final ActorRef actorRef = Optional.ofNullable(exampleActor).orElse(getContext().getSystem().deadLetters());
        return getContext().getSystem().actorSelection(actorRef.path().toStringWithoutAddress());
    }

    private static String describeEventualCause(@Nullable final Throwable throwable) {
        if (null == throwable) {
            return "Unknown cause.";
        }
        final var cause = throwable.getCause();
        if (cause == null || cause.equals(throwable)) {
            final String message =
                    throwable.getMessage() != null
                            ? throwable.getMessage()
                            // if message is null, provide at least the exception class name
                            : throwable.getClass().getName();
            return "Cause: " + message;
        } else {
            return describeEventualCause(cause);
        }
    }

    private SupervisorStrategy createSupervisorStrategy(final ActorRef self) {
        return new OneForOneStrategy(
                DeciderBuilder
                        .match(DittoRuntimeException.class, error -> {
                            logger.warning("Received unhandled DittoRuntimeException <{}>. " +
                                    "Telling outbound mapping processor about it.", error);
                            outboundDispatchingActor.tell(error, ActorRef.noSender());
                            return SupervisorStrategy.resume();
                        })
                        .matchAny(error -> {
                            logger.warning("Received unhandled exception in supervisor: [{}] {}",
                                    error.getClass().getName(), error.getMessage());
                            self.tell(ConnectionFailure.of(getSender(), error, "exception in child"), self);
                            if (getSender().equals(tunnelActor)) {
                                logger.debug("Restarting tunnel actor after failure: {}", error.getMessage());
                                return SupervisorStrategy.restart();
                            } else {
                                return SupervisorStrategy.stop();
                            }
                        }).build()
        );
    }

    /**
     * Subscribe for signals. NOT thread-safe due to querying actor state.
     *
     * @param isDryRun whether this is a dry run
     * @param resubscribe whether this is a resubscription
     * @return a future that completes when subscription and ack label declaration succeed and fails when either fails.
     */
    private CompletionStage<Void> subscribeAndDeclareAcknowledgementLabels(final boolean isDryRun,
            final boolean resubscribe) {
        if (isDryRun) {
            // no point writing to the distributed data in a dry run - this actor will stop right away
            return CompletableFuture.completedFuture(null);
        } else {
            final String group = getPubsubGroup();
            final CompletionStage<Boolean> subscribe = subscribeToStreamingTypes(group, resubscribe);
            final CompletionStage<Void> declare =
                    dittoProtocolSub.declareAcknowledgementLabels(getDeclaredAcks(), getSelf(), group);

            // only resub for connection; the initial subscription happened in preStart independent of publisher actors
            final CompletionStage<Void> connectionSub = resubscribe
                    ? connectionPubSub.subscribe(connection.getId(), getSelf(), true).thenApply(unused -> null)
                    : CompletableFuture.completedStage(null);
            return subscribe.thenCompose(unused -> declare).thenCompose(unused -> connectionSub);
        }
    }

    private CompletionStage<Boolean> subscribeToStreamingTypes(final String pubSubGroup, final boolean resubscribe) {
        final Set<StreamingType> streamingTypes = getUniqueStreamingTypes();
        if (streamingTypes.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return dittoProtocolSub.subscribe(streamingTypes, getTargetAuthSubjects(), getSelf(), pubSubGroup, resubscribe);
    }

    private Set<AcknowledgementLabel> getDeclaredAcks() {
        return ConnectionValidator.getAcknowledgementLabelsToDeclare(connection).collect(Collectors.toSet());
    }

    private String getPubsubGroup() {
        return connectionId().toString();
    }

    private Set<String> getTargetAuthSubjects() {
        return connection.getTargets()
                .stream()
                .map(Target::getAuthorizationContext)
                .map(AuthorizationContext::getAuthorizationSubjectIds)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
    }

    private Set<StreamingType> getUniqueStreamingTypes() {
        return connection.getTargets().stream()
                .flatMap(target -> target.getTopics().stream()
                        .map(FilteredTopic::getTopic)
                        .map(BaseClientActor::toStreamingTypes))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    private void maintainResubscribeTimer(final BaseClientState nextState) {
        if (nextState == CONNECTED) {
            startSubscriptionRefreshTimer();
        } else {
            cancelTimer(Control.RESUBSCRIBE.name());
        }
    }

    private void startSubscriptionRefreshTimer() {
        final var delay = connectivityConfig.getClientConfig().getSubscriptionRefreshDelay();
        final var randomizedDelay = delay.plus(Duration.ofMillis((long) (delay.toMillis() * Math.random())));
        startSingleTimer(Control.RESUBSCRIBE.name(), Control.RESUBSCRIBE, randomizedDelay);
    }

    private Supplier<CompletionStage<Done>> askSelfShutdownTask(final Object question) {
        final var shutdownTimeout = Duration.ofMinutes(2);
        return () -> Patterns.ask(getSelf(), question, shutdownTimeout).thenApply(answer -> Done.done());
    }

    private FSM.State<BaseClientState, BaseClientData> ackregatorStarted(final Control event,
            final BaseClientData data) {
        ++ackregatorCount;
        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> ackregatorStopped(final Control event,
            final BaseClientData data) {
        --ackregatorCount;
        if (shuttingDown && ackregatorCount == 0) {
            logger.info("{}: finished waiting for ackregators", Control.SERVICE_REQUESTS_DONE);
            return stop();
        } else {
            return stay();
        }
    }

    private FSM.State<BaseClientState, BaseClientData> serviceRequestsDone(final Control event,
            final BaseClientData data) {
        shuttingDown = true;
        logger.info("{}: ackregatorCount={}", Control.SERVICE_REQUESTS_DONE, ackregatorCount);
        if (ackregatorCount == 0) {
            return stop();
        } else {
            final var shutdownTimeout = connectivityConfig.getConnectionConfig().getShutdownTimeout();
            startSingleTimer(Control.SHUTDOWN_TIMEOUT.name(), Control.SHUTDOWN_TIMEOUT, shutdownTimeout);

            return stay();
        }
    }

    private FSM.State<BaseClientState, BaseClientData> shutdownTimeout(final Control event,
            final BaseClientData data) {
        final var shutdownTimeout = connectivityConfig.getConnectionConfig().getShutdownTimeout();
        logger.warning("Shutdown timeout <{}> reached; aborting <{}> ackregators", shutdownTimeout, ackregatorCount);

        return stop();
    }

    private static Optional<StreamingType> toStreamingTypes(final Topic topic) {
        return switch (topic) {
            case POLICY_ANNOUNCEMENTS -> Optional.of(StreamingType.POLICY_ANNOUNCEMENTS);
            case LIVE_EVENTS -> Optional.of(StreamingType.LIVE_EVENTS);
            case LIVE_COMMANDS -> Optional.of(StreamingType.LIVE_COMMANDS);
            case LIVE_MESSAGES -> Optional.of(StreamingType.MESSAGES);
            case TWIN_EVENTS -> Optional.of(StreamingType.EVENTS);
            default -> Optional.empty();
        };
    }

    /*
     * Reconnect timeout strategy that provides increasing timeouts for reconnecting the client.
     * On timeout, increase the next timeout so that backoff happens when connecting to a drop-all firewall.
     * On failure, increase backoff-wait so that backoff happens when connecting to a broken broker.
     * Timeout and backoff are incremented individually in case the remote end refuse or drop packets at random.
     * Each failure causes a timeout. As a result, failures increment both timeout and backoff. The counter
     * {@code currentTries} is only incremented on timeout so that it is not incremented twice on failure.
     */
    public interface ReconnectTimeoutStrategy {

        boolean canReconnect();

        void reset();

        Duration getNextTimeout();

        Duration getNextBackoff();

    }

    /**
     * Implements {@code timeout = minTimeout * 2^x} until max timeout is reached.
     */
    static final class DuplicationReconnectTimeoutStrategy implements ReconnectTimeoutStrategy {

        private final Duration minTimeout;
        private final Duration maxTimeout;
        private final Duration minBackoff;
        private final Duration maxBackoff;
        private final int maxTries;
        private Duration currentTimeout;
        private Duration nextBackoff;
        private int currentTries;

        @Nullable
        private Instant lastTimeoutIncrease = null;

        DuplicationReconnectTimeoutStrategy(final Duration minTimeout,
                final Duration maxTimeout,
                final int maxTries,
                final Duration minBackoff,
                final Duration maxBackoff) {

            this.maxTimeout = checkArgument(maxTimeout, isPositiveOrZero(), () -> "maxTimeout must be positive");
            this.maxBackoff = checkArgument(maxBackoff, isPositiveOrZero(), () -> "maxBackoff must be positive");
            this.minTimeout = checkArgument(minTimeout, isPositiveOrZero().and(isLowerThanOrEqual(maxTimeout)),
                    () -> "minTimeout must be positive and lower than or equal to maxTimeout");
            this.minBackoff = checkArgument(minBackoff, isPositiveOrZero().and(isLowerThanOrEqual(maxBackoff)),
                    () -> "minBackoff must be positive and lower than or equal to maxTimeout");
            this.maxTries = checkArgument(maxTries, arg -> arg > 0, () -> "maxTries must be positive");
            reset();
        }

        private static DuplicationReconnectTimeoutStrategy fromConfig(final ClientConfig clientConfig) {
            return new DuplicationReconnectTimeoutStrategy(clientConfig.getConnectingMinTimeout(),
                    clientConfig.getConnectingMaxTimeout(), clientConfig.getConnectingMaxTries(),
                    clientConfig.getMinBackoff(), clientConfig.getMaxBackoff());
        }

        @Override
        public boolean canReconnect() {
            return currentTries < maxTries;
        }

        @Override
        public void reset() {
            currentTimeout = minTimeout;
            nextBackoff = minBackoff;
            currentTries = 0;
        }

        @Override
        public Duration getNextTimeout() {
            increaseTimeoutAfterRecovery();

            return currentTimeout;
        }

        @Override
        public Duration getNextBackoff() {
            // no need to perform recovery here because timeout always happens after a backoff
            final Duration result = nextBackoff;
            nextBackoff = minDuration(maxBackoff, nextBackoff.multipliedBy(2L));

            return result;
        }

        private void increaseTimeoutAfterRecovery() {
            final var now = Instant.now();
            performRecovery(now);
            currentTimeout = minDuration(maxTimeout, currentTimeout.multipliedBy(2L));
            ++currentTries;
        }

        /*
         * Some form of recovery (reduction of backoff, timeout and retry counter) is necessary so that
         * connections that experience short downtime once every couple days do not fail permanently
         * after some time.
         */
        private void performRecovery(final Instant now) {
            // no point to perform linear recovery if this is the first timeout increase
            if (lastTimeoutIncrease != null) {
                final Duration durationSinceLastTimeout = Duration.between(lastTimeoutIncrease, now);
                final Duration resetThreshold = maxTimeout.plus(maxBackoff).multipliedBy(2L);
                if (isLonger(durationSinceLastTimeout, resetThreshold)) {
                    reset();
                }
            }
            lastTimeoutIncrease = now;
        }

        private static Duration minDuration(final Duration d1, final Duration d2) {
            return isLonger(d1, d2) ? d2 : d1;
        }

        private static boolean isLonger(final Duration d1, final Duration d2) {
            return d2.minus(d1).isNegative();
        }

        private static Predicate<Duration> isLowerThanOrEqual(final Duration otherDuration) {
            return arg -> {
                final Duration minus = arg.minus(otherDuration);

                return minus.isNegative() || minus.isZero();
            };
        }

        private static Predicate<Duration> isPositiveOrZero() {
            return arg -> !arg.isNegative();
        }

    }

    /**
     * Wrapper for a mapped {@link OutboundSignal} that should be forwarded to the publisher actor.
     */
    static final class PublishMappedMessage {

        private final OutboundSignal.MultiMapped outboundSignal;

        PublishMappedMessage(final OutboundSignal.MultiMapped outboundSignal) {
            this.outboundSignal = outboundSignal;
        }

        OutboundSignal.MultiMapped getOutboundSignal() {
            return outboundSignal;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "outboundSignal=" + outboundSignal +
                    "]";
        }
    }

    /**
     * Signals successful or failed result of client actor initialization.
     */
    public static final class InitializationResult {

        @Nullable private final ConnectionFailure failure;

        public static InitializationResult success() {
            return new InitializationResult(null);
        }

        public static InitializationResult failed(@Nullable final Throwable throwable) {
            return new InitializationResult(ConnectionFailure.of(null, throwable,
                    "Exception during client actor initialization."));
        }

        private InitializationResult(@Nullable final ConnectionFailure failure) {
            this.failure = failure;
        }

        @Nullable
        public ConnectionFailure getFailure() {
            return failure;
        }

        public boolean isSuccess() {
            return failure == null;
        }

        @Override
        public String toString() {
            return isSuccess() ? "Success" : failure.toString();
        }

    }

    private static final Object SEND_DISCONNECT_ANNOUNCEMENT = new Object();

    private static final class Disconnect {

        @Nullable
        private final ActorRef sender;
        private final boolean shutdownAfterDisconnect;

        private Disconnect(@Nullable final ActorRef sender, final boolean shutdownAfterDisconnect) {
            this.sender = sender;
            this.shutdownAfterDisconnect = shutdownAfterDisconnect;
        }

        @Nullable
        private ActorRef getSender() {
            return sender;
        }

        private boolean shutdownAfterDisconnect() {
            return shutdownAfterDisconnect;
        }
    }

    private static final class CloseConnectionAndShutdown {

        private static final CloseConnectionAndShutdown INSTANCE = new CloseConnectionAndShutdown();

        private CloseConnectionAndShutdown() {
            // no-op
        }
    }

    /**
     * Messages to control the life cycles of the actor.
     */
    public enum Control {
        INIT_COMPLETE,
        CONNECT_AFTER_TUNNEL_ESTABLISHED,
        GOTO_CONNECTED_AFTER_INITIALIZATION,
        RESUBSCRIBE,
        SERVICE_UNBIND,
        SERVICE_REQUESTS_DONE,
        ACKREGATOR_STARTED,
        ACKREGATOR_STOPPED,
        SHUTDOWN_TIMEOUT
    }

}
