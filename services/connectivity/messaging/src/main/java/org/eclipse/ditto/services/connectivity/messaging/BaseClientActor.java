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
import static org.eclipse.ditto.services.connectivity.messaging.MessageHeaderFilter.Mode.EXCLUDE;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.ConnectivityModifyCommand;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatusResponse;

import com.typesafe.config.Config;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.DynamicAccess;
import akka.actor.ExtendedActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.pubsub.DistributedPubSub;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.japi.pf.FSMTransitionHandlerBuilder;
import akka.routing.DefaultResizer;
import akka.routing.RoundRobinPool;
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

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final List<String> headerBlacklist;

    private final ActorRef pubSubMediator;
    private final String pubSubTargetPath;

    @Nullable private ActorRef messageMappingProcessor;

    private long consumedMessageCounter = 0L;
    private long publishedMessageCounter = 0L;

    protected BaseClientActor(final String connectionId, @Nullable final Connection connection,
            final ActorRef connectionActor, final String pubSubTargetPath) {

        checkNotNull(connectionId, "connectionId");
        this.pubSubMediator = DistributedPubSub.get(getContext().getSystem()).mediator();
        this.pubSubTargetPath = checkNotNull(pubSubTargetPath, "PubSubTargetPath");
        final Config config = getContext().getSystem().settings().config();
        final java.time.Duration initTimeout = config.getDuration(ConfigKeys.Client.INIT_TIMEOUT);
        headerBlacklist = config.getStringList(ConfigKeys.Message.HEADER_BLACKLIST);

        startWith(DISCONNECTED, new BaseClientData(connectionId, connection, ConnectionStatus.CLOSED,
                "initialized", Collections.emptyList()));

        when(DISCONNECTED, Duration.fromNanos(initTimeout.toNanos()),
                inDisconnectedState(connectionId, connectionActor, initTimeout));
        when(CONNECTING, Duration.create(CONNECTING_TIMEOUT, TimeUnit.SECONDS),
                inConnectingState());
        when(CONNECTED,
                inConnectedState());
        when(DISCONNECTING, Duration.create(CONNECTING_TIMEOUT, TimeUnit.SECONDS),
                inDisconnectingState());
        when(FAILED,
                inFailedState());

        onTransition(handleTransitions());

        whenUnhandled(unhandledHandler(connectionId).
                anyEvent((event, state) -> {
                    log().warning("received unhandled request {} in state {} - status: {}",
                            event, stateName(),
                            state.getConnectionStatus() + ": " + state.getConnectionStatusDetails().orElse(""));
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

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inDisconnectedState(final String connectionId,
            final ActorRef connectionActor, final java.time.Duration initTimeout) {
        return matchEvent(Arrays.asList(CloseConnection.class, DeleteConnection.class), BaseClientData.class,
                (event, data) -> stay().replying(new Status.Success(DISCONNECTED))
        ).
                eventEquals(StateTimeout(), BaseClientData.class, (state, data) -> {
                    log.info("Did not receive connect command within {}, " +
                                    "requesting information from connection actor for connection <{}>.",
                            initTimeout, connectionId);
                    connectionActor.tell(RetrieveConnection.of(connectionId, DittoHeaders.empty()), getSelf());
                    connectionActor.tell(RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty()),
                            getSelf());
                    return stay().using(data);
                }).
                event(RetrieveConnectionResponse.class, BaseClientData.class, (msg, data) -> {
                    final BaseClientData nextStateData = data.setConnection(msg.getConnection());
                    return shouldBeConnecting(nextStateData) ?
                            goTo(CONNECTING).using(nextStateData) :
                            stay().using(nextStateData);
                }).
                event(RetrieveConnectionStatusResponse.class, BaseClientData.class, (msg, data) -> {
                    final BaseClientData nextStateData = data.setConnectionStatus(msg.getConnectionStatus());
                    return shouldBeConnecting(nextStateData) ?
                            goTo(CONNECTING).using(nextStateData) :
                            stay().using(nextStateData);
                }).
                event(TestConnection.class, BaseClientData.class, (testConnection, data) -> {
                    try {
                        final CompletionStage<Status.Status> connectionStatus =
                                doTestConnection(testConnection.getConnection());
                        final CompletionStage<Status.Status> mappingStatus =
                                testMessageMappingProcessor(testConnection.getMappingContexts());

                        final ActorRef sender = getSender();
                        connectionStatus.toCompletableFuture()
                                .thenCombine(mappingStatus, (connection, mapping) -> {
                                    if (connection instanceof Status.Success && mapping instanceof Status.Success) {
                                        return new Status.Success("successfully connected + initialized mapper");
                                    } else if (connection instanceof Status.Failure) {
                                        return connection;
                                    } else {
                                        return mapping;
                                    }
                                }).thenAccept(testStatus -> sender.tell(testStatus, getSelf()));
                    } catch (final DittoRuntimeException e) {
                        getSender().tell(new Status.Failure(e), getSelf());
                    }
                    return stop();
                }).
                event(CreateConnection.class, BaseClientData.class, (createConnection, data) ->
                        goTo(CONNECTING)
                                .using(data
                                        .setConnection(createConnection.getConnection())
                                        .setMappingContexts(createConnection.getMappingContexts())
                                        .setConnectionStatusDetails("creating connection at " + Instant.now())
                                )
                ).
                event(OpenConnection.class, BaseClientData.class, (openConnection, data) ->
                        goTo(CONNECTING).using(data)
                ).
                event(ClientConnected.class, BaseClientData.class, this::handleClientConnected);
    }

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectingState() {
        return matchEvent(
                Arrays.asList(CreateConnection.class, OpenConnection.class), BaseClientData.class, (event, data) ->
                        stay()
        ).
                event(Arrays.asList(CloseConnection.class, DeleteConnection.class), BaseClientData.class,
                        (event, data) ->
                                goTo(DISCONNECTING).using(data
                                        .setConnectionStatusDetails(
                                                "closing or deleting connection at " + Instant.now())
                                )
                ).
                event(ClientConnected.class, BaseClientData.class, this::handleClientConnected).
                event(ClientDisconnected.class, BaseClientData.class, this::handleClientDisconnected).
                event(ConnectionFailure.class, BaseClientData.class, this::handleConnectionFailure).
                eventEquals(StateTimeout(), BaseClientData.class, (event, data) -> {
                    if (data.getConnectionStatus().filter(ConnectionStatus.FAILED::equals).isPresent()) {
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
                        goTo(DISCONNECTING).using(data)
        ).
                event(OpenConnection.class, BaseClientData.class, (openConnection, data) ->
                        stay().replying(new Status.Success(CONNECTED))
                ).
                event(ClientDisconnected.class, BaseClientData.class, this::handleClientDisconnected).
                event(ConnectionFailure.class, BaseClientData.class, this::handleConnectionFailure);
    }

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inDisconnectingState() {
        return matchEvent(
                Arrays.asList(CloseConnection.class, DeleteConnection.class), BaseClientData.class, (event, data) ->
                        stay()
        ).
                event(ClientDisconnected.class, BaseClientData.class, this::handleClientDisconnected).
                eventEquals(StateTimeout(), BaseClientData.class, (event, data) ->
                        goTo(CONNECTED).using(data
                                .setConnectionStatus(ConnectionStatus.OPEN)
                                .setConnectionStatusDetails(
                                        "Disconnecting timed out, still connected at " + Instant.now())
                        )
                );
    }

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inFailedState() {
        return matchEvent(OpenConnection.class, BaseClientData.class, (event, data) ->
                goTo(CONNECTING).using(data)
        ).
                event(ClientConnected.class, BaseClientData.class, this::handleClientConnected).
                event(ClientDisconnected.class, BaseClientData.class, this::handleClientDisconnected).
                event(ConnectionFailure.class, BaseClientData.class, this::handleConnectionFailure);
    }

    private State<BaseClientState, BaseClientData> handleClientConnected(final ClientConnected event,
            final BaseClientData data) {
        onClientConnected(event, data);
        event.getOrigin().ifPresent(o -> o.tell(new Status.Success(CONNECTED), getSelf()));
        return goTo(CONNECTED).using(data
                .setConnectionStatus(ConnectionStatus.OPEN)
                .setConnectionStatusDetails("Connected at " + Instant.now())
        );
    }

    private State<BaseClientState, BaseClientData> handleClientDisconnected(final ClientDisconnected event,
            final BaseClientData data) {
        onClientDisconnected(event, data);
        event.getOrigin().ifPresent(o -> o.tell(new Status.Success(DISCONNECTED), getSelf()));
        return goTo(DISCONNECTED).using(data
                .setConnectionStatus(ConnectionStatus.CLOSED)
                .setConnectionStatusDetails("Disconnected at " + Instant.now())
        );
    }

    private State<BaseClientState, BaseClientData> handleConnectionFailure(final ConnectionFailure event,
            final BaseClientData data) {
        onConnectionFailure(event, data);
        return stay().using(data
                .setConnectionStatus(ConnectionStatus.FAILED)
                .setConnectionStatusDetails(event.getFailureDescription())
        );
    }

    /**
     *
     * @param connectionId
     * @return
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> unhandledHandler(final String connectionId) {
        return matchEvent(RetrieveConnectionMetrics.class, BaseClientData.class, (command, data) -> stay()
                .replying(RetrieveConnectionMetricsResponse.of(
                        connectionId,
                        ConnectivityModelFactory.newConnectionMetrics(
                                getCurrentConnectionStatus(), getCurrentConnectionStatusDetails().orElse(null),
                                stateName().name(), getCurrentSourcesMetrics(), getCurrentTargetsMetrics()),
                        command.getDittoHeaders())
                )
        ).
                event(ConnectivityModifyCommand.class, BaseClientData.class, (command, data) -> {
                    final List<SourceMetrics> sourcesMetrics;
                    cannotHandle(command, data.getConnection().orElse(null));
                    return stay();
                }).
                event(Signal.class, BaseClientData.class, (signal, data) -> {
                    handleSignal(signal);
                    return stay();
                }).
                event(Status.Success.class, BaseClientData.class, (success, data) -> {
                    log.info("Got Status.Success: {}", success);
                    return stay();
                });
    }

    /**
     * API!
     */
    protected void onTransition(final BaseClientState from, final BaseClientState to) {
        log.info("Transition: {} -> {}", from, to);

        switch (to) {
            case CONNECTING:
                doConnectClient(nextStateData().getConnection().orElseThrow(() ->
                        new IllegalStateException("Connection not available when switching to " + CONNECTING)));
                break;
            case DISCONNECTING:
                doDisconnectClient(nextStateData().getConnection().orElseThrow(() ->
                        new IllegalStateException("Connection not available when switching to " + DISCONNECTING)));
                break;
        }
    }

    /**
     *
     * @param connection
     * @return
     */
    protected abstract CompletionStage<Status.Status> doTestConnection(final Connection connection);

    /**
     *
     * @param clientConnected
     * @param data
     */
    protected abstract void onClientConnected(final ClientConnected clientConnected, final BaseClientData data);

    /**
     *
     * @param clientDisconnected
     * @param data
     */
    protected abstract void onClientDisconnected(final ClientDisconnected clientDisconnected,
            final BaseClientData data);

    /**
     *
     * @param connectionFailure
     * @param data
     */
    protected void onConnectionFailure(final ConnectionFailure connectionFailure, final BaseClientData data) {
        connectionFailure.getOrigin().ifPresent(o -> o.tell(connectionFailure.getFailure(), getSelf()));
    }

    /**
     *
     * @param connection
     */
    protected abstract void doConnectClient(final Connection connection);

    /**
     *
     * @param connection
     */
    protected abstract void doDisconnectClient(final Connection connection);

    /**
     *
     */
    protected void incrementConsumedMessageCounter() {
        consumedMessageCounter++;
    }

    /**
     *
     */
    protected void incrementPublishedMessageCounter() {
        publishedMessageCounter++;
    }

    private static boolean shouldBeConnecting(final BaseClientData data) {
        return data.getConnection().isPresent() &&
                data.getConnectionStatus().filter(ConnectionStatus.OPEN::equals).isPresent();
    }

    private void handleSignal(final Signal<?> signal) {
        LogUtil.enhanceLogWithCorrelationId(log, signal);
        if (messageMappingProcessor != null) {
            messageMappingProcessor.tell(signal, getSelf());
        } else {
            log.info("Cannot handle <{}> signal, no MessageMappingProcessor available.", signal.getType());
        }
    }

    private ConnectionStatus getCurrentConnectionStatus() {
        return stateData().getConnectionStatus().orElseGet(() -> {
            switch (stateName()) {
                case CONNECTED:
                case DISCONNECTING:
                    return ConnectionStatus.OPEN;
                case CONNECTING:
                case DISCONNECTED:
                    return ConnectionStatus.CLOSED;
                case FAILED:
                    return ConnectionStatus.FAILED;
                default:
                    return ConnectionStatus.UNKNOWN;
            }
        });
    }

    private Optional<String> getCurrentConnectionStatusDetails() {
        return stateData().getConnectionStatusDetails();
    }

    private List<SourceMetrics> getCurrentSourcesMetrics() {
        return getSourcesOrEmptySet()
                .stream()
                .map(source -> ConnectivityModelFactory.newSourceMetrics(
                        source.getAddresses(),
                        source.getConsumerCount(),
                        getCurrentConnectionStatus(), // TODO TJ differentiate for sources?
                        getCurrentConnectionStatusDetails().orElse(null),
                        consumedMessageCounter)
                )
                .collect(Collectors.toList());
    }

    private List<TargetMetrics> getCurrentTargetsMetrics() {
        return getTargetsOrEmptySet()
                .stream()
                .map(target -> ConnectivityModelFactory.newTargetMetrics(
                        target.getAddress(),
                        target.getTopics(),
                        getCurrentConnectionStatus(), // TODO TJ differentiate for sources?
                        getCurrentConnectionStatusDetails().orElse(null),
                        publishedMessageCounter)
                )
                .collect(Collectors.toList());
    }

    private CompletionStage<Status.Status> testMessageMappingProcessor(final List<MappingContext> mappingContexts) {
        try {
            // this one throws DittoRuntimeExceptions when the mapper could not be configured
            MessageMappingProcessor.of(mappingContexts, getDynamicAccess(), log);
            return CompletableFuture.completedFuture(new Status.Success("mapping"));
        } catch (final DittoRuntimeException dre) {
            log.info("Got DittoRuntimeException during initialization of MessageMappingProcessor: {} {} - desc: {}",
                    dre.getClass().getSimpleName(), dre.getMessage(), dre.getDescription().orElse(""));
            getSender().tell(dre, getSelf());
            return CompletableFuture.completedFuture(new Status.Failure(dre));
        }
    }

    /**
     *
     *
     * @param commandProducer
     * @param mappingContexts
     */
    protected void startMessageMappingProcessor(final ActorRef commandProducer,
            final List<MappingContext> mappingContexts) {
        if (messageMappingProcessor == null) {
            if (connection().isPresent()) {
                final Connection connection = connection().get();

                final MessageMappingProcessor processor;
                try {
                    // this one throws DittoRuntimeExceptions when the mapper could not be configured
                    processor = MessageMappingProcessor.of(mappingContexts, getDynamicAccess(), log);
                } catch (final DittoRuntimeException dre) {
                    log.info("Got DittoRuntimeException during initialization of MessageMappingProcessor: {} {} - desc: {}",
                            dre.getClass().getSimpleName(), dre.getMessage(), dre.getDescription().orElse(""));
                    getSender().tell(dre, getSelf());
                    return;
                }

                log.info("Configured for processing messages with the following content types: <{}>",
                        processor.getSupportedContentTypes());
                log.info("Interpreting messages with missing content type as <{}>", processor.getDefaultContentType());

                log.debug("Starting MessageMappingProcessorActor with pool size of <{}>.",
                        connection.getProcessorPoolSize());
                final Props props = MessageMappingProcessorActor.props(pubSubMediator, pubSubTargetPath, commandProducer,
                        connection.getAuthorizationContext(), new MessageHeaderFilter(EXCLUDE, headerBlacklist),
                        processor);
                final String messageMappingProcessorName = getMessageMappingProcessorActorName(connection.getId());

                final DefaultResizer resizer = new DefaultResizer(1, connection.getProcessorPoolSize());
                messageMappingProcessor = getContext().actorOf(new RoundRobinPool(1)
                        .withDispatcher("message-mapping-processor-dispatcher")
                        .withResizer(resizer)
                        .props(props), messageMappingProcessorName);
            } else {
                log.error("Connection was not present when MessageMappingProcessor should be started.");
            }
        } else {
            log.info("MessageMappingProcessor already instantiated, don't initialize again..");
        }
    }

    /**
     *
     * @return
     */
    protected Optional<ActorRef> getMessageMappingProcessor() {
        return Optional.ofNullable(messageMappingProcessor);
    }

    /**
     *
     */
    protected void stopMessageMappingProcessor() {
        if (messageMappingProcessor != null) {
            log.debug("Stopping MessageMappingProcessorActor.");
            getContext().stop(messageMappingProcessor);
            messageMappingProcessor = null;
        }
    }

    private void cannotHandle(final Command<?> command, @Nullable final Connection connection) {
        LogUtil.enhanceLogWithCorrelationId(log, command);
        log.info("Command <{}> cannot be handled in current state <{}>.", command.getType(), stateName());
        final String message =
                MessageFormat.format("Cannot execute command <{0}> in current state <{1}>.", command.getType(),
                        stateName());
        final String connectionId = connection != null ? connection.getId() : "?";
        final ConnectionFailedException failedException =
                ConnectionFailedException.newBuilder(connectionId).message(message).build();
        getSender().tell(new Status.Failure(failedException), getSelf());
    }

    private DynamicAccess getDynamicAccess() {
        return ((ExtendedActorSystem) getContext().getSystem()).dynamicAccess();
    }

    private String getMessageMappingProcessorActorName(final String connectionId) {
        return escapeActorName(MessageMappingProcessorActor.ACTOR_NAME_PREFIX + connectionId);
    }

    /**
     *
     * @param name
     * @return
     */
    protected static String escapeActorName(final String name) {
        return name.replace('/', '_');
    }

    /**
     *
     * @param name
     * @param props
     * @return
     */
    protected ActorRef startChildActor(final String name, final Props props) {
        log.debug("Starting child actor '{}'", name);
        final String nameEscaped = escapeActorName(name);
        return getContext().actorOf(props, nameEscaped);
    }

    /**
     *
     * @param name
     */
    protected void stopChildActor(final String name) {
        final String nameEscaped = escapeActorName(name);
        final Optional<ActorRef> child = getContext().findChild(nameEscaped);
        if (child.isPresent()) {
            log.debug("Stopping child actor '{}'", nameEscaped);
            getContext().stop(child.get());
        } else {
            log.debug("Cannot stop child actor '{}' because it does not exist.");
        }
    }

    /**
     *
     * @param actor
     */
    protected void stopChildActor(final ActorRef actor) {
        log.debug("Stopping child actor '{}'", actor.path());
        getContext().stop(actor);
    }

    /**
     *
     * @return
     */
    protected boolean isConsuming() {
        return connection()
                .filter(c -> !c.getSources().isEmpty())
                .isPresent();
    }

    /**
     *
     * @return
     */
    protected boolean isPublishing() {
        return connection()
                .filter(c -> !c.getTargets().isEmpty())
                .isPresent();
    }

    /**
     *
     * @return
     */
    protected Optional<Connection> connection() {
        return stateData().getConnection();
    }

    /**
     *
     * @return
     */
    protected String connectionId() {
        return stateData().getConnectionId();
    }

    /**
     * @return the sources configured for this connection or an empty set if no sources were configured.
     */
    protected Set<Source> getSourcesOrEmptySet() {
        return connection().map(Connection::getSources).orElse(Collections.emptySet());
    }

    /**
     * @return the targets configured for this connection or an empty set if no targets were configured.
     */
    protected Set<Target> getTargetsOrEmptySet() {
        return connection().map(Connection::getTargets).orElse(Collections.emptySet());
    }
}
