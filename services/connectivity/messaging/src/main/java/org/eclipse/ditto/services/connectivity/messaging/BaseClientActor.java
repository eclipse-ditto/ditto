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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.models.connectivity.BaseClientState.CONNECTED;
import static org.eclipse.ditto.services.models.connectivity.BaseClientState.CONNECTING;
import static org.eclipse.ditto.services.models.connectivity.BaseClientState.DISCONNECTED;
import static org.eclipse.ditto.services.models.connectivity.BaseClientState.DISCONNECTING;
import static org.eclipse.ditto.services.models.connectivity.BaseClientState.INITIALIZED;
import static org.eclipse.ditto.services.models.connectivity.BaseClientState.TESTING;
import static org.eclipse.ditto.services.models.connectivity.BaseClientState.UNKNOWN;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.FatalPubSubException;
import org.eclipse.ditto.model.base.acks.PubSubTerminatedException;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.FilteredTopic;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.services.connectivity.config.ClientConfig;
import org.eclipse.ditto.services.connectivity.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.config.ConnectivityConfigModifiedBehavior;
import org.eclipse.ditto.services.connectivity.config.ConnectivityConfigProvider;
import org.eclipse.ditto.services.connectivity.config.ConnectivityConfigProviderFactory;
import org.eclipse.ditto.services.connectivity.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.config.mapping.MapperLimitsConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLoggerRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.metrics.ConnectivityCounterRegistry;
import org.eclipse.ditto.services.connectivity.messaging.persistence.ConnectionPersistenceActor;
import org.eclipse.ditto.services.connectivity.messaging.validation.ConnectionValidator;
import org.eclipse.ditto.services.connectivity.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.services.models.connectivity.BaseClientState;
import org.eclipse.ditto.services.models.connectivity.InboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.services.utils.pubsub.DittoProtocolSub;
import org.eclipse.ditto.services.utils.pubsub.StreamingType;
import org.eclipse.ditto.services.utils.search.SubscriptionManager;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionSignalIllegalException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CheckConnectionLogsActive;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.EnableConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.LoggingExpired;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.WithSubscriptionId;

import akka.Done;
import akka.actor.AbstractFSMWithStash;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.FSM;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.actor.Terminated;
import akka.cluster.pubsub.DistributedPubSub;
import akka.japi.Pair;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.Patterns;
import akka.stream.Materializer;

/**
 * Base class for ClientActors which implement the connection handling for various connectivity protocols.
 * <p>
 * The actor expects to receive a {@link CreateConnection} command after it was started. If this command is not received
 * within timeout (can be the case when this actor is remotely deployed after the command was sent) the actor requests
 * the required information from ConnectionActor.
 * </p>
 */
public abstract class BaseClientActor extends AbstractFSMWithStash<BaseClientState, BaseClientData> implements
        ConnectivityConfigModifiedBehavior {

    private static final Set<String> NO_ADDRESS_REPORTING_CHILD_NAMES = Set.of(
            InboundMappingProcessorActor.ACTOR_NAME,
            InboundDispatchingActor.ACTOR_NAME,
            OutboundMappingProcessorActor.ACTOR_NAME,
            OutboundDispatchingActor.ACTOR_NAME
    );

    protected static final Status.Success DONE = new Status.Success(Done.getInstance());

    private static final String DITTO_STATE_TIMEOUT_TIMER = "dittoStateTimeout";
    private static final int SOCKET_CHECK_TIMEOUT_MS = 2000;

    protected final ConnectionLogger connectionLogger;
    protected ConnectivityConfig connectivityConfig;
    protected final ClientConfig clientConfig;

    /**
     * Common logger for all sub-classes of BaseClientActor as its MDC already contains the connection ID.
     */
    protected final ThreadSafeDittoLoggingAdapter logger;

    private final Connection connection;
    private final ActorRef connectionActor;
    private final ActorSelection proxyActorSelection;
    private final Gauge clientGauge;
    private final Gauge clientConnectingGauge;
    private final ConnectionLoggerRegistry connectionLoggerRegistry;
    private final ConnectivityCounterRegistry connectionCounterRegistry;
    private final ActorRef inboundMappingProcessorActor;
    private final ActorRef outboundDispatchingActor;
    private final ActorRef outboundMappingProcessorActor;
    private final ActorRef subscriptionManager;
    private final ReconnectTimeoutStrategy reconnectTimeoutStrategy;
    private final SupervisorStrategy supervisorStrategy;
    private final ClientActorRefs clientActorRefs;
    private final Duration clientActorRefsNotificationDelay;
    private final DittoProtocolSub dittoProtocolSub;
    private final int subscriptionIdPrefixLength;
    private final ProtocolAdapter protocolAdapter;
    private final String actorUUID;

    private final ConnectivityConfigProvider connectivityConfigProvider;

    // counter for all child actors ever started to disambiguate between them
    private int childActorCount = 0;

    protected BaseClientActor(final Connection connection, @Nullable final ActorRef proxyActor,
            final ActorRef connectionActor) {

        this.connection = checkNotNull(connection, "connection");
        this.connectionActor = connectionActor;
        actorUUID = UUID.randomUUID().toString();

        final ConnectionId connectionId = connection.getId();
        logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connection.getId());
        // log the default client ID for tracing
        logger.info("Using default client ID <{}>", getDefaultClientId());

        connectivityConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()));
        clientConfig = connectivityConfig.getClientConfig();
        proxyActorSelection = getLocalActorOfSamePath(proxyActor);
        connectivityConfigProvider = ConnectivityConfigProviderFactory.getInstance(getContext().getSystem());

        final ProtocolAdapterProvider protocolAdapterProvider =
                ProtocolAdapterProvider.load(connectivityConfig.getProtocolConfig(), getContext().getSystem());
        protocolAdapter = protocolAdapterProvider.getProtocolAdapter(null);

        clientGauge = DittoMetrics.gauge("connection_client")
                .tag("id", connectionId.toString())
                .tag("type", connection.getConnectionType().getName());
        clientConnectingGauge = DittoMetrics.gauge("connecting_client")
                .tag("id", connectionId.toString())
                .tag("type", connection.getConnectionType().getName());

        final MonitoringConfig monitoringConfig = connectivityConfig.getMonitoringConfig();
        connectionCounterRegistry = ConnectivityCounterRegistry.newInstance();
        connectionLoggerRegistry = ConnectionLoggerRegistry.fromConfig(monitoringConfig.logger());

        connectionLoggerRegistry.initForConnection(connection);
        connectionCounterRegistry.initForConnection(connection);

        connectionLogger = connectionLoggerRegistry.forConnection(connectionId);

        reconnectTimeoutStrategy = DuplicationReconnectTimeoutStrategy.fromConfig(clientConfig);

        final Pair<ActorRef, ActorRef> actorPair = startOutboundActors(connection, protocolAdapter);
        outboundDispatchingActor = actorPair.first();
        outboundMappingProcessorActor = actorPair.second();

        final ActorRef inboundDispatcher =
                startInboundDispatchingActor(connection, protocolAdapter, outboundMappingProcessorActor);
        inboundMappingProcessorActor =
                startInboundMappingProcessorActor(connection, protocolAdapter, inboundDispatcher);
        subscriptionManager = startSubscriptionManager(this.proxyActorSelection);
        supervisorStrategy = createSupervisorStrategy(getSelf());
        clientActorRefs = ClientActorRefs.empty();
        clientActorRefsNotificationDelay = randomize(clientConfig.getClientActorRefsNotificationDelay());
        dittoProtocolSub = DittoProtocolSub.get(getContext().getSystem());
        subscriptionIdPrefixLength =
                ConnectionPersistenceActor.getSubscriptionPrefixLength(connection.getClientCount());

        // Send init message to allow for unsafe initialization of subclasses.
        getSelf().tell(Control.INIT, getSelf());
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
        final BaseClientData startingData = new BaseClientData(connection.getId(), connection,
                ConnectivityStatus.UNKNOWN, ConnectivityStatus.OPEN, "initialized", Instant.now());
        startWith(UNKNOWN, startingData);

        onTransition(this::onTransition);

        whenUnhandled(inAnyState()
                .anyEvent(this::onUnknownEvent));

        connectivityConfigProvider.registerForConnectivityConfigChanges(connectionId(), getSelf());

        initialize();

        // inform connection actor of my presence if there are other client actors
        if (connection.getClientCount() > 1) {
            connectionActor.tell(getSelf(), getSelf());
            startTimerWithFixedDelay(Control.REFRESH_CLIENT_ACTOR_REFS.name(), Control.REFRESH_CLIENT_ACTOR_REFS,
                    clientActorRefsNotificationDelay);
        }
        clientActorRefs.add(getSelf());
    }

    @Override
    public void postStop() {
        clientGauge.reset();
        clientConnectingGauge.reset();
        try {
            super.postStop();
        } catch (final Exception e) {
            logger.error(e, "An error occurred post stop.");
        }
    }

    @Override
    public void onConnectivityConfigModified(final ConnectivityConfig modifiedConfig) {
        if (hasInboundMapperConfigChanged(modifiedConfig)) {
            logger.debug("Config changed for InboundMappingProcessor, recreating it.");
            final InboundMappingProcessor inboundMappingProcessor =
                    InboundMappingProcessor.of(connection.getId(), connection.getConnectionType(),
                            connection.getPayloadMappingDefinition(),
                            getContext().getSystem(), modifiedConfig,
                            protocolAdapter, logger);
            inboundMappingProcessorActor.tell(new ReplaceInboundMappingProcessor(inboundMappingProcessor), getSelf());
        }
        if (hasOutboundMapperConfigChanged(modifiedConfig)) {
            logger.debug("Config changed for OutboundMappingProcessor, recreating it.");
            final OutboundMappingProcessor outboundMappingProcessor =
                    OutboundMappingProcessor.of(connection, getContext().getSystem(), modifiedConfig,
                            protocolAdapter, logger);
            outboundMappingProcessorActor.tell(new ReplaceOutboundMappingProcessor(outboundMappingProcessor),
                    getSelf());
        }
        this.connectivityConfig = modifiedConfig;
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
            return prefix + "_" + actorUUID;
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

    private boolean hasInboundMapperConfigChanged(final ConnectivityConfig connectivityConfig) {
        final MapperLimitsConfig currentConfig = this.connectivityConfig.getMappingConfig().getMapperLimitsConfig();
        final MapperLimitsConfig modifiedConfig = connectivityConfig.getMappingConfig().getMapperLimitsConfig();
        return currentConfig.getMaxMappedInboundMessages() != modifiedConfig.getMaxMappedInboundMessages()
                || currentConfig.getMaxSourceMappers() != modifiedConfig.getMaxSourceMappers();
    }

    private boolean hasOutboundMapperConfigChanged(final ConnectivityConfig connectivityConfig) {
        final MapperLimitsConfig currentConfig = this.connectivityConfig.getMappingConfig().getMapperLimitsConfig();
        final MapperLimitsConfig modifiedConfig = connectivityConfig.getMappingConfig().getMapperLimitsConfig();
        return currentConfig.getMaxMappedOutboundMessages() != modifiedConfig.getMaxMappedOutboundMessages()
                || currentConfig.getMaxTargetMappers() != modifiedConfig.getMaxTargetMappers();
    }

    private FSM.State<BaseClientState, BaseClientData> init() {
        doInit();

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
     * Subclasses should initialize in the implementation. This method is called once after construction.
     */
    protected void doInit() {
        // do nothing by default
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
     */
    protected abstract void doDisconnectClient(Connection connection, @Nullable ActorRef origin);

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
     * Check whether a {@code ClientConnected}, {@code ClientDisconnected} or {@code ConnectionFailed} is up to date.
     * All events are interpreted as up-to-date by default.
     *
     * @param event an event from somewhere.
     * @param state the current actor state.
     * @param sender sender of the event.
     * @return whether the event is up-to-date and should be interpreted.
     */
    protected boolean isEventUpToDate(final Object event, final BaseClientState state, final ActorRef sender) {
        return true;
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
                .event(ThingSearchCommand.class, this::forwardThingSearchCommand)
                .event(RetrieveConnectionStatus.class, this::retrieveConnectionStatus)
                .event(ResetConnectionMetrics.class, this::resetConnectionMetrics)
                .event(EnableConnectionLogs.class, (command, data) -> enableConnectionLogs(command))
                .event(RetrieveConnectionLogs.class, (command, data) -> retrieveConnectionLogs(command))
                .event(ResetConnectionLogs.class, this::resetConnectionLogs)
                .event(CheckConnectionLogsActive.class, (command, data) -> checkLoggingActive(command))
                .event(InboundSignal.class, this::handleInboundSignal)
                .event(PublishMappedMessage.class, this::publishMappedMessage)
                .event(ConnectivityCommand.class, this::onUnknownEvent) // relevant connectivity commands were handled
                .event(org.eclipse.ditto.signals.events.base.Event.class,
                        (event, data) -> connectivityConfigProvider.canHandle(event),
                        (ccb, data) -> {
                            handleEvent(ccb);
                            return stay();
                        })
                .event(Signal.class, this::handleSignal)
                .event(ActorRef.class, this::onOtherClientActorStartup)
                .event(Terminated.class, this::otherClientActorTerminated)
                .eventEquals(Control.REFRESH_CLIENT_ACTOR_REFS, this::refreshClientActorRefs)
                .event(FatalPubSubException.class, this::failConnectionDueToPubSubException);
    }

    /**
     * @return the {@link InboundMappingProcessorActor}.
     */
    protected final ActorRef getInboundMappingProcessorActor() {
        return inboundMappingProcessorActor;
    }

    /**
     * Escapes the passed actorName in a actorName valid way. Actor name should be a valid URL with ASCII letters, see
     * also {@code akka.actor.ActorPath#isValidPathElement}, therefor we encode the name as an ASCII URL.
     *
     * @param name the actorName to escape.
     * @return the escaped name.
     */
    protected static String escapeActorName(final String name) {
        try {
            return URLEncoder.encode(name, StandardCharsets.US_ASCII.name());
        } catch (final UnsupportedEncodingException e) {
            // should never happen, every JDK must support US_ASCII
            throw new IllegalStateException(e);
        }
    }

    private FSM.State<BaseClientState, BaseClientData> onOtherClientActorStartup(final ActorRef otherClientActor,
            final BaseClientData data) {
        clientActorRefs.add(otherClientActor);
        getContext().watch(otherClientActor);
        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> otherClientActorTerminated(final Terminated terminated,
            final BaseClientData data) {
        clientActorRefs.remove(terminated.getActor());
        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> refreshClientActorRefs(final Control refreshClientActorRefs,
            final BaseClientData data) {
        connectionActor.tell(getSelf(), getSelf());
        return stay();
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
                    new ImmutableConnectionFailure(null, exception.asDittoRuntimeException(), description),
                    getSelf());
        }
        return stay();
    }

    /**
     * Starts a child actor.
     *
     * @param name the Actor's name
     * @param props the Props
     * @return the created ActorRef
     */
    private ActorRef startChildActor(final String name, final Props props) {
        logger.debug("Starting child actor <{}>.", name);
        final String nameEscaped = escapeActorName(name);
        return getContext().actorOf(props, nameEscaped);
    }

    /**
     * Start a child actor whose name is guaranteed to be different from all other child actors started by this method.
     *
     * @param prefix prefix of the child actor name.
     * @param props props of the child actor.
     * @return the created ActorRef.
     */
    protected final ActorRef startChildActorConflictFree(final String prefix, final Props props) {
        return startChildActor(nextChildActorName(prefix), props);
    }

    /**
     * Stops a child actor.
     *
     * @param actor the ActorRef
     */
    protected final void stopChildActor(@Nullable final ActorRef actor) {
        if (actor != null) {
            logger.debug("Stopping child actor <{}>.", actor.path());
            getContext().stop(actor);
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
        }
        if (to == DISCONNECTED) {
            clientGauge.reset();
        }
        if (to == CONNECTING) {
            clientConnectingGauge.set(1L);
        }
        // dont use else if since we might use goTo(CONNECTING) if in CONNECTING state. This will cause another onTransition.
        if (from == CONNECTING) {
            clientConnectingGauge.reset();
        }
        // cancel our own state timeout if target state is stable
        if (to == CONNECTED || to == DISCONNECTED || to == INITIALIZED) {
            cancelStateTimeout();
        }
    }

    /*
     * For each volatile state, use the special goTo methods for timer management.
     */
    private FSM.State<BaseClientState, BaseClientData> goToConnecting(final Duration timeout) {
        scheduleStateTimeout(timeout);
        return goTo(CONNECTING);
    }

    private FSM.State<BaseClientState, BaseClientData> goToDisconnecting() {
        scheduleStateTimeout(clientConfig.getConnectingMinTimeout());
        return goTo(DISCONNECTING);
    }

    private FSM.State<BaseClientState, BaseClientData> goToTesting() {
        scheduleStateTimeout(clientConfig.getTestingTimeout());
        return goTo(TESTING);
    }

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inUnknownState() {
        return matchEventEquals(Control.INIT, (init, baseClientData) -> init())
                .anyEvent((o, baseClientData) -> {
                    stash();
                    return stay();
                });
    }

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inInitializedState() {
        return matchEvent(OpenConnection.class, this::openConnection)
                .event(CloseConnection.class, this::closeConnection)
                .event(TestConnection.class, this::testConnection);
    }

    /**
     * Creates the handler for messages in disconnected state. Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inDisconnectedState() {
        return matchEvent(OpenConnection.class, this::openConnection)
                .event(CloseConnection.class, this::connectionAlreadyClosed)
                .event(TestConnection.class, this::testConnection);
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
                .event(OpenConnection.class, this::openConnectionInConnectingState);
    }

    /**
     * Creates the handler for messages in connected state. Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectedState() {
        return matchEvent(CloseConnection.class, this::closeConnection)
                .event(OpenConnection.class, this::connectionAlreadyOpen)
                .event(ConnectionFailure.class, this::connectedConnectionFailed);
    }

    @Nullable
    protected abstract ActorRef getPublisherActor();

    private FSM.State<BaseClientState, BaseClientData> publishMappedMessage(final PublishMappedMessage message,
            final BaseClientData data) {

        if (getPublisherActor() != null) {
            getPublisherActor().forward(message.getOutboundSignal(), getContext());
        } else {
            logger.withCorrelationId(message.getOutboundSignal().getSource())
                    .error("No publisher actor available, dropping message: {}", message);
        }
        return stay();
    }

    /**
     * Creates the handler for messages in disconnected state. Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inDisconnectingState() {
        return matchEventEquals(StateTimeout(), (event, data) -> connectionTimedOut(data))
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
                .eventEquals(StateTimeout(), (stats, data) -> {
                    logger.info("test timed out.");
                    data.getSessionSenders().forEach(sender -> {
                        final DittoRuntimeException error = ConnectionFailedException.newBuilder(connectionId())
                                .description(String.format("Failed to open requested connection within <%d> seconds!",
                                        clientConfig.getTestingTimeout().getSeconds()))
                                .dittoHeaders(sender.second())
                                .build();
                        sender.first().tell(new Status.Failure(error), getSelf());
                    });
                    return stop();
                });
    }

    private State<BaseClientState, BaseClientData> onUnknownEvent(final Object event, final BaseClientData state) {
        Object message = event;
        if (event instanceof Failure) {
            message = ((Failure) event).cause();
        } else if (event instanceof Status.Failure) {
            message = ((Status.Failure) event).cause();
        }

        if (message instanceof Throwable) {
            logger.error((Throwable) message, "received Exception {} in state {} - status: {} - sender: {}",
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

    private FSM.State<BaseClientState, BaseClientData> closeConnection(final CloseConnection closeConnection,
            final BaseClientData data) {

        final ActorRef sender = getSender();
        doDisconnectClient(data.getConnection(), sender);
        dittoProtocolSub.removeSubscriber(getSelf());
        return goToDisconnecting().using(setSession(data, sender, closeConnection.getDittoHeaders())
                .setDesiredConnectionStatus(ConnectivityStatus.CLOSED)
                .setConnectionStatusDetails("closing or deleting connection at " + Instant.now()));
    }

    private FSM.State<BaseClientState, BaseClientData> openConnection(final OpenConnection openConnection,
            final BaseClientData data) {

        final ActorRef sender = getSender();
        final DittoHeaders dittoHeaders = openConnection.getDittoHeaders();
        reconnectTimeoutStrategy.reset();
        final Duration connectingTimeout = clientConfig.getConnectingMinTimeout();
        if (canConnectViaSocket(connection)) {
            doConnectClient(connection, sender);
            return goToConnecting(connectingTimeout).using(setSession(data, sender, dittoHeaders));
        } else {
            cleanupResourcesForConnection();
            final DittoRuntimeException error = newConnectionFailedException(dittoHeaders);
            sender.tell(new Status.Failure(error), getSelf());
            return goToConnecting(connectingTimeout)
                    .using(data.setConnectionStatus(ConnectivityStatus.FAILED)
                            .setConnectionStatusDetails(error.getMessage())
                            .resetSession());
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
        logger.debug("Trying to reconnect.");
        connectionLogger.success("Trying to reconnect.");
        if (canConnectViaSocket(connection)) {
            doConnectClient(connection, null);
        } else {
            logger.info("Socket is closed, scheduling a reconnect.");
            cleanupResourcesForConnection();
        }
    }

    private FSM.State<BaseClientState, BaseClientData> testConnection(final TestConnection testConnection,
            final BaseClientData data) {

        final ActorRef self = getSelf();
        final ActorRef sender = getSender();
        final Connection connectionToBeTested = testConnection.getConnection();

        if (!canConnectViaSocket(connectionToBeTested)) {
            final ConnectionFailedException connectionFailedException =
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

    private FSM.State<BaseClientState, BaseClientData> connectionTimedOut(final BaseClientData data) {
        data.getSessionSenders().forEach(sender -> {
            final DittoRuntimeException error = ConnectionFailedException.newBuilder(connectionId())
                    .dittoHeaders(sender.second())
                    .build();
            sender.first().tell(new Status.Failure(error), getSelf());
        });
        cleanupResourcesForConnection();
        cleanupFurtherResourcesOnConnectionTimeout(stateName());

        final String timeoutMessage = "Connection timed out at " + Instant.now() + " while " + stateName() + ".";

        if (ConnectivityStatus.OPEN.equals(data.getDesiredConnectionStatus())) {
            if (reconnectTimeoutStrategy.canReconnect()) {
                reconnect();
                return goToConnecting(reconnectTimeoutStrategy.getNextTimeout()).using(data.resetSession()
                        .setConnectionStatus(ConnectivityStatus.FAILED)
                        .setConnectionStatusDetails(timeoutMessage + " Will try to reconnect."));
            } else {
                connectionLogger.failure(
                        "Connection timed out. Reached maximum tries and thus will no longer try to reconnect.");
                logger.info(
                        "Connection <{}> reached maximum retries for reconnecting and thus will no longer try to reconnect.",
                        connectionId());

                return goTo(INITIALIZED).using(data.resetSession()
                        .setConnectionStatus(ConnectivityStatus.FAILED)
                        .setConnectionStatusDetails(timeoutMessage +
                                " Reached maximum retries and thus will not try to reconnect any longer."));
            }
        }

        connectionLogger.failure("Connection timed out.");
        return goTo(INITIALIZED).using(data.resetSession()
                .setConnectionStatus(ConnectivityStatus.FAILED)
                .setConnectionStatusDetails(timeoutMessage));
    }

    private State<BaseClientState, BaseClientData> openConnectionInConnectingState(final OpenConnection openConnection,
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

        return ifEventUpToDate(clientConnected, () -> {
            allocateResourcesOnConnection(clientConnected);

            Patterns.pipe(startPublisherAndConsumerActors(clientConnected), getContext().getDispatcher())
                    .to(getSelf());

            return stay().using(data);
        });
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
        final CompletionStage<Void> pubsubReady = subscribeAndDeclareAcknowledgementLabels();

        return publisherReady.thenCompose(_void -> consumersReady)
                .thenCompose(_void -> pubsubReady)
                .thenApply(unused -> InitializationResult.success())
                .exceptionally(InitializationResult::failed);
    }

    private State<BaseClientState, BaseClientData> handleInitializationResult(
            final InitializationResult initializationResult, final BaseClientData data) {

        if (initializationResult.isSuccess()) {
            connectionLogger.success("Connection successful.");
            data.getSessionSenders().forEach(origin -> origin.first().tell(new Status.Success(CONNECTED), getSelf()));
            return goTo(CONNECTED).using(data.resetSession()
                    .setConnectionStatus(ConnectivityStatus.OPEN)
                    .setConnectionStatusDetails("Connected at " + Instant.now()));
        } else {
            getSelf().tell(initializationResult.getFailure(), ActorRef.noSender());
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

        return ifEventUpToDate(event, () -> {
            connectionLogger.success("Disconnected successfully.");

            cleanupResourcesForConnection();
            data.getSessionSenders()
                    .forEach(sender -> sender.first().tell(new Status.Success(DISCONNECTED), getSelf()));
            return goTo(DISCONNECTED).using(data.resetSession()
                    .setConnectionStatus(ConnectivityStatus.CLOSED)
                    .setConnectionStatusDetails("Disconnected at " + Instant.now()));
        });
    }

    private State<BaseClientState, BaseClientData> connectingConnectionFailed(final ConnectionFailure event,
            final BaseClientData data) {

        return ifEventUpToDate(event, () -> {
            logger.info("{} failed: <{}>", stateName(), event.getFailure());

            cleanupResourcesForConnection();
            data.getSessionSenders().forEach(sender ->
                    sender.first().tell(getStatusToReport(event.getFailure(), sender.second()), getSelf()));

            return backoffAfterFailure(event, data);
        });
    }

    private State<BaseClientState, BaseClientData> connectedConnectionFailed(final ConnectionFailure event,
            final BaseClientData data) {

        return ifEventUpToDate(event, () -> {

            // do not bother to disconnect gracefully - the other end of the connection is probably dead
            cleanupResourcesForConnection();
            cleanupFurtherResourcesOnConnectionTimeout(stateName());

            return backoffAfterFailure(event, data);
        });
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
                final Duration nextBackoff = reconnectTimeoutStrategy.getNextBackoff();
                final String errorMessage =
                        String.format("Connection failed due to: {0}. Will reconnect after %s.", nextBackoff);
                connectionLogger.failure(errorMessage, event.getFailureDescription());
                logger.info("Connection failed: {}. Reconnect after {}.", event, nextBackoff);
                return goToConnecting(nextBackoff).using(data.resetSession()
                        .setConnectionStatus(ConnectivityStatus.FAILED)
                        .setConnectionStatusDetails(event.getFailureDescription()));
            } else {
                connectionLogger.failure(
                        "Connection failed due to: {0}. Reached maximum tries and thus will no longer try to reconnect.",
                        event.getFailureDescription());
                logger.info(
                        "Connection <{}> reached maximum retries for reconnecting and thus will no longer try to reconnect.",
                        connectionId());

                // stay in UNKNOWN state until re-opened manually
                return goTo(INITIALIZED).using(data.resetSession()
                        .setConnectionStatus(ConnectivityStatus.FAILED)
                        .setConnectionStatusDetails(event.getFailureDescription()
                                + " Reached maximum retries and thus will not try to reconnect any longer."));
            }
        }

        connectionLogger.failure("Connection failed due to: {0}.", event.getFailureDescription());
        return goTo(INITIALIZED)
                .using(data.resetSession()
                        .setConnectionStatus(ConnectivityStatus.FAILED)
                        .setConnectionStatusDetails(event.getFailureDescription())
                );
    }

    private State<BaseClientState, BaseClientData> ifEventUpToDate(final Object event,
            final Supplier<State<BaseClientState, BaseClientData>> thenExecute) {

        final BaseClientState state = stateName();
        final ActorRef sender = getSender();
        if (isEventUpToDate(event, state, sender)) {
            return thenExecute.get();
        } else {
            logger.warning("Received stale event <{}> at state <{}>", event, state);
            return stay();
        }
    }

    private FSM.State<BaseClientState, BaseClientData> retrieveConnectionStatus(final RetrieveConnectionStatus command,
            final BaseClientData data) {

        logger.withCorrelationId(command)
                .debug("Received RetrieveConnectionStatus for connection <{}> message from <{}>." +
                                " Forwarding to consumers and publishers.", command.getConnectionEntityId(),
                        getSender());

        // send to all children (consumers, publishers, except mapping actor)
        getContext().getChildren().forEach(child -> {
            final String childName = child.path().name();
            if (!NO_ADDRESS_REPORTING_CHILD_NAMES.contains(childName)) {
                logger.withCorrelationId(command)
                        .debug("Forwarding RetrieveAddressStatus to child <{}>.", child.path());
                child.tell(RetrieveAddressStatus.getInstance(), getSender());
            }
        });

        final ResourceStatus clientStatus =
                ConnectivityModelFactory.newClientStatus(getInstanceIdentifier(),
                        data.getConnectionStatus(),
                        "[" + stateName().name() + "] " + data.getConnectionStatusDetails().orElse(""),
                        getInConnectionStatusSince());
        getSender().tell(clientStatus, getSelf());

        return stay();
    }

    private static String getInstanceIdentifier() {
        return InstanceIdentifierSupplier.getInstance().get();
    }

    private FSM.State<BaseClientState, BaseClientData> retrieveConnectionMetrics(
            final RetrieveConnectionMetrics command) {

        logger.withCorrelationId(command)
                .debug("Received RetrieveConnectionMetrics message for connection <{}>. Gathering metrics.",
                        command.getConnectionEntityId());
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final SourceMetrics sourceMetrics = connectionCounterRegistry.aggregateSourceMetrics(connectionId());
        final TargetMetrics targetMetrics = connectionCounterRegistry.aggregateTargetMetrics(connectionId());

        final ConnectionMetrics connectionMetrics =
                connectionCounterRegistry.aggregateConnectionMetrics(sourceMetrics, targetMetrics);

        final RetrieveConnectionMetricsResponse retrieveConnectionMetricsResponse =
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
                        command.getConnectionEntityId());
        connectionCounterRegistry.resetForConnection(data.getConnection());

        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> enableConnectionLogs(final EnableConnectionLogs command) {
        final ConnectionId connectionId = command.getConnectionEntityId();
        logger.withCorrelationId(command)
                .debug("Received EnableConnectionLogs message for connection <{}>. Enabling logs.", connectionId);

        connectionLoggerRegistry.unmuteForConnection(connectionId);

        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> checkLoggingActive(final CheckConnectionLogsActive command) {
        final ConnectionId connectionId = command.getConnectionEntityId();
        logger.withCorrelationId(command)
                .debug("Received checkLoggingActive message for connection <{}>." +
                        " Checking if logging for connection is expired.", connectionId);

        if (connectionLoggerRegistry.isLoggingExpired(connectionId, command.getTimestamp())) {
            connectionLoggerRegistry.muteForConnection(connectionId);
            getSender().tell(LoggingExpired.of(connectionId), ActorRef.noSender());
        }

        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> retrieveConnectionLogs(final RetrieveConnectionLogs command) {
        logger.withCorrelationId(command)
                .debug("Received RetrieveConnectionLogs message for connection <{}>. Gathering metrics.",
                        command.getConnectionEntityId());

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
        final DittoHeaders headers = signal instanceof WithDittoHeaders
                ? ((WithDittoHeaders) signal).getDittoHeaders()
                : DittoHeaders.empty();
        switch (state) {
            case CONNECTING:
            case DISCONNECTING:
                return ConnectionSignalIllegalException.newBuilder(connectionId())
                        .operationName(state.name().toLowerCase())
                        .timeout(clientConfig.getConnectingMinTimeout())
                        .dittoHeaders(headers)
                        .build();
            default:
                final String signalType = signal instanceof Signal
                        ? ((Signal<?>) signal).getType()
                        : "unknown"; // no need to disclose Java class of signal to clients
                return ConnectionSignalIllegalException.newBuilder(connectionId())
                        .illegalSignalForState(signalType, state.name().toLowerCase())
                        .dittoHeaders(headers)
                        .build();
        }
    }

    protected boolean canConnectViaSocket(final Connection connection) {
        return checkHostAndPortForAvailability(connection.getHostname(), connection.getPort());
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

    private FSM.State<BaseClientState, BaseClientData> handleSignal(final Signal<?> signal,
            final BaseClientData data) {
        if (stateName() == CONNECTED) {
            outboundDispatchingActor.tell(signal, getSender());
        } else {
            logger.withCorrelationId(signal)
                    .debug("Client state <{}> is not CONNECTED; dropping <{}>", stateName(), signal);
        }
        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> handleInboundSignal(final InboundSignal inboundSignal,
            final BaseClientData data) {
        // dispatch signal to other client actors according to entity ID
        final Signal<?> signal = inboundSignal.getSignal();
        if (signal instanceof WithSubscriptionId<?>) {
            dispatchSearchCommand((WithSubscriptionId<?>) signal);
        } else {
            final ActorRef recipient = clientActorRefs.lookup(signal.getEntityId()).orElseThrow();
            if (getSelf().equals(recipient)) {
                outboundDispatchingActor.tell(inboundSignal, getSender());
            } else {
                recipient.tell(inboundSignal, getSender());
            }
        }
        return stay();
    }

    private void dispatchSearchCommand(final WithSubscriptionId<?> searchCommand) {
        final String subscriptionId = searchCommand.getSubscriptionId();
        if (subscriptionId.length() > subscriptionIdPrefixLength) {
            final String prefix = subscriptionId.substring(0, subscriptionIdPrefixLength);
            final Optional<Integer> index = parseHexString(prefix);
            if (index.isPresent()) {
                final ActorRef receiver = clientActorRefs.get(index.get()).orElseThrow();
                if (getSelf().equals(receiver)) {
                    forwardThingSearchCommand(searchCommand, stateData());
                } else {
                    // sender is overwritten at the client actor responsible for the subscription ID prefix.
                    receiver.tell(searchCommand, ActorRef.noSender());
                }
                return;
            }
        }
        // command is invalid or outdated, dropping.
        logger.withCorrelationId(searchCommand)
                .info("Dropping search command with invalid subscription ID: <{}>", searchCommand);
        connectionLogger.failure(InfoProviderFactory.forSignal(searchCommand),
                "Dropping search command with invalid subscription ID: " +
                        searchCommand.getSubscriptionId());
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
        InboundMappingProcessor.of(connection.getId(),
                connection.getConnectionType(),
                connection.getPayloadMappingDefinition(),
                actorSystem,
                connectivityConfig,
                protocolAdapter,
                logger);
        OutboundMappingProcessor.of(connection,
                actorSystem,
                connectivityConfig,
                protocolAdapter,
                logger);
        return CompletableFuture.completedFuture(new Status.Success("mapping"));
    }

    private Pair<ActorRef, ActorRef> startOutboundActors(final Connection connection,
            final ProtocolAdapter protocolAdapter) {
        final OutboundMappingSettings settings;
        final OutboundMappingProcessor outboundMappingProcessor;
        try {
            ConnectivityConfig retrievedConnectivityConfig =
                    connectivityConfigProvider.getConnectivityConfig(connection.getId());
            // this one throws DittoRuntimeExceptions when the mapper could not be configured
            settings = OutboundMappingSettings.of(connection,
                    getContext().getSystem(),
                    proxyActorSelection,
                    retrievedConnectivityConfig,
                    protocolAdapter,
                    logger);
            outboundMappingProcessor = OutboundMappingProcessor.of(settings);
        } catch (final DittoRuntimeException dre) {
            connectionLogger.failure("Failed to start message mapping processor due to: {}.", dre.getMessage());
            logger.info("Got DittoRuntimeException during initialization of MessageMappingProcessor: {} {} - desc: {}",
                    dre.getClass().getSimpleName(), dre.getMessage(), dre.getDescription().orElse(""));
            throw dre;
        }

        logger.debug("Starting mapping processor actors with pool size of <{}>.", connection.getProcessorPoolSize());
        final Props outboundMappingProcessorActorProps =
                OutboundMappingProcessorActor.props(getSelf(), outboundMappingProcessor, connection,
                        connection.getProcessorPoolSize());

        final ActorRef processorActor =
                getContext().actorOf(outboundMappingProcessorActorProps, OutboundMappingProcessorActor.ACTOR_NAME);

        final Props outboundDispatchingProcessorActorProps = OutboundDispatchingActor.props(settings, processorActor);
        final ActorRef dispatchingActor =
                getContext().actorOf(outboundDispatchingProcessorActorProps, OutboundDispatchingActor.ACTOR_NAME);

        return Pair.create(dispatchingActor, processorActor);
    }

    /**
     * Starts the {@link InboundDispatchingActor} responsible for signal de-multiplexing and acknowledgement
     * aggregation.
     *
     * @return the ref to the started {@link InboundMappingProcessorActor}
     * @throws DittoRuntimeException when mapping processor could not get started.
     */
    private ActorRef startInboundDispatchingActor(final Connection connection,
            final ProtocolAdapter protocolAdapter,
            final ActorRef outboundMappingProcessorActor) {

        final Props inboundDispatchingActorProps =
                InboundDispatchingActor.props(connection, protocolAdapter.headerTranslator(), proxyActorSelection,
                        connectionActor, outboundMappingProcessorActor);

        return getContext().actorOf(inboundDispatchingActorProps, InboundDispatchingActor.ACTOR_NAME);
    }

    /**
     * Starts the {@link InboundMappingProcessorActor} responsible for payload transformation/mapping as child actor.
     *
     * @param connection the connection.
     * @param protocolAdapter the protocol adapter.
     * @param inboundDispatchingActor the actor to hand mapping outcomes to.
     * @return the ref to the started {@link InboundMappingProcessorActor}
     * @throws DittoRuntimeException when mapping processor could not get started.
     */
    private ActorRef startInboundMappingProcessorActor(final Connection connection,
            final ProtocolAdapter protocolAdapter,
            final ActorRef inboundDispatchingActor) {

        final InboundMappingProcessor inboundMappingProcessor;
        try {
            ConnectivityConfig retrievedConnectivityConfig =
                    connectivityConfigProvider.getConnectivityConfig(connection.getId());

            // this one throws DittoRuntimeExceptions when the mapper could not be configured
            inboundMappingProcessor = InboundMappingProcessor.of(connection.getId(),
                    connection.getConnectionType(),
                    connection.getPayloadMappingDefinition(),
                    getContext().getSystem(),
                    retrievedConnectivityConfig,
                    protocolAdapter,
                    logger);
        } catch (final DittoRuntimeException dre) {
            connectionLogger.failure("Failed to start message mapping processor due to: {}.", dre.getMessage());
            logger.info("Got DittoRuntimeException during initialization of MessageMappingProcessor: {} {} - desc: {}",
                    dre.getClass().getSimpleName(), dre.getMessage(), dre.getDescription().orElse(""));
            throw dre;
        }

        logger.debug("Starting inbound mapping processor actors with pool size of <{}>.",
                connection.getProcessorPoolSize());

        final Props inboundMappingProcessorActorProps =
                InboundMappingProcessorActor.props(inboundMappingProcessor, protocolAdapter.headerTranslator(),
                        connection, connection.getProcessorPoolSize(), inboundDispatchingActor);

        return getContext().actorOf(inboundMappingProcessorActorProps, InboundMappingProcessorActor.ACTOR_NAME);
    }

    /**
     * Start the subscription manager. Requires MessageMappingProcessorActor to be started to work.
     * Creates an actor materializer.
     *
     * @return reference of the subscription manager.
     */
    private ActorRef startSubscriptionManager(final ActorSelection proxyActor) {
        final ActorRef pubSubMediator = DistributedPubSub.get(getContext().getSystem()).mediator();
        final Materializer mat = Materializer.createMaterializer(this::getContext);
        final Props props = SubscriptionManager.props(clientConfig.getSubscriptionManagerTimeout(), pubSubMediator,
                proxyActor, mat);
        return getContext().actorOf(props, SubscriptionManager.ACTOR_NAME);
    }

    private FSM.State<BaseClientState, BaseClientData> forwardThingSearchCommand(final ThingSearchCommand<?> command,
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

    protected boolean isDryRun() {
        return TESTING.equals(stateName());
    }

    private String nextChildActorName(final String prefix) {
        return prefix + ++childActorCount;
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
        setTimer(DITTO_STATE_TIMEOUT_TIMER, StateTimeout(), duration, false);
    }

    /**
     * Add meaningful message to status for reporting.
     *
     * @param status status to report.
     * @return status with meaningful message.
     */
    private Status.Status getStatusToReport(final Status.Status status, final DittoHeaders dittoHeaders) {
        final Status.Status answerToPublish;
        if (status instanceof Status.Failure) {
            final Status.Failure failure = (Status.Failure) status;
            if (!(failure.cause() instanceof DittoRuntimeException)) {
                final DittoRuntimeException error = ConnectionFailedException.newBuilder(connectionId())
                        .description(describeEventualCause(failure.cause()))
                        .dittoHeaders(dittoHeaders)
                        .build();
                answerToPublish = new Status.Failure(error);
            } else {
                answerToPublish = status;
            }
        } else {
            answerToPublish = status;
        }
        return answerToPublish;
    }

    private ActorSelection getLocalActorOfSamePath(@Nullable final ActorRef exampleActor) {
        final ActorRef actorRef = Optional.ofNullable(exampleActor).orElse(getContext().getSystem().deadLetters());
        return getContext().getSystem().actorSelection(actorRef.path().toStringWithoutAddress());
    }

    private static String describeEventualCause(@Nullable final Throwable throwable) {
        if (null == throwable) {
            return "Unknown cause.";
        }
        final Throwable cause = throwable.getCause();
        if (cause == null || cause.equals(throwable)) {
            return "Cause: " + throwable.getMessage();
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
                            return (SupervisorStrategy.Directive) SupervisorStrategy.resume();
                        })
                        .matchAny(error -> {
                            self.tell(new ImmutableConnectionFailure(getSender(), error, "exception in child"), self);
                            return (SupervisorStrategy.Directive) SupervisorStrategy.stop();
                        }).build()
        );
    }

    /**
     * Subscribe for signals. NOT thread-safe due to querying actor state.
     *
     * @return a future that completes when subscription and ack label declaration succeed and fails when either fails.
     */
    private CompletionStage<Void> subscribeAndDeclareAcknowledgementLabels() {
        if (isDryRun()) {
            // no point writing to the distributed data in a dry run - this actor will stop right away
            return CompletableFuture.completedFuture(null);
        } else {
            final String group = getPubsubGroup();
            final CompletionStage<Void> subscribe =
                    dittoProtocolSub.subscribe(getUniqueStreamingTypes(), getTargetAuthSubjects(), getSelf(), group);
            final CompletionStage<Void> declare =
                    dittoProtocolSub.declareAcknowledgementLabels(getDeclaredAcks(), getSelf(), group);
            return declare.thenCompose(_void -> subscribe);
        }
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
                .collect(Collectors.toSet());
    }

    private static StreamingType toStreamingTypes(final Topic topic) {
        switch (topic) {
            case POLICY_ANNOUNCEMENTS:
                return StreamingType.POLICY_ANNOUNCEMENTS;
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
    }

    private static Duration randomize(final Duration base) {
        return base.plus(Duration.ofMillis((long) (base.toMillis() * Math.random())));
    }

    private static Optional<Integer> parseHexString(final String hexString) {
        try {
            return Optional.of(Integer.parseUnsignedInt(hexString, 16));
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
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
            final Instant now = Instant.now();
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
            return new InitializationResult(new ImmutableConnectionFailure(null, throwable,
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

    private enum Control {
        INIT,
        REFRESH_CLIENT_ACTOR_REFS
    }

    /**
     * Message sent to {@link InboundMappingProcessorActor} instructing it to replace the current
     * {@link InboundMappingProcessor}.
     */
    static class ReplaceInboundMappingProcessor {

        private final InboundMappingProcessor inboundMappingProcessor;

        private ReplaceInboundMappingProcessor(final InboundMappingProcessor inboundMappingProcessor) {
            this.inboundMappingProcessor = inboundMappingProcessor;
        }

        InboundMappingProcessor getInboundMappingProcessor() {
            return inboundMappingProcessor;
        }
    }

    /**
     * Message sent to {@link OutboundMappingProcessorActor} instructing it to replace the current
     * {@link OutboundMappingProcessor}.
     */
    static class ReplaceOutboundMappingProcessor {

        private final OutboundMappingProcessor outboundMappingProcessor;

        private ReplaceOutboundMappingProcessor(
                final OutboundMappingProcessor outboundMappingProcessor) {
            this.outboundMappingProcessor = outboundMappingProcessor;
        }

        OutboundMappingProcessor getOutboundMappingProcessor() {
            return outboundMappingProcessor;
        }
    }
}
