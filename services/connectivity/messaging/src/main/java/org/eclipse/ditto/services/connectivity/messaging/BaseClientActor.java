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
import static org.eclipse.ditto.services.connectivity.messaging.BaseClientState.CONNECTED;
import static org.eclipse.ditto.services.connectivity.messaging.BaseClientState.CONNECTING;
import static org.eclipse.ditto.services.connectivity.messaging.BaseClientState.DISCONNECTED;
import static org.eclipse.ditto.services.connectivity.messaging.BaseClientState.DISCONNECTING;
import static org.eclipse.ditto.services.connectivity.messaging.BaseClientState.TESTING;
import static org.eclipse.ditto.services.connectivity.messaging.BaseClientState.UNKNOWN;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
import org.eclipse.ditto.services.connectivity.messaging.InitializationState.ResourceReady;
import org.eclipse.ditto.services.connectivity.messaging.config.ClientConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLoggerRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.metrics.ConnectivityCounterRegistry;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.base.Signal;
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

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.FSM;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.routing.Broadcast;
import akka.routing.ConsistentHashingPool;
import akka.routing.ConsistentHashingRouter;

/**
 * Base class for ClientActors which implement the connection handling for various connectivity protocols.
 * <p>
 * The actor expects to receive a {@link CreateConnection} command after it was started. If this command is not received
 * within timeout (can be the case when this actor is remotely deployed after the command was sent) the actor requests
 * the required information from ConnectionActor.
 * </p>
 */
public abstract class BaseClientActor extends AbstractFSM<BaseClientState, BaseClientData> {

    private static final String DITTO_STATE_TIMEOUT_TIMER = "dittoStateTimeout";

    private static final int SOCKET_CHECK_TIMEOUT_MS = 2000;

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    protected final ConnectionLogger connectionLogger;
    @Nullable protected ActorRef publisherActor = null;

    protected final ConnectivityConfig connectivityConfig;
    protected final ClientConfig clientConfig;
    private final ProtocolAdapterProvider protocolAdapterProvider;
    private final ActorRef conciergeForwarder;
    private final Gauge clientGauge;
    private final Gauge clientConnectingGauge;
    private final ConnectionLoggerRegistry connectionLoggerRegistry;
    private final ConnectivityCounterRegistry connectionCounterRegistry;
    private final ActorRef messageMappingProcessorActor;

    private final ReconnectTimeoutStrategy reconnectTimeoutStrategy;

    // counter for all child actors ever started to disambiguate between them
    private int childActorCount = 0;

    protected BaseClientActor(final Connection connection,
            final ConnectivityStatus desiredConnectionStatus,
            final ActorRef conciergeForwarder) {

        checkNotNull(connection, "connection");

        ConnectionLogUtil.enhanceLogWithConnectionId(log, connection.getId());

        this.connectivityConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        this.clientConfig = this.connectivityConfig.getClientConfig();
        this.conciergeForwarder = conciergeForwarder;
        protocolAdapterProvider =
                ProtocolAdapterProvider.load(connectivityConfig.getProtocolConfig(), getContext().getSystem());

        final BaseClientData startingData =
                new BaseClientData(connection.getId(), connection, ConnectivityStatus.UNKNOWN, desiredConnectionStatus,
                        "initialized", Instant.now(), null, null,
                        new InitializationState(connection.getProcessorPoolSize()));

        clientGauge = DittoMetrics.gauge("connection_client")
                .tag("id", connection.getId())
                .tag("type", connection.getConnectionType().getName());
        clientConnectingGauge = DittoMetrics.gauge("connecting_client")
                .tag("id", connection.getId())
                .tag("type", connection.getConnectionType().getName());

        // stable states
        when(UNKNOWN, inUnknownState());
        when(CONNECTED, inConnectedState());
        when(DISCONNECTED, inDisconnectedState());

        // volatile states
        //
        // DO NOT use state timeout:
        // FSM state timeout gets reset by any message, AND cannot be longer than 5 minutes (Akka v2.5.23).
        when(DISCONNECTING, inDisconnectingState());
        when(CONNECTING, inConnectingState());
        when(TESTING, inTestingState());

        startWith(UNKNOWN, startingData, clientConfig.getInitTimeout());

        onTransition(this::onTransition);

        whenUnhandled(inAnyState().anyEvent(this::onUnknownEvent));

        final MonitoringConfig monitoringConfig = this.connectivityConfig.getMonitoringConfig();
        this.connectionCounterRegistry = ConnectivityCounterRegistry.fromConfig(monitoringConfig.counter());
        this.connectionLoggerRegistry = ConnectionLoggerRegistry.fromConfig(monitoringConfig.logger());

        this.connectionLoggerRegistry.initForConnection(connection);
        this.connectionCounterRegistry.initForConnection(connection);

        this.connectionLogger = this.connectionLoggerRegistry.forConnection(connection.getId());

        this.reconnectTimeoutStrategy = DuplicationReconnectTimeoutStrategy.fromConfig(clientConfig);

        this.messageMappingProcessorActor = startMessageMappingProcessorActor();

        initialize();
    }

    /**
     * Handles {@link TestConnection} commands by returning a CompletionState of {@link akka.actor.Status.Status Status}
     * which may be {@link akka.actor.Status.Success Success} or {@link akka.actor.Status.Failure Failure}.
     *
     * @param connection the Connection to test
     * @return the CompletionStage with the test result
     */
    protected abstract CompletionStage<Status.Status> doTestConnection(Connection connection);

    /**
     * Subclasses should allocate resources (publishers and consumers) in the implementation. This method is called once
     * this {@code Client} connected successfully.
     *
     * @param clientConnected the ClientConnected message which may be subclassed and thus adding more information
     */
    protected abstract void allocateResourcesOnConnection(ClientConnected clientConnected);

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
        return matchEvent(RetrieveConnectionMetrics.class, BaseClientData.class,
                (command, data) -> this.retrieveConnectionMetrics(command))
                .event(RetrieveConnectionStatus.class, BaseClientData.class, this::retrieveConnectionStatus)
                .event(ResetConnectionMetrics.class, BaseClientData.class, this::resetConnectionMetrics)
                .event(EnableConnectionLogs.class, BaseClientData.class,
                        (command, data) -> this.enableConnectionLogs(command))
                .event(RetrieveConnectionLogs.class, BaseClientData.class,
                        (command, data) -> this.retrieveConnectionLogs(command))
                .event(ResetConnectionLogs.class, BaseClientData.class, this::resetConnectionLogs)
                .event(CheckConnectionLogsActive.class, BaseClientData.class,
                        (command, data) -> this.checkLoggingActive(command))
                .event(OutboundSignal.class, BaseClientData.class, (signal, data) -> {
                    handleOutboundSignal(signal);
                    return stay();
                }).event(ResourceReady.class, this::resourceReady);
    }

    /**
     * @return the MessageMappingProcessorActor.
     */
    protected final ActorRef getMessageMappingProcessorActor() {
        return messageMappingProcessorActor;
    }

    protected void stopPublisherActor() {
        if (publisherActor != null) {
            stopChildActor(publisherActor);
        }
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

    /**
     * Starts a child actor.
     *
     * @param name the Actor's name
     * @param props the Props
     * @return the created ActorRef
     */
    private ActorRef startChildActor(final String name, final Props props) {
        log.debug("Starting child actor <{}>.", name);
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
     * @param name the Actor's name
     */
    protected final void stopChildActor(final String name) {
        final String nameEscaped = escapeActorName(name);
        final Optional<ActorRef> child = getContext().findChild(nameEscaped);
        if (child.isPresent()) {
            log.debug("Stopping child actor <{}>.", nameEscaped);
            getContext().stop(child.get());
        } else {
            log.debug("Cannot stop child actor <{}> because it does not exist.", nameEscaped);
        }
    }

    /**
     * Stops a child actor.
     *
     * @param actor the ActorRef
     */
    protected final void stopChildActor(final ActorRef actor) {
        log.debug("Stopping child actor <{}>.", actor.path());
        getContext().stop(actor);
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
    protected final String connectionId() {
        return stateData().getConnectionId();
    }

    /**
     * @return the sources configured for this connection or an empty list if no sources were configured.
     */
    protected final List<Source> getSourcesOrEmptyList() {
        return connection().getSources();
    }

    /**
     * @return the targets configured for this connection or an empty list if no targets were configured.
     */
    protected final List<Target> getTargetsOrEmptyList() {
        return connection().getTargets();
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
        ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId());
        log.debug("Transition: {} -> {}", from, to);
        if (to == CONNECTED) {
            clientGauge.increment();
        }
        if (to == DISCONNECTED) {
            clientGauge.decrement();
        }
        if (to == CONNECTING) {
            clientConnectingGauge.increment();
        }
        // dont use else if since we might use goTo(CONNECTING) if in CONNECTING state. This will cause another onTransition.
        if (from == CONNECTING) {
            clientConnectingGauge.decrement();
        }
        // cancel our own state timeout if target state is stable
        switch (to) {
            case UNKNOWN:
            case CONNECTED:
            case DISCONNECTED:
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
        return matchEvent(OpenConnection.class, BaseClientData.class, this::openConnection)
                .event(CloseConnection.class, BaseClientData.class, this::closeConnection)
                .event(TestConnection.class, BaseClientData.class, this::testConnection)
                .eventEquals(StateTimeout(), BaseClientData.class, (state, data) -> {
                    if (ConnectivityStatus.OPEN == data.getDesiredConnectionStatus()) {
                        log.info("Did not receive connect command within init-timeout, connecting");
                        final OpenConnection openConnection = OpenConnection.of(connectionId(), DittoHeaders.empty());
                        getSelf().tell(openConnection, getSelf());
                    } else if (ConnectivityStatus.CLOSED == data.getDesiredConnectionStatus()) {
                        log.info(
                                "Did not receive connect command within init-timeout, desired state is closed, going to disconnected state.");
                        return goTo(DISCONNECTED);
                    } else {
                        log.info(
                                "Did not receive connect command within init-timeout, desired state is {}, do nothing.",
                                data.getDesiredConnectionStatus());
                    }
                    return stay(); // handle self-told commands later
                });
    }

    /**
     * Creates the handler for messages in disconnected state. Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inDisconnectedState() {
        return matchEvent(OpenConnection.class, BaseClientData.class, this::openConnection)
                .event(TestConnection.class, BaseClientData.class, this::testConnection);
    }

    /**
     * Creates the handler for messages in connecting state. Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectingState() {
        return matchEventEquals(StateTimeout(), BaseClientData.class, (event, data) -> this.connectionTimedOut(data))
                .event(ConnectionFailure.class, BaseClientData.class, this::connectingConnectionFailed)
                .event(ClientConnected.class, BaseClientData.class, this::clientConnected)
                .event(CloseConnection.class, BaseClientData.class, this::closeConnection);
    }

    /**
     * Creates the handler for messages in connected state. Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectedState() {
        return matchEvent(CloseConnection.class, BaseClientData.class, this::closeConnection)
                .event(OpenConnection.class, BaseClientData.class, this::connectionAlreadyOpen)
                .event(ConnectionFailure.class, BaseClientData.class, this::connectedConnectionFailed);
    }

    /**
     * Creates the handler for messages in disconnected state. Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inDisconnectingState() {
        return matchEventEquals(StateTimeout(), BaseClientData.class, (event, data) -> this.connectionTimedOut(data))
                .event(ConnectionFailure.class, BaseClientData.class, this::connectingConnectionFailed)
                .event(ClientDisconnected.class, BaseClientData.class, this::clientDisconnected);
    }

    /**
     * Creates the handler for messages in testing state. Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inTestingState() {
        return matchEvent(Status.Status.class, (e, d) -> Objects.equals(getSender(), getSelf()),
                (status, data) -> {
                    final Status.Status answerToPublish = getStatusToReport(status);
                    data.getSessionSender().ifPresent(sender -> sender.tell(answerToPublish, getSelf()));
                    return stop();
                })
                .eventEquals(StateTimeout(), BaseClientData.class, (stats, data) -> {
                    log.info("test timed out.");
                    data.getSessionSender().ifPresent(sender -> {
                        final DittoRuntimeException error = ConnectionFailedException.newBuilder(connectionId())
                                .description(String.format("Failed to open requested connection within <%d> seconds!",
                                        clientConfig.getTestingTimeout().getSeconds()))
                                .dittoHeaders(data.getSessionHeaders())
                                .build();
                        sender.tell(new Status.Failure(error), getSelf());
                    });
                    return stop();
                });
    }

    private State<BaseClientState, BaseClientData> onUnknownEvent(final Object event,
            final BaseClientData state) {

        Object message = event;
        if (event instanceof Failure) {
            message = ((Failure) event).cause();
        }
        if (event instanceof Status.Failure) {
            message = ((Status.Failure) event).cause();
        }

        if (message instanceof Throwable) {
            log.error((Throwable) message, "received Exception {} in state {} - status: {} - sender: {}",
                    message,
                    stateName(),
                    state.getConnectionStatus() + ": " + state.getConnectionStatusDetails().orElse(""),
                    getSender());
        } else {
            log.warning("received unknown/unsupported message {} in state {} - status: {} - sender: {}",
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
        return goToDisconnecting().using(setSession(data, sender, closeConnection.getDittoHeaders())
                .setDesiredConnectionStatus(ConnectivityStatus.CLOSED)
                .setConnectionStatusDetails("closing or deleting connection at " + Instant.now()));
    }

    private FSM.State<BaseClientState, BaseClientData> openConnection(final OpenConnection openConnection,
            final BaseClientData data) {

        final ActorRef sender = getSender();
        final Connection connection = data.getConnection();
        final DittoHeaders dittoHeaders = openConnection.getDittoHeaders();
        reconnectTimeoutStrategy.reset();
        final Duration connectingTimeout = clientConfig.getConnectingMinTimeout();
        if (canConnectViaSocket(connection)) {
            doConnectClient(connection, sender);
            return goToConnecting(connectingTimeout).using(setSession(data, sender, dittoHeaders));
        } else {
            cleanupResourcesForConnection();
            final DittoRuntimeException error = newConnectionFailedException(data.getConnection(), dittoHeaders);
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

    private void reconnect(final BaseClientData data) {
        log.debug("Trying to reconnect.");
        connectionLogger.success("Trying to reconnect.");
        final ActorRef sender = data.getSessionSender().orElse(null);
        final Connection connection = data.getConnection();
        if (canConnectViaSocket(connection)) {
            doConnectClient(connection, sender);
        } else {
            log.info("Socket is closed, scheduling a reconnect.");
            cleanupResourcesForConnection();
        }
    }

    private FSM.State<BaseClientState, BaseClientData> testConnection(final TestConnection testConnection,
            final BaseClientData data) {

        final ActorRef self = getSelf();
        final ActorRef sender = getSender();
        final Connection connection = testConnection.getConnection();

        if (!canConnectViaSocket(connection)) {
            final ConnectionFailedException connectionFailedException =
                    newConnectionFailedException(connection, testConnection.getDittoHeaders());
            final Status.Status failure = new Status.Failure(connectionFailedException);
            getSelf().tell(failure, self);
        } else {
            final CompletionStage<Status.Status> connectionStatusStage = doTestConnection(connection);
            final CompletionStage<Status.Status> mappingStatusStage =
                    testMessageMappingProcessor(connection.getMappingContext().orElse(null));

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
                .setConnection(connection)
                .setConnectionStatusDetails("Testing connection since " + Instant.now()));
    }

    private FSM.State<BaseClientState, BaseClientData> connectionTimedOut(final BaseClientData data) {

        data.getSessionSender().ifPresent(sender -> {
            final DittoRuntimeException error = ConnectionFailedException.newBuilder(connectionId())
                    .dittoHeaders(data.getSessionHeaders())
                    .build();
            sender.tell(new Status.Failure(error), getSelf());
        });
        cleanupResourcesForConnection();
        cleanupFurtherResourcesOnConnectionTimeout(stateName());

        final String timeoutMessage = "Connection timed out at " + Instant.now() + " while " + stateName() + ".";

        if (ConnectivityStatus.OPEN.equals(data.getDesiredConnectionStatus())) {
            if (reconnectTimeoutStrategy.canReconnect()) {
                reconnect(data);
                return goToConnecting(reconnectTimeoutStrategy.getNextTimeout()).using(data.resetSession()
                        .setConnectionStatus(ConnectivityStatus.FAILED)
                        .setConnectionStatusDetails(timeoutMessage + " Will try to reconnect."));
            } else {
                connectionLogger.failure(
                        "Connection timed out. Reached maximum tries and thus will no longer try to reconnect.");
                log.info(
                        "Connection <{}> reached maximum retries for reconnecting and thus will no longer try to reconnect.",
                        connectionId());

                return goTo(UNKNOWN).using(data.resetSession()
                        .setConnectionStatus(ConnectivityStatus.FAILED)
                        .setConnectionStatusDetails(timeoutMessage +
                                " Reached maximum retries and thus will not try to reconnect any longer."));
            }
        }

        connectionLogger.failure("Connection timed out.");
        return goTo(UNKNOWN).using(data.resetSession()
                .setConnectionStatus(ConnectivityStatus.FAILED)
                .setConnectionStatusDetails(timeoutMessage));
    }

    private State<BaseClientState, BaseClientData> clientConnected(final ClientConnected clientConnected,
            final BaseClientData data) {
        return ifEventUpToDate(clientConnected, () -> {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId());
            allocateResourcesOnConnection(clientConnected);
            return stay().using(data);
        });
    }

    private State<BaseClientState, BaseClientData> resourceReady(final ResourceReady resourceReady,
            final BaseClientData data) {
        ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId());

        log.debug("Resource initialized: {}.", resourceReady);

        final BaseClientData currentState =
                data.setInitializationState(data.getInitializationState().resourceReady(resourceReady));

        if (resourceReady.isPublisher()) {
            // tell all message mapping actors that the publisher is ready now
            getMessageMappingProcessorActor().tell(new Broadcast(resourceReady), getSelf());
        }

        if (currentState.getInitializationState().isFinished()) {
            connectionLogger.success("Connection successful.");
            data.getSessionSender().ifPresent(origin -> origin.tell(new Status.Success(CONNECTED), getSelf()));
            return goTo(CONNECTED).using(data.resetSession()
                    .setConnectionStatus(ConnectivityStatus.OPEN)
                    .setConnectionStatusDetails("Connected at " + Instant.now()));
        } else {
            log.debug("Initialization in progress, current state {}.", currentState.getInitializationState());
            return stay().using(currentState);
        }

    }

    private State<BaseClientState, BaseClientData> clientDisconnected(final ClientDisconnected event,
            final BaseClientData data) {

        return ifEventUpToDate(event, () -> {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId());
            connectionLogger.success("Disconnected successfully.");

            cleanupResourcesForConnection();
            data.getSessionSender().ifPresent(sender -> sender.tell(new Status.Success(DISCONNECTED), getSelf()));
            return goTo(DISCONNECTED).using(data.resetSession()
                    .setConnectionStatus(ConnectivityStatus.CLOSED)
                    .setConnectionStatusDetails("Disconnected at " + Instant.now()));
        });
    }

    private State<BaseClientState, BaseClientData> connectingConnectionFailed(final ConnectionFailure event,
            final BaseClientData data) {
        return ifEventUpToDate(event, () -> {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId());
            final Status.Status statusToReport = getStatusToReport(event.getFailure());

            cleanupResourcesForConnection();
            data.getSessionSender().ifPresent(sender -> sender.tell(statusToReport, getSelf()));

            return backoffAfterFailure(event, data);
        });
    }

    private State<BaseClientState, BaseClientData> connectedConnectionFailed(final ConnectionFailure event,
            final BaseClientData data) {
        return ifEventUpToDate(event, () -> {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId());

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
        if (ConnectivityStatus.OPEN.equals(data.getDesiredConnectionStatus())) {
            if (reconnectTimeoutStrategy.canReconnect()) {
                final Duration nextBackoff = reconnectTimeoutStrategy.getNextBackoff();
                final String errorMessage =
                        String.format("Connection failed due to: {0}. Will reconnect after %s.", nextBackoff);
                connectionLogger.failure(errorMessage, event.getFailureDescription());
                log.info("Connection failed: {}. Reconnect after {}.", event, nextBackoff);
                return goToConnecting(nextBackoff).using(data.resetSession()
                        .setConnectionStatus(ConnectivityStatus.FAILED)
                        .setConnectionStatusDetails(event.getFailureDescription()));
            } else {
                connectionLogger.failure(
                        "Connection failed due to: {0}. Reached maximum tries and thus will no longer try to reconnect.",
                        event.getFailureDescription());
                log.info(
                        "Connection <{}> reached maximum retries for reconnecting and thus will no longer try to reconnect.",
                        connectionId());

                // stay in UNKNOWN state until re-opened manually
                return goTo(UNKNOWN).using(data.resetSession()
                        .setConnectionStatus(ConnectivityStatus.FAILED)
                        .setConnectionStatusDetails(event.getFailureDescription()
                                + " Reached maximum retries and thus will not try to reconnect any longer."));
            }

        }

        connectionLogger.failure("Connection failed due to: {0}.", event.getFailureDescription());
        return goTo(UNKNOWN)
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
            log.warning("Received stale event <{}> at state <{}>", event, state);
            return stay();
        }
    }

    private FSM.State<BaseClientState, BaseClientData> retrieveConnectionStatus(final RetrieveConnectionStatus command,
            final BaseClientData data) {

        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, command, command.getConnectionId());
        log.debug("Received RetrieveConnectionStatus message from {}, forwarding to consumers and publishers.",
                getSender());

        // send to all children (consumers, publishers, except mapping actor)
        getContext().getChildren().forEach(child -> {
            if (messageMappingProcessorActor != child) {
                log.debug("Forwarding RetrieveAddressStatus to child: {}", child.path());
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

        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, command, command.getConnectionId());
        log.debug("Received RetrieveConnectionMetrics message, gathering metrics.");
        final DittoHeaders dittoHeaders = command.getDittoHeaders().toBuilder()
                .source(getInstanceIdentifier())
                .build();

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

        this.getSender().tell(retrieveConnectionMetricsResponse, this.getSelf());
        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> resetConnectionMetrics(final ResetConnectionMetrics command,
            final BaseClientData data) {

        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, command, command.getConnectionId());
        log.debug("Received ResetConnectionMetrics message, resetting metrics.");
        //  TODO: think about just clearing the counters instead of completely rebuilding them
        connectionCounterRegistry.resetForConnection(data.getConnection());
        connectionCounterRegistry.initForConnection(data.getConnection());

        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> enableConnectionLogs(
            final EnableConnectionLogs command) {

        final String connectionId = command.getConnectionId();
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, command, connectionId);
        log.debug("Received EnableConnectionLogs message, enabling logs.");

        this.connectionLoggerRegistry.unmuteForConnection(connectionId);

        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> checkLoggingActive(
            final CheckConnectionLogsActive command) {

        final String connectionId = command.getConnectionId();
        final Instant timestamp = command.getTimestamp();
        ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
        log.debug("Received checkLoggingActive message, check if Logging for connection <{}> is expired.",
                connectionId);

        if (this.connectionLoggerRegistry.isLoggingExpired(connectionId, timestamp)) {
            this.connectionLoggerRegistry.muteForConnection(connectionId);
            getSender().tell(LoggingExpired.of(connectionId), ActorRef.noSender());
        }

        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> retrieveConnectionLogs(final RetrieveConnectionLogs command) {

        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, command, command.getConnectionId());
        log.debug("Received RetrieveConnectionLogs message, gathering metrics.");
        final DittoHeaders dittoHeaders = command.getDittoHeaders().toBuilder()
                .source(getInstanceIdentifier())
                .build();

        final ConnectionLoggerRegistry.ConnectionLogs connectionLogs =
                connectionLoggerRegistry.aggregateLogs(connectionId());

        getSender().tell(
                RetrieveConnectionLogsResponse.of(connectionId(), connectionLogs.getLogs(),
                        connectionLogs.getEnabledSince(),
                        connectionLogs.getEnabledUntil(), dittoHeaders),
                getSelf());

        return stay();
    }

    private FSM.State<BaseClientState, BaseClientData> resetConnectionLogs(
            final ResetConnectionLogs command,
            final BaseClientData data) {

        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, command, command.getConnectionId());
        log.debug("Received ResetConnectionLogs message, resetting logs.");

        this.connectionLoggerRegistry.resetForConnection(data.getConnection());

        this.connectionLoggerRegistry.forConnection(data.getConnectionId())
                .success(InfoProviderFactory.forSignal(command), "Successfully reset the logs.");

        return stay();
    }

    private static ConnectionFailedException newConnectionFailedException(final Connection connection,
            final DittoHeaders dittoHeaders) {

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
                        ? ((Signal) signal).getType()
                        : "unknown"; // no need to disclose Java class of signal to clients
                return ConnectionSignalIllegalException.newBuilder(connectionId())
                        .illegalSignalForState(signalType, state.name().toLowerCase())
                        .dittoHeaders(headers)
                        .build();
        }
    }

    private boolean canConnectViaSocket(final Connection connection) {
        return checkHostAndPortForAvailability(connection.getHostname(), connection.getPort());
    }

    private boolean checkHostAndPortForAvailability(final String host, final int port) {
        try (final Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), SOCKET_CHECK_TIMEOUT_MS);
            return true;
        } catch (final IOException | IllegalArgumentException ex) {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId());
            connectionLogger.failure("Socket could not be opened for {0}:{1,number,#} due to {2}", host, port,
                    ex.getMessage());

            log.warning("Socket could not be opened for <{}:{}> due to <{}:{}>", host, port,
                    ex.getClass().getCanonicalName(), ex.getMessage());
        }
        return false;
    }

    private void handleOutboundSignal(final OutboundSignal signal) {
        enhanceLogUtil(signal.getSource());
        final Object msg = new ConsistentHashingRouter.ConsistentHashableEnvelope(signal, signal.getSource().getId());
        messageMappingProcessorActor.tell(msg, getSender());
    }

    private void enhanceLogUtil(final WithDittoHeaders<?> signal) {
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, signal, connectionId());
    }

    private Instant getInConnectionStatusSince() {
        return stateData().getInConnectionStatusSince();
    }

    private CompletionStage<Status.Status> testMessageMappingProcessor(@Nullable final MappingContext mappingContext) {
        try {
            return tryToConfigureMessageMappingProcessor(mappingContext);
        } catch (final DittoRuntimeException dre) {
            final String logMessage = MessageFormat.format(
                    "Got DittoRuntimeException during initialization of MessageMappingProcessor: {0} {1} - desc: {2}",
                    dre.getClass().getSimpleName(), dre.getMessage(), dre.getDescription().orElse(""));
            connectionLogger.failure(logMessage);
            log.info(logMessage);
            getSender().tell(dre, getSelf());
            return CompletableFuture.completedFuture(new Status.Failure(dre));
        }
    }

    private CompletionStage<Status.Status> tryToConfigureMessageMappingProcessor(
            @Nullable final MappingContext mappingContext) {

        final ActorSystem actorSystem = getContext().getSystem();

        // this one throws DittoRuntimeExceptions when the mapper could not be configured
        MessageMappingProcessor.of(connectionId(), mappingContext, actorSystem, connectivityConfig,
                protocolAdapterProvider, log);
        return CompletableFuture.completedFuture(new Status.Success("mapping"));
    }

    /**
     * Starts the {@link MessageMappingProcessorActor} responsible for payload transformation/mapping as child actor
     * behind a (cluster node local) RoundRobin pool and a dynamic resizer from the current mapping context.
     *
     * @return {@link org.eclipse.ditto.services.connectivity.messaging.MessageMappingProcessorActor} or exception,
     * which will also cause an sideeffect that stores the mapping actor in the local variable {@code
     * messageMappingProcessorActor}.
     */
    private ActorRef startMessageMappingProcessorActor() {
        final MappingContext mappingContext = stateData().getConnection().getMappingContext().orElse(null);

        final Connection connection = connection();

        final MessageMappingProcessor processor;
        try {
            // this one throws DittoRuntimeExceptions when the mapper could not be configured
            processor = MessageMappingProcessor.of(connectionId(), mappingContext, getContext().getSystem(),
                    connectivityConfig, protocolAdapterProvider, log);
        } catch (final DittoRuntimeException dre) {
            connectionLogger.failure("Failed to start message mapping processor due to: {}.", dre.getMessage());
            log.info(
                    "Got DittoRuntimeException during initialization of MessageMappingProcessor: {} {} - desc: {}",
                    dre.getClass().getSimpleName(), dre.getMessage(), dre.getDescription().orElse(""));
            getSender().tell(dre, getSelf());
            throw dre;
        }

        log.info("Configured for processing messages with the following MessageMapperRegistry: <{}>",
                processor.getRegistry());

        log.debug("Starting MessageMappingProcessorActor with pool size of <{}>.",
                connection.getProcessorPoolSize());
        final Props props = MessageMappingProcessorActor.props(conciergeForwarder, processor, connectionId());

        /*
         * By using a ConsistentHashingPool, messages sent to this actor which are wrapped into
         * akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope may define which consistent hashing
         * key to use.
         * That way the message with the same hash are always sent to the same pooled instance of the
         * MessageMappingProcessorActor.
         *
         * That is needed in order to guarantee message processing order. Otherwise two messages received for the
         * same Thing may be processed out-of-order if the mapping of the first message takes longer than the
         * mapping of the second message.
         * This however will also limit throughput as the used hashing key is often connection source address based
         * and does not yet "know" of the Thing ID.
         */
        return getContext().actorOf(new ConsistentHashingPool(connection.getProcessorPoolSize())
                .withDispatcher("message-mapping-processor-dispatcher")
                .props(props), MessageMappingProcessorActor.ACTOR_NAME);
    }

    protected boolean isDryRun() {
        return TESTING.equals(stateName());
    }

    private String nextChildActorName(final String prefix) {
        return prefix + ++childActorCount;
    }

    private BaseClientData setSession(final BaseClientData data, @Nullable final ActorRef sender,
            @Nullable final DittoHeaders headers) {
        if (!Objects.equals(sender, getSelf()) && !Objects.equals(sender, getContext().system().deadLetters())) {
            return data.setSessionSender(sender)
                    .setSessionHeaders(headers);
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

    protected void notifyConsumersReady() {
        getSelf().tell(ResourceReady.consumersReady(), getSelf());
    }
    /**
     * Add meaningful message to status for reporting.
     *
     * @param status status to report.
     * @return status with meaningful message.
     */
    private Status.Status getStatusToReport(final Status.Status status) {
        final Status.Status answerToPublish;
        if (status instanceof Status.Failure) {
            final Status.Failure failure = (Status.Failure) status;
            log.info("{} failed: <{}>", stateName(), failure.cause());
            if (!(failure.cause() instanceof DittoRuntimeException)) {
                final DittoRuntimeException error = ConnectionFailedException.newBuilder(connectionId())
                        .description(describeEventualCause(failure.cause()))
                        .dittoHeaders(stateData().getSessionHeaders())
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

    private static String describeEventualCause(@Nullable final Throwable throwable) {
        if (null == throwable) {
            return "Unkown cause.";
        }
        final Throwable cause = throwable.getCause();
        if (cause == null || cause == throwable) {
            return "Cause: " + throwable.getMessage();
        } else {
            return describeEventualCause(cause);
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
    static class DuplicationReconnectTimeoutStrategy implements ReconnectTimeoutStrategy {

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

        DuplicationReconnectTimeoutStrategy(final Duration minTimeout, final Duration maxTimeout,
                final int maxTries, final Duration minBackoff, final Duration maxBackoff) {
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
            this.currentTimeout = this.minTimeout;
            this.nextBackoff = this.minBackoff;
            this.currentTries = 0;
        }

        @Override
        public Duration getNextTimeout() {
            this.increaseTimeoutAfterRecovery();
            return this.currentTimeout;
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
         *
         * Simply resetting the timeout strategy on connection success is not sufficient,
         * because AMQP 1.0 connections can enter CONNECTED state and then fail immediately
         * if source or target addresses are misconfigured--the broker would reject requests
         * to create message consumers and publishers. If this strategy is reset on entering
         * CONNECTED, then those misconfigured connections do not experience exponential
         * backoff; reconnection would happen every minimum backoff duration forever.
         *
         * This recovery strategy is based on the duration D between timeouts, which are
         * also caused by errors. If D is larger than a threshold, then the connection is considered
         * stable and the timeout strategy is reset. Otherwise the connection is considered unstable
         * and retry counter will count up until we give up for good.
         *
         * The recovery threshold is chosen to be 2*(maxTimeout + maxBackoff) so that after this much
         * time, the connection has stayed open without errors for at least (maxTimeout + maxBackoff).
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

        private static Duration maxDuration(final Duration d1, final Duration d2) {
            return isLonger(d2, d1) ? d2 : d1;
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
}
