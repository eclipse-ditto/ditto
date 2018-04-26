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
import static org.eclipse.ditto.services.connectivity.messaging.BaseClientState.CONNECTED;
import static org.eclipse.ditto.services.connectivity.messaging.BaseClientState.CONNECTING;
import static org.eclipse.ditto.services.connectivity.messaging.BaseClientState.DISCONNECTED;
import static org.eclipse.ditto.services.connectivity.messaging.BaseClientState.DISCONNECTING;
import static org.eclipse.ditto.services.connectivity.messaging.BaseClientState.FAILED;
import static org.eclipse.ditto.services.connectivity.messaging.DittoHeadersFilter.Mode.EXCLUDE;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectClient;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.DisconnectClient;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressMetric;
import org.eclipse.ditto.services.connectivity.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.CommandRouterPropsFactory;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ConnectivityModifyCommand;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ModifyConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;

import com.typesafe.config.Config;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Pair;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.japi.pf.FSMTransitionHandlerBuilder;
import akka.pattern.PatternsCS;
import akka.routing.DefaultResizer;
import akka.routing.RoundRobinPool;
import akka.util.Timeout;
import scala.concurrent.duration.Duration;

/**
 * Base class for ClientActors which implement the connection handling for various connectivity protocols.
 * <p>
 * The actor expects to receive a {@link CreateConnection} command after it was started. If this command is not received
 * within timeout (can be the case when this actor is remotely deployed after the command was sent) the actor requests
 * the required information from ConnectionActor.
 * </p>
 */
public abstract class BaseClientActor extends AbstractFSM<BaseClientState, BaseClientData> {

    private static final int CONNECTING_TIMEOUT = 10;
    protected static final int RETRIEVE_METRICS_TIMEOUT = 2;

    private static final int SOCKET_CHECK_TIMEOUT_MS = 2000;

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final List<String> headerBlacklist;
    private final ActorRef commandRouter;

    @Nullable private ActorRef messageMappingProcessorActor;

    private long consumedMessageCounter = 0L;
    private long publishedMessageCounter = 0L;

    protected BaseClientActor(final Connection connection, final ConnectionStatus desiredConnectionStatus,
            @Nullable final ActorRef commandRouter) {
        checkNotNull(connection, "connection");
        final Config config = getContext().getSystem().settings().config();
        final java.time.Duration initTimeout = config.getDuration(ConfigKeys.Client.INIT_TIMEOUT);
        headerBlacklist = config.getStringList(ConfigKeys.Message.HEADER_BLACKLIST);

        if (commandRouter != null) {
            this.commandRouter = commandRouter;
        } else {
            this.commandRouter = getContext().actorOf(CommandRouterPropsFactory.getProps(config), "commandRouter");
        }

        startWith(DISCONNECTED, new BaseClientData(connection.getId(), connection, ConnectionStatus.UNKNOWN,
                desiredConnectionStatus, "initialized", Instant.now(), null));

        when(DISCONNECTED, Duration.fromNanos(initTimeout.toNanos()),
                inDisconnectedState(initTimeout));
        when(CONNECTING, Duration.create(CONNECTING_TIMEOUT, TimeUnit.SECONDS),
                inConnectingState());
        when(CONNECTED,
                inConnectedState());
        when(DISCONNECTING, Duration.create(CONNECTING_TIMEOUT, TimeUnit.SECONDS),
                inDisconnectingState());
        when(FAILED,
                inFailedState());

        onTransition(handleTransitions());

        whenUnhandled(unhandledHandler(connection.getId()).
                anyEvent((event, state) -> {
                    log.warning("received unknown/unsupported message {} in state {} - status: {}",
                            event, stateName(),
                            state.getConnectionStatus() + ": " +
                                    state.getConnectionStatusDetails().orElse(""));
                    return stay();
                }));

        initialize();
    }

    private FSMTransitionHandlerBuilder<BaseClientState> handleTransitions() {
        return matchState(DISCONNECTED, CONNECTING, this::onTransition)
                .state(DISCONNECTED, CONNECTED, this::onTransition)
                .state(DISCONNECTED, DISCONNECTING, this::onTransition)
                .state(DISCONNECTED, FAILED, this::onTransition)
                .state(CONNECTING, CONNECTED, this::onTransition)
                .state(CONNECTING, DISCONNECTING, this::onTransition)
                .state(CONNECTING, DISCONNECTED, this::onTransition)
                .state(CONNECTING, CONNECTING, this::onTransition)
                .state(CONNECTING, FAILED, this::onTransition)
                .state(CONNECTED, CONNECTING, this::onTransition)
                .state(CONNECTED, DISCONNECTING, this::onTransition)
                .state(CONNECTED, DISCONNECTED, this::onTransition)
                .state(CONNECTED, FAILED, this::onTransition)
                .state(DISCONNECTING, CONNECTING, this::onTransition)
                .state(DISCONNECTING, CONNECTED, this::onTransition)
                .state(DISCONNECTING, DISCONNECTED, this::onTransition)
                .state(DISCONNECTING, FAILED, this::onTransition)
                .state(FAILED, CONNECTING, this::onTransition)
                .state(FAILED, CONNECTED, this::onTransition)
                .state(FAILED, DISCONNECTING, this::onTransition)
                .state(FAILED, DISCONNECTED, this::onTransition);
    }

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inDisconnectedState(
            final java.time.Duration initTimeout) {
        return matchEvent(Arrays.asList(CloseConnection.class, DeleteConnection.class), BaseClientData.class,
                (event, data) -> stay().using(data
                        .setOrigin(getSender())
                        .setDesiredConnectionStatus(ConnectionStatus.CLOSED)
                ).replying(new Status.Success(DISCONNECTED))
        )
                .eventEquals(StateTimeout(), BaseClientData.class, (state, data) -> {
                    if (data.getDesiredConnectionStatus() == ConnectionStatus.OPEN) {
                        log.info("Did not receive connect command within {}, trying to go to CONNECTING",
                                initTimeout);
                        return goTo(CONNECTING);
                    } else {
                        // desired is not OPEN, so stay:
                        return stay();
                    }
                })
                .event(TestConnection.class, BaseClientData.class, (testConnection, data) -> {
                    final Connection connection = testConnection.getConnection();
                    if (!canConnectViaSocket(connection, testConnection.getDittoHeaders())) {
                        return stop();
                    }

                    try {
                        final CompletionStage<Status.Status> connectionStatusStage =
                                doTestConnection(connection);
                        final CompletionStage<Status.Status> mappingStatusStage =
                                testMessageMappingProcessor(connection.getMappingContext().orElse(null));

                        final ActorRef sender = getSender();
                        connectionStatusStage.toCompletableFuture()
                                .thenCombine(mappingStatusStage, (connectionStatus, mappingStatus) -> {
                                    if (connectionStatus instanceof Status.Success && mappingStatus instanceof Status.Success) {
                                        return new Status.Success("successfully connected + initialized mapper");
                                    } else if (connectionStatus instanceof Status.Failure) {
                                        return connectionStatus;
                                    } else {
                                        return mappingStatus;
                                    }
                                }).thenAccept(testStatus -> sender.tell(testStatus, getSelf()));
                    } catch (final DittoRuntimeException e) {
                        getSender().tell(new Status.Failure(e), getSelf());
                    }
                    return stop();
                })
                .event(CreateConnection.class, BaseClientData.class, (createConnection, data) -> {
                    final Connection connection = createConnection.getConnection();
                    canConnectViaSocket(connection, createConnection.getDittoHeaders());

                    return goTo(baseClientStateFromConnectionStatus(connection.getConnectionStatus()))
                            .using(data
                                    .setConnection(connection)
                                    .setDesiredConnectionStatus(connection.getConnectionStatus())
                                    .setConnectionStatusDetails("creating connection at " + Instant.now())
                                    .setOrigin(getSender())
                            );
                })
                .event(OpenConnection.class, BaseClientData.class, (openConnection, data) ->
                        goTo(CONNECTING).using(data.setOrigin(getSender()))
                );
    }

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectingState() {
        return matchEvent(
                Arrays.asList(CreateConnection.class, OpenConnection.class), BaseClientData.class, (event, data) ->
                        stay().using(data.setOrigin(getSender()))
        )
                .event(Arrays.asList(CloseConnection.class, DeleteConnection.class), BaseClientData.class,
                        (event, data) ->
                                goTo(DISCONNECTING).using(data
                                        .setDesiredConnectionStatus(ConnectionStatus.CLOSED)
                                        .setConnectionStatusDetails(
                                                "closing or deleting connection at " + Instant.now())
                                        .setOrigin(getSender())
                                )
                )
                .eventEquals(StateTimeout(), BaseClientData.class, (event, data) -> {
                    if (data.getConnectionStatus() == ConnectionStatus.FAILED) {
                        // if the status is already in FAILED, keep the status + detail:
                        return goTo(CONNECTING); // re-trigger connecting
                    } else {
                        return goTo(CONNECTING).using(data // re-trigger connecting
                                .setConnectionStatus(ConnectionStatus.FAILED)
                                .setConnectionStatusDetails("Connecting timed out at " + Instant.now())
                        );
                    }
                });
    }

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectedState() {
        return matchEvent(
                Arrays.asList(CloseConnection.class, DeleteConnection.class), BaseClientData.class, (event, data) ->
                        goTo(DISCONNECTING).using(data
                                .setOrigin(getSender())
                                .setDesiredConnectionStatus(ConnectionStatus.CLOSED)
                        )
        )
                .event(OpenConnection.class, BaseClientData.class, (openConnection, data) ->
                        // interpret as reconnecting
                        goTo(CONNECTING).using(data.setOrigin(getSender()))
                );
    }

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inDisconnectingState() {
        return matchEvent(
                Arrays.asList(CloseConnection.class, DeleteConnection.class), BaseClientData.class, (event, data) ->
                        stay()
        )
                .eventEquals(StateTimeout(), BaseClientData.class, (event, data) ->
                        goTo(CONNECTED).using(data
                                .setConnectionStatus(ConnectionStatus.OPEN)
                                .setConnectionStatusDetails(
                                        "Disconnecting timed out, still connected at " + Instant.now())
                                .setOrigin(getSender())
                        )
                );
    }

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inFailedState() {
        return matchEvent(OpenConnection.class, BaseClientData.class, (event, data) -> {
                    if (data.getDesiredConnectionStatus() == ConnectionStatus.OPEN) {
                        return goTo(CONNECTING).using(data.setOrigin(getSender()));
                    } else {
                        return stay();
                    }
                }
        );
    }

    /**
     * Creates the handler for unhandled messages to this actor.
     * <p>
     * Overwrite and extend by additional matchers.
     * </p>
     *
     * @param connectionId the connection ID
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> unhandledHandler(final String connectionId) {
        return matchEvent(RetrieveConnectionMetrics.class, BaseClientData.class, (command, data) -> stay()
                .using(data.setOrigin(getSender()))
                .replying(RetrieveConnectionMetricsResponse.of(
                        connectionId,
                        ConnectivityModelFactory.newConnectionMetrics(
                                getCurrentConnectionStatus(), getCurrentConnectionStatusDetails().orElse(null),
                                getInConnectionStatusSince(), stateName().name(),
                                getCurrentSourcesMetrics(), getCurrentTargetsMetrics()),
                        command.getDittoHeaders().toBuilder()
                                .source(org.eclipse.ditto.services.utils.config.ConfigUtil.calculateInstanceUniqueSuffix())
                                .build())
                )
        )
                .event(ModifyConnection.class, BaseClientData.class, (modifyConnection, data) -> {
                    final Connection connection = modifyConnection.getConnection();
                    return goTo(baseClientStateFromConnectionStatus(connection.getConnectionStatus()))
                            .using(data
                                    .setConnection(connection)
                                    .setDesiredConnectionStatus(connection.getConnectionStatus())
                                    .setConnectionStatusDetails("modifying connection at " + Instant.now())
                                    .setOrigin(getSender())
                            );
                })
                .event(ConnectClient.class, BaseClientData.class, (connectClient, data) -> shouldBeConnecting(data) ?
                        goTo(CONNECTING).using(data.setOrigin(getSender())) :
                        goTo(DISCONNECTING).using(data.setOrigin(getSender())))
                .event(DisconnectClient.class, BaseClientData.class,
                        (disconnectClient, data) -> goTo(DISCONNECTING).using(data.setOrigin(getSender())))
                .event(ClientConnected.class, BaseClientData.class, this::handleClientConnected)
                .event(ClientDisconnected.class, BaseClientData.class, this::handleClientDisconnected)
                .event(ConnectionFailure.class, BaseClientData.class, this::handleConnectionFailure)
                .event(ConnectivityModifyCommand.class, BaseClientData.class, (command, data) -> {
                    cannotHandle(command, data.getConnection());
                    return stay().using(data.setOrigin(getSender()));
                })
                .event(Signal.class, BaseClientData.class, (signal, data) -> {
                    handleSignal(signal);
                    return stay();
                })
                .event(ExternalMessage.class, BaseClientData.class, (externalMessage, data) -> {
                    handleExternalMessage(externalMessage);
                    return stay();
                })
                .event(Status.Success.class, BaseClientData.class, (success, data) -> {
                    log.info("Got Status.Success: {}", success);
                    return stay();
                });
    }

    private static BaseClientState baseClientStateFromConnectionStatus(final ConnectionStatus connectionStatus) {
        return connectionStatus == ConnectionStatus.OPEN ? CONNECTING : DISCONNECTING;
    }

    private boolean canConnectViaSocket(final Connection connection, final DittoHeaders dittoHeaders) {
        if (!checkHostAndPortForAvailability(connection.getHostname(), connection.getPort())) {
            final ConnectionFailedException connectionFailedException = ConnectionFailedException
                    .newBuilder(connection.getId())
                    .dittoHeaders(dittoHeaders)
                    .description("Could not establish a connection on '" +
                            connection.getHostname() + ":" + connection.getPort() + "'. Make sure the " +
                            "endpoint is reachable and that no firewall prevents the connection.")
                    .build();
            getSender().tell(new Status.Failure(connectionFailedException), getSelf());
            return false;
        }
        return true;
    }

    private boolean checkHostAndPortForAvailability(final String host, final int port) {
        try (final Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), SOCKET_CHECK_TIMEOUT_MS);
            return true;
        } catch (final IOException ex) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.warning("Socket could not be opened for <{}:{}>", host, port);
        }
        return false;
    }

    private State<BaseClientState, BaseClientData> handleClientConnected(final ClientConnected clientConnected,
            final BaseClientData data) {

        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
        startMessageMappingProcessor(data.getConnection().getMappingContext().orElse(null));
        onClientConnected(clientConnected, data);
        return goTo(CONNECTED).using(data
                .setConnectionStatus(ConnectionStatus.OPEN)
                .setConnectionStatusDetails("Connected at " + Instant.now())
                .setOrigin(getSender())
        ).replying(new Status.Success(CONNECTED));
    }

    private State<BaseClientState, BaseClientData> handleClientDisconnected(final ClientDisconnected event,
            final BaseClientData data) {

        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
        stopMessageMappingProcessorActor();
        onClientDisconnected(event, data);
        return goTo(DISCONNECTED).using(data
                .setConnectionStatus(ConnectionStatus.CLOSED)
                .setConnectionStatusDetails("Disconnected at " + Instant.now())
                .setOrigin(getSender())
        ).replying(new Status.Success(DISCONNECTED));
    }

    private State<BaseClientState, BaseClientData> handleConnectionFailure(final ConnectionFailure event,
            final BaseClientData data) {

        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
        onConnectionFailure(event, data);
        return stay().using(data
                .setConnectionStatus(ConnectionStatus.FAILED)
                .setConnectionStatusDetails(event.getFailureDescription())
                .setOrigin(getSender())
        );
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
    protected void onTransition(final BaseClientState from, final BaseClientState to) {
        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
        log.info("Transition: {} -> {}", from, to);

        switch (to) {
            case CONNECTING:
                if (from == CONNECTED) {
                    // reconnect!
                    log.info("Triggering reconnection");
                    doReconnectClient(nextStateData().getConnection(), nextStateData().getOrigin().orElse(null));
                } else {
                    doConnectClient(nextStateData().getConnection(), nextStateData().getOrigin().orElse(null));
                }
                break;
            case DISCONNECTING:
                doDisconnectClient(nextStateData().getConnection(), nextStateData().getOrigin().orElse(null));
                break;
        }
    }

    /**
     * Handles {@link TestConnection} commands by returning a CompletionState of
     * {@link akka.actor.Status.Status Status} which may be {@link akka.actor.Status.Success Success} or
     * {@link akka.actor.Status.Failure Failure}.
     *
     * @param connection the Connection to test
     * @return the CompletionStage with the test result
     */
    protected abstract CompletionStage<Status.Status> doTestConnection(final Connection connection);

    /**
     * Called when this {@code Client} connected successfully.
     *
     * @param clientConnected the ClientConnected message which may be subclassed and thus adding more information
     * @param data the data of the current State
     */
    protected abstract void onClientConnected(final ClientConnected clientConnected, final BaseClientData data);

    /**
     * Called when this {@code Client} disconnected.
     *
     * @param clientDisconnected the ClientDisconnected message which may be subclassed and thus adding more information
     * @param data the data of the current State
     */
    protected abstract void onClientDisconnected(final ClientDisconnected clientDisconnected,
            final BaseClientData data);

    /**
     * @return the optional Actor to use for Publishing commandResponses/events
     */
    protected abstract Optional<ActorRef> getPublisherActor();

    /**
     * Called when this {@code Client} encountered a Failure.
     *
     * @param connectionFailure the ConnectionFailure message which may be subclassed and thus adding more information
     * @param data the data of the current State
     */
    protected void onConnectionFailure(final ConnectionFailure connectionFailure, final BaseClientData data) {
        connectionFailure.getOrigin().ifPresent(o -> o.tell(connectionFailure.getFailure(), getSelf()));
    }

    /**
     * Invoked when this {@code Client} should connect.
     *
     * @param connection the Connection to use for connecting
     * @param origin the ActorRef which caused the ConnectClient command
     */
    protected abstract void doConnectClient(final Connection connection, @Nullable final ActorRef origin);

    /**
     * Invoked when this {@code Client} should be reconnected.
     *
     * @param connection the Connection to use for reconnecting
     * @param origin the ActorRef which caused the ReconnectClient command
     */
    protected abstract void doReconnectClient(final Connection connection, @Nullable final ActorRef origin);

    /**
     * Invoked when this {@code Client} should disconnect.
     *
     * @param connection the Connection to use for disconnecting
     * @param origin the ActorRef which caused the DisconnectClient command
     */
    protected abstract void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin);

    /**
     * Retrieves the connection status of the passed {@link Source}.
     *
     * @param source the Source to retrieve the connection status for
     * @return the result as Map containing an entry for each source
     */
    protected abstract Map<String, AddressMetric> getSourceConnectionStatus(final Source source);

    /**
     * Retrieves the connection status of the passed {@link Target}.
     *
     * @param target the Target to retrieve the connection status for
     * @return the result as Map containing an entry for each target
     */
    protected abstract Map<String, AddressMetric> getTargetConnectionStatus(final Target target);

    /**
     * Retrieves the {@link AddressMetric} of a single address which is handled by a child actor with the passed
     * {@code childActorName}.
     *
     * @param addressIdentifier the identifier used as first entry in the Pair of CompletableFuture
     * @param childActorName the actor name of the child to ask for the AddressMetric
     * @return a CompletableFuture with the addressIdentifier and the retrieved AddressMetric
     */
    protected final CompletableFuture<Pair<String, AddressMetric>> retrieveAddressMetric(
            final String addressIdentifier, final String childActorName) {

        final Optional<ActorRef> childActor = getContext().findChild(childActorName);
        if (childActor.isPresent()) {
            final ActorRef actorRef = childActor.get();
            return PatternsCS.ask(actorRef, RetrieveAddressMetric.getInstance(),
                    Timeout.apply(RETRIEVE_METRICS_TIMEOUT, TimeUnit.SECONDS))
                    .handle((response, throwable) -> {
                        if (response != null) {
                            return Pair.create(addressIdentifier, (AddressMetric) response);
                        } else {
                            return Pair.create(addressIdentifier,
                                    ConnectivityModelFactory.newAddressMetric(
                                            ConnectionStatus.FAILED,
                                            throwable.getClass().getSimpleName() + ": " +
                                                    throwable.getMessage(),
                                            -1, Instant.now()));
                        }
                    }).toCompletableFuture();
        } else {
            log.warning("Consumer actor child <{}> was not found", childActorName);
            return CompletableFuture.completedFuture(Pair.create(addressIdentifier,
                    ConnectivityModelFactory.newAddressMetric(
                            ConnectionStatus.FAILED,
                            "child <" + childActorName + "> not found",
                            -1, Instant.now())));
        }
    }

    /**
     * Increments the consumed message counter by 1.
     */
    protected final void incrementConsumedMessageCounter() {
        consumedMessageCounter++;
    }

    /**
     * Increments the published message counter by 1.
     */
    protected final void incrementPublishedMessageCounter() {
        publishedMessageCounter++;
    }

    private static boolean shouldBeConnecting(final BaseClientData data) {
        return data.getConnectionStatus() == ConnectionStatus.OPEN;
    }

    private void handleSignal(final Signal<?> signal) {
        enhanceLogUtil(signal);
        if (messageMappingProcessorActor != null) {
            messageMappingProcessorActor.tell(signal, getSelf());
        } else {
            log.info("Cannot handle <{}> signal, no MessageMappingProcessor available.", signal.getType());
        }
    }

    private void enhanceLogUtil(final WithDittoHeaders<?> signal) {
        LogUtil.enhanceLogWithCorrelationId(log, signal);
        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
    }

    private void handleExternalMessage(final ExternalMessage externalMessage) {
        getPublisherActor().ifPresent(publisher -> {
            incrementPublishedMessageCounter();
            publisher.forward(externalMessage, getContext());
        });
    }

    private ConnectionStatus getCurrentConnectionStatus() {
        return stateData().getConnectionStatus();
    }

    private Instant getInConnectionStatusSince() {
        return stateData().getInConnectionStatusSince();
    }

    private Optional<String> getCurrentConnectionStatusDetails() {
        return stateData().getConnectionStatusDetails();
    }

    private List<SourceMetrics> getCurrentSourcesMetrics() {
        return getSourcesOrEmptySet()
                .stream()
                .map(source -> ConnectivityModelFactory.newSourceMetrics(
                        getSourceConnectionStatus(source),
                        consumedMessageCounter)
                )
                .collect(Collectors.toList());
    }

    private List<TargetMetrics> getCurrentTargetsMetrics() {
        return getTargetsOrEmptySet()
                .stream()
                .map(target -> ConnectivityModelFactory.newTargetMetrics(
                        getTargetConnectionStatus(target),
                        publishedMessageCounter)
                )
                .collect(Collectors.toList());
    }

    private CompletionStage<Status.Status> testMessageMappingProcessor(@Nullable final MappingContext mappingContext) {
        try {
            // this one throws DittoRuntimeExceptions when the mapper could not be configured
            MessageMappingProcessor.of(connectionId(), mappingContext, getContext().getSystem(), log);
            return CompletableFuture.completedFuture(new Status.Success("mapping"));
        } catch (final DittoRuntimeException dre) {
            log.info("Got DittoRuntimeException during initialization of MessageMappingProcessor: {} {} - desc: {}",
                    dre.getClass().getSimpleName(), dre.getMessage(), dre.getDescription().orElse(""));
            getSender().tell(dre, getSelf());
            return CompletableFuture.completedFuture(new Status.Failure(dre));
        }
    }

    /**
     * Starts the {@link MessageMappingProcessorActor} responsible for payload transformation/mapping as child actor
     * behind a (cluster node local) RoundRobin pool and a dynamic resizer.
     *
     * @param mappingContext the MappingContext containing information about how to map external messages
     */
    private void startMessageMappingProcessor(@Nullable final MappingContext mappingContext) {
        if (!getMessageMappingProcessorActor().isPresent()) {
            final Connection connection = connection();

            final MessageMappingProcessor processor;
            try {
                // this one throws DittoRuntimeExceptions when the mapper could not be configured
                processor = MessageMappingProcessor.of(connectionId(), mappingContext, getContext().getSystem(), log);
            } catch (final DittoRuntimeException dre) {
                log.info(
                        "Got DittoRuntimeException during initialization of MessageMappingProcessor: {} {} - desc: {}",
                        dre.getClass().getSimpleName(), dre.getMessage(), dre.getDescription().orElse(""));
                getSender().tell(dre, getSelf());
                return;
            }

            log.info("Configured for processing messages with the following MessageMapperRegistry: <{}>",
                    processor.getRegistry());

            log.debug("Starting MessageMappingProcessorActor with pool size of <{}>.",
                    connection.getProcessorPoolSize());
            final Props props =
                    MessageMappingProcessorActor.props(getSelf(), commandRouter,
                            connection.getAuthorizationContext(), new DittoHeadersFilter(EXCLUDE, headerBlacklist),
                            processor, connectionId());

            final DefaultResizer resizer = new DefaultResizer(1, connection.getProcessorPoolSize());
            messageMappingProcessorActor = getContext().actorOf(new RoundRobinPool(1)
                    .withDispatcher("message-mapping-processor-dispatcher")
                    .withResizer(resizer)
                    .props(props), MessageMappingProcessorActor.ACTOR_NAME);
        } else {
            log.info("MessageMappingProcessor already instantiated, don't initialize again..");
        }
    }

    /**
     * @return the optional MessageMappingProcessorActor
     */
    protected final Optional<ActorRef> getMessageMappingProcessorActor() {
        return Optional.ofNullable(messageMappingProcessorActor);
    }

    private void stopMessageMappingProcessorActor() {
        if (messageMappingProcessorActor != null) {
            log.debug("Stopping MessageMappingProcessorActor.");
            getContext().stop(messageMappingProcessorActor);
            messageMappingProcessorActor = null;
        }
    }

    private void cannotHandle(final Command<?> command, @Nullable final Connection connection) {
        enhanceLogUtil(command);
        log.info("Command <{}> cannot be handled in current state <{}>.", command.getType(), stateName());
        final String message =
                MessageFormat.format("Cannot execute command <{0}> in current state <{1}>.", command.getType(),
                        stateName());
        final String connectionId = connection != null ? connection.getId() : "?";
        final ConnectionFailedException failedException =
                ConnectionFailedException.newBuilder(connectionId).message(message).build();
        getSender().tell(new Status.Failure(failedException), getSelf());
    }

    /**
     * Escapes the passed actorName in a actorName valid way.
     *
     * @param name the actorName to escape
     * @return the escaped name
     */
    protected static String escapeActorName(final String name) {
        return name.replace('/', '_');
    }

    /**
     * Transforms a List of CompletableFutures to a CompletableFuture of a List.
     *
     * @param futures the List of futures
     * @param <T> the type of the CompletableFuture and the List elements
     * @return the CompletableFuture of a List
     */
    protected static <T> CompletableFuture<List<T>> collectAsList(final List<CompletableFuture<T>> futures) {
        return collect(futures, Collectors.toList());
    }

    private static <T, A, R> CompletableFuture<R> collect(final List<CompletableFuture<T>> futures,
            final Collector<T, A, R> collector) {

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(collector));
    }

    /**
     * Starts a child actor.
     *
     * @param name the Actor's name
     * @param props the Props
     * @return the created ActorRef
     */
    protected final ActorRef startChildActor(final String name, final Props props) {
        log.debug("Starting child actor '{}'", name);
        final String nameEscaped = escapeActorName(name);
        return getContext().actorOf(props, nameEscaped);
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
            log.debug("Stopping child actor '{}'", nameEscaped);
            getContext().stop(child.get());
        } else {
            log.debug("Cannot stop child actor '{}' because it does not exist.", nameEscaped);
        }
    }

    /**
     * Stops a child actor.
     *
     * @param actor the ActorRef
     */
    protected final void stopChildActor(final ActorRef actor) {
        log.debug("Stopping child actor '{}'", actor.path());
        getContext().stop(actor);
    }

    /**
     * @return whether this client is consuming at all
     */
    protected final boolean isConsuming() {
        return !connection().getSources().isEmpty();
    }

    /**
     * @return whether this client is publishing at all
     */
    protected final boolean isPublishing() {
        return !connection().getTargets().isEmpty();
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
     * @return the sources configured for this connection or an empty set if no sources were configured.
     */
    protected final Set<Source> getSourcesOrEmptySet() {
        return connection().getSources();
    }

    /**
     * @return the targets configured for this connection or an empty set if no targets were configured.
     */
    protected final Set<Target> getTargetsOrEmptySet() {
        return connection().getTargets();
    }

}
