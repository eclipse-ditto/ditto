/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging;

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
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
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
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressMetric;
import org.eclipse.ditto.services.connectivity.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionSignalIllegalException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;

import com.typesafe.config.Config;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.FSM;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Pair;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.PatternsCS;
import akka.routing.DefaultResizer;
import akka.routing.Resizer;
import akka.routing.RoundRobinPool;
import akka.util.Timeout;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * Base class for ClientActors which implement the connection handling for various connectivity protocols.
 * <p>
 * The actor expects to receive a {@link CreateConnection} command after it was started. If this command is not received
 * within timeout (can be the case when this actor is remotely deployed after the command was sent) the actor requests
 * the required information from ConnectionActor.
 * </p>
 */
public abstract class BaseClientActor extends AbstractFSM<BaseClientState, BaseClientData> {

    protected static final int CONNECTING_TIMEOUT = 10;
    protected static final int TEST_CONNECTION_TIMEOUT = 10;
    protected static final int RETRIEVE_METRICS_TIMEOUT = 2;

    private static final int SOCKET_CHECK_TIMEOUT_MS = 2000;

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorRef conciergeForwarder;

    @Nullable private ActorRef messageMappingProcessorActor;

    private long consumedMessageCounter = 0L;
    private long publishedMessageCounter = 0L;

    // counter for all child actors ever started to disambiguate between them
    private int childActorCount = 0;

    protected BaseClientActor(final Connection connection, final ConnectionStatus desiredConnectionStatus,
            final ActorRef conciergeForwarder) {

        checkNotNull(connection, "connection");

        final Config config = getContext().getSystem().settings().config();
        final java.time.Duration javaInitTimeout = config.getDuration(ConfigKeys.Client.INIT_TIMEOUT);
        this.conciergeForwarder = conciergeForwarder;

        final BaseClientData startingData = new BaseClientData(connection.getId(), connection, ConnectionStatus.UNKNOWN,
                desiredConnectionStatus, "initialized", Instant.now(), null, null);

        final FiniteDuration initTimeout = Duration.create(javaInitTimeout.toMillis(), TimeUnit.MILLISECONDS);
        final FiniteDuration connectingTimeout = Duration.create(CONNECTING_TIMEOUT, TimeUnit.SECONDS);

        startWith(UNKNOWN, startingData, initTimeout);

        // stable states
        when(UNKNOWN, inUnknownState());
        when(CONNECTED, inConnectedState());
        when(DISCONNECTED, inDisconnectedState());

        // volatile states that time out
        when(CONNECTING, connectingTimeout, inConnectingState());
        when(DISCONNECTING, connectingTimeout, inDisconnectingState());
        when(TESTING, connectingTimeout, inTestingState());

        onTransition(this::onTransition);

        whenUnhandled(inAnyState().anyEvent(this::onUnknownEvent));

        initialize();
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
     * Allocate resources once this {@code Client} connected successfully.
     *
     * @param clientConnected the ClientConnected message which may be subclassed and thus adding more information
     */
    protected abstract void allocateResourcesOnConnection(final ClientConnected clientConnected);

    /**
     * Clean up everything spawned in {@code allocateResourcesOnConnection}. It should be idempotent.
     */
    protected abstract void cleanupResourcesForConnection();

    /**
     * @return the optional Actor to use for Publishing commandResponses/events.
     */
    protected abstract Optional<ActorRef> getPublisherActor();

    /**
     * Invoked when this {@code Client} should connect.
     *
     * @param connection the Connection to use for connecting.
     * @param origin the ActorRef which caused the ConnectClient command.
     */
    protected abstract void doConnectClient(final Connection connection, @Nullable final ActorRef origin);

    /**
     * Invoked when this {@code Client} should disconnect.
     *
     * @param connection the Connection to use for disconnecting.
     * @param origin the ActorRef which caused the DisconnectClient command.
     */
    protected abstract void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin);

    /**
     * Retrieves the connection status of the passed {@link Source}.
     *
     * @param source the Source to retrieve the connection status for.
     * @return the result as Map containing an entry for each source.
     */
    protected abstract CompletionStage<Map<String, AddressMetric>> getSourceConnectionStatus(final Source source);

    /**
     * Retrieves the connection status of the passed {@link Target}.
     *
     * @param target the Target to retrieve the connection status for.
     * @return the result as Map containing an entry for each target.
     */
    protected abstract CompletionStage<Map<String, AddressMetric>> getTargetConnectionStatus(final Target target);

    /**
     * Release any temporary resources allocated during a connection operation when the operation times out.
     * Do nothing by default.
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
        return matchEvent(RetrieveConnectionMetrics.class, BaseClientData.class, this::retrieveConnectionMetrics)
                .event(OutboundSignal.WithExternalMessage.class, BaseClientData.class, (outboundSignal, data) -> {
                    handleExternalMessage(outboundSignal);
                    return stay();
                })
                .event(OutboundSignal.class, BaseClientData.class, (signal, data) -> {
                    handleOutboundSignal(signal);
                    return stay();
                });
    }


    /**
     * Retrieves the {@link AddressMetric} of a single address which is handled by a child actor with the passed
     * {@code childActorName}.
     *
     * @param addressIdentifier the identifier used as first entry in the Pair of CompletableFuture
     * @param childActorLabel what to call the child actor
     * @param childActorRef the actor reference of the child to ask for the AddressMetric
     * @return a CompletableFuture with the addressIdentifier and the retrieved AddressMetric
     */
    protected final CompletableFuture<Pair<String, AddressMetric>> retrieveAddressMetric(
            final String addressIdentifier, final String childActorLabel, @Nullable final ActorRef childActorRef) {

        if (childActorRef != null) {
            final Timeout timeout = Timeout.apply(RETRIEVE_METRICS_TIMEOUT, TimeUnit.SECONDS);
            return PatternsCS.ask(childActorRef, RetrieveAddressMetric.getInstance(), timeout)
                    .handle((response, throwable) -> {
                        if (response != null) {
                            return Pair.create(addressIdentifier, (AddressMetric) response);
                        } else {
                            return Pair.create(addressIdentifier,
                                    ConnectivityModelFactory.newAddressMetric(
                                            ConnectionStatus.FAILED,
                                            throwable.getClass().getSimpleName() + ": " + throwable.getMessage(),
                                            -1, Instant.now()));
                        }
                    }).toCompletableFuture();
        } else {
            log.warning("Consumer actor child <{}> was not found in child actors <{}>", childActorLabel,
                    StreamSupport
                            .stream(getContext().getChildren().spliterator(), false)
                            .map(ref -> ref.path().name())
                            .collect(Collectors.toList()));
            return CompletableFuture.completedFuture(Pair.create(addressIdentifier,
                    ConnectivityModelFactory.newAddressMetric(
                            ConnectionStatus.FAILED,
                            "child <" + childActorLabel + "> not found!",
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

    /**
     * @return the optional MessageMappingProcessorActor.
     */
    protected final Optional<ActorRef> getMessageMappingProcessorActor() {
        return Optional.ofNullable(messageMappingProcessorActor);
    }

    /**
     * Escapes the passed actorName in a actorName valid way.
     * Actor name should be a valid URL with ASCII letters, see also {@code akka.actor.ActorPath#isValidPathElement},
     * therefor we encode the name as an ASCII URL.
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
     * Transforms a List of CompletableFutures to a CompletableFuture of a List.
     *
     * @param futures the stream of futures
     * @param <T> the type of the CompletableFuture and the List elements
     * @return the CompletableFuture of a List
     */
    protected static <T> CompletableFuture<List<T>> collectAsList(final Stream<CompletionStage<T>> futures) {

        final CompletableFuture<T>[] futureArray = futures.map(CompletionStage::toCompletableFuture)
                .toArray((IntFunction<CompletableFuture<T>[]>) CompletableFuture[]::new);

        return CompletableFuture.allOf(futureArray).thenApply(_void ->
                Arrays.stream(futureArray)
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    /**
     * Starts a child actor.
     *
     * @param name the Actor's name
     * @param props the Props
     * @return the created ActorRef
     */
    protected final ActorRef startChildActor(final String name, final Props props) {
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
        if (actor != null) {
            log.debug("Stopping child actor <{}>.", actor.path());
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
    protected final List<Source> getSourcesOrEmptySet() {
        return connection().getSources();
    }

    /**
     * @return the targets configured for this connection or an empty set if no targets were configured.
     */
    protected final Set<Target> getTargetsOrEmptySet() {
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
        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
        log.debug("Transition: {} -> {}", from, to);
    }

    private FSMStateFunctionBuilder<BaseClientState, BaseClientData> inUnknownState() {
        return matchEvent(OpenConnection.class, BaseClientData.class, this::openConnection)
                .event(CloseConnection.class, BaseClientData.class, this::closeConnection)
                .event(TestConnection.class, BaseClientData.class, this::testConnection)
                .eventEquals(StateTimeout(), BaseClientData.class, (state, data) -> {
                    if (ConnectionStatus.OPEN == data.getDesiredConnectionStatus()) {
                        log.info("Did not receive connect command within init-timeout, connecting");
                        final OpenConnection openConnection = OpenConnection.of(connectionId(), DittoHeaders.empty());
                        getSelf().tell(openConnection, getSelf());
                    } else if (ConnectionStatus.CLOSED == data.getDesiredConnectionStatus()) {
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
     * Creates the handler for messages in disconnected state.
     * Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inDisconnectedState() {
        return matchEvent(OpenConnection.class, BaseClientData.class, this::openConnection)
                .event(TestConnection.class, BaseClientData.class, this::testConnection);
    }

    /**
     * Creates the handler for messages in connecting state.
     * Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectingState() {
        return matchEventEquals(StateTimeout(), BaseClientData.class, this::connectionTimedOut)
                .event(ConnectionFailure.class, BaseClientData.class, this::connectionFailure)
                .event(ClientConnected.class, BaseClientData.class, this::clientConnected);
    }

    /**
     * Creates the handler for messages in connected state.
     * Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectedState() {
        return matchEvent(CloseConnection.class, BaseClientData.class, this::closeConnection);
    }

    /**
     * Creates the handler for messages in disconnected state.
     * Overwrite and extend by additional matchers.
     *
     * @return an FSM function builder
     */
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inDisconnectingState() {
        return matchEventEquals(StateTimeout(), BaseClientData.class, this::connectionTimedOut)
                .event(ConnectionFailure.class, BaseClientData.class, this::connectionFailure)
                .event(ClientDisconnected.class, BaseClientData.class, this::clientDisconnected);
    }

    /**
     * Creates the handler for messages in testing state.
     * Overwrite and extend by additional matchers.
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
                                        TEST_CONNECTION_TIMEOUT))
                                .dittoHeaders(data.getSessionHeaders())
                                .build();
                        sender.tell(new Status.Failure(error), getSelf());
                    });
                    return stop();
                });
    }

    private State<BaseClientState, BaseClientData> onUnknownEvent(final Object event,
            final BaseClientData state) {

        log.warning("received unknown/unsupported message {} in state {} - status: {} - sender: {}",
                event,
                stateName(),
                state.getConnectionStatus() + ": " + state.getConnectionStatusDetails().orElse(""),
                getSender());

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
        return goTo(DISCONNECTING).using(setSession(data, sender, closeConnection.getDittoHeaders())
                .setDesiredConnectionStatus(ConnectionStatus.CLOSED)
                .setConnectionStatusDetails("closing or deleting connection at " + Instant.now()));
    }

    private FSM.State<BaseClientState, BaseClientData> openConnection(final OpenConnection openConnection,
            final BaseClientData data) {

        final ActorRef sender = getSender();
        final Connection connection = data.getConnection();
        final DittoHeaders dittoHeaders = openConnection.getDittoHeaders();
        if (canConnectViaSocket(connection)) {
            doConnectClient(connection, sender);
            return goTo(CONNECTING).using(setSession(data, sender, dittoHeaders));
        } else {
            cleanupResourcesForConnection();
            final DittoRuntimeException error = newConnectionFailedException(data.getConnection(), dittoHeaders);
            sender.tell(new Status.Failure(error), getSelf());
            return goTo(UNKNOWN).using(data.resetSession());
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

        return goTo(TESTING)
                .using(setSession(data, sender, testConnection.getDittoHeaders())
                        .setConnection(connection)
                        .setConnectionStatusDetails("Testing connection since " + Instant.now()));
    }

    private FSM.State<BaseClientState, BaseClientData> connectionTimedOut(final Object event,
            final BaseClientData data) {

        data.getSessionSender().ifPresent(sender -> {
            final DittoRuntimeException error = ConnectionFailedException.newBuilder(connectionId())
                    .dittoHeaders(data.getSessionHeaders())
                    .build();
            sender.tell(new Status.Failure(error), getSelf());
        });
        cleanupResourcesForConnection();
        cleanupFurtherResourcesOnConnectionTimeout(stateName());
        return goTo(UNKNOWN).using(data.resetSession()
                .setConnectionStatus(ConnectionStatus.FAILED)
                .setConnectionStatusDetails("Connection timed out at " + Instant.now() + " while " + stateName()));
    }

    private State<BaseClientState, BaseClientData> clientConnected(final ClientConnected clientConnected,
            final BaseClientData data) {

        return ifEventUpToDate(clientConnected, () -> {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            startMessageMappingProcessor(data.getConnection().getMappingContext().orElse(null));
            allocateResourcesOnConnection(clientConnected);
            data.getSessionSender().ifPresent(origin -> origin.tell(new Status.Success(CONNECTED), getSelf()));
            return goTo(CONNECTED).using(data.resetSession()
                    .setConnectionStatus(ConnectionStatus.OPEN)
                    .setConnectionStatusDetails("Connected at " + Instant.now()));
        });
    }

    private State<BaseClientState, BaseClientData> clientDisconnected(final ClientDisconnected event,
            final BaseClientData data) {

        return ifEventUpToDate(event, () -> {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            stopMessageMappingProcessorActor();
            cleanupResourcesForConnection();
            data.getSessionSender().ifPresent(sender -> sender.tell(new Status.Success(DISCONNECTED), getSelf()));
            return goTo(DISCONNECTED).using(data.resetSession()
                    .setConnectionStatus(ConnectionStatus.CLOSED)
                    .setConnectionStatusDetails("Disconnected at " + Instant.now()));
        });
    }

    private State<BaseClientState, BaseClientData> connectionFailure(final ConnectionFailure event,
            final BaseClientData data) {
        return ifEventUpToDate(event, () -> {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            cleanupResourcesForConnection();
            data.getSessionSender().ifPresent(sender -> sender.tell(getStatusToReport(event.getFailure()), getSelf()));
            return goTo(UNKNOWN).using(data.resetSession()
                    .setConnectionStatus(ConnectionStatus.FAILED)
                    .setConnectionStatusDetails(event.getFailureDescription())
                    .setSessionSender(getSender())
            );
        });
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

    private FSM.State<BaseClientState, BaseClientData> retrieveConnectionMetrics(
            final RetrieveConnectionMetrics command,
            final BaseClientData data) {

        final ActorRef sender = getSender();
        final ActorRef self = getSelf();

        final DittoHeaders dittoHeaders = command.getDittoHeaders().toBuilder()
                .source(org.eclipse.ditto.services.utils.config.ConfigUtil.instanceIdentifier())
                .build();

        final CompletionStage<List<SourceMetrics>> sourceMetricsFuture = getCurrentSourcesMetrics();
        final CompletionStage<List<TargetMetrics>> targetMetricsFuture = getCurrentTargetsMetrics();

        final CompletionStage<ConnectionMetrics> metricsFuture =
                sourceMetricsFuture.thenCompose(sourceMetrics ->
                        targetMetricsFuture.thenApply(targetMetrics ->
                                ConnectivityModelFactory.newConnectionMetrics(
                                        getCurrentConnectionStatus(),
                                        getCurrentConnectionStatusDetails().orElse(null),
                                        getInConnectionStatusSince(),
                                        stateName().name(),
                                        sourceMetrics,
                                        targetMetrics)
                        )
                );

        metricsFuture.thenAccept(connectionMetrics -> {
            final Object response =
                    RetrieveConnectionMetricsResponse.of(connectionId(), connectionMetrics, dittoHeaders);
            sender.tell(response, self);
        });

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
                        .timeout(CONNECTING_TIMEOUT)
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
        } catch (final IOException ex) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.warning("Socket could not be opened for <{}:{}> due to <{}:{}>", host, port,
                    ex.getClass().getCanonicalName(), ex.getMessage());
        }
        return false;
    }

    private void handleOutboundSignal(final OutboundSignal signal) {
        enhanceLogUtil(signal.getSource());
        if (messageMappingProcessorActor != null) {
            messageMappingProcessorActor.tell(signal, getSelf());
        } else {
            log.info("Cannot handle <{}> signal as there is no MessageMappingProcessor available.",
                    signal.getSource().getType());
        }
    }

    private void enhanceLogUtil(final WithDittoHeaders<?> signal) {
        LogUtil.enhanceLogWithCorrelationId(log, signal);
        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
    }

    private void handleExternalMessage(final OutboundSignal.WithExternalMessage mappedOutboundSignal) {
        getPublisherActor().ifPresent(publisher -> {
            incrementPublishedMessageCounter();
            publisher.forward(mappedOutboundSignal, getContext());
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

    private CompletionStage<List<SourceMetrics>> getCurrentSourcesMetrics() {
        final long consumedMessages = consumedMessageCounter;
        return collectAsList(getSourcesOrEmptySet()
                .stream()
                .map(this::getSourceConnectionStatus)
                .map(future -> future.thenApply(status ->
                        ConnectivityModelFactory.newSourceMetrics(status, consumedMessages)
                ))
        );
    }

    private CompletionStage<List<TargetMetrics>> getCurrentTargetsMetrics() {
        final long publishedMessages = publishedMessageCounter;
        return collectAsList(getTargetsOrEmptySet()
                .stream()
                .map(this::getTargetConnectionStatus)
                .map(future -> future.thenApply(status ->
                        ConnectivityModelFactory.newTargetMetrics(status, publishedMessages)
                )));
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
     * behind a (cluster node local) RoundRobin pool and a dynamic resizer from the current mapping context.
     */
    protected Either<DittoRuntimeException, ActorRef> startMessageMappingProcessor() {
        final MappingContext mappingContext = stateData().getConnection().getMappingContext().orElse(null);
        return startMessageMappingProcessor(mappingContext);
    }

    /**
     * Starts the {@link MessageMappingProcessorActor} responsible for payload transformation/mapping as child actor
     * behind a (cluster node local) RoundRobin pool and a dynamic resizer.
     *
     * @param mappingContext the MappingContext containing information about how to map external messages
     */
    private Either<DittoRuntimeException, ActorRef> startMessageMappingProcessor(
            @Nullable final MappingContext mappingContext) {
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
                return Left.apply(dre);
            }

            log.info("Configured for processing messages with the following MessageMapperRegistry: <{}>",
                    processor.getRegistry());

            log.debug("Starting MessageMappingProcessorActor with pool size of <{}>.",
                    connection.getProcessorPoolSize());
            final Props props =
                    MessageMappingProcessorActor.props(getSelf(), conciergeForwarder, processor, connectionId());

            final Resizer resizer = new DefaultResizer(1, connection.getProcessorPoolSize());
            messageMappingProcessorActor = getContext().actorOf(new RoundRobinPool(1)
                    .withDispatcher("message-mapping-processor-dispatcher")
                    .withResizer(resizer)
                    .props(props), nextChildActorName(MessageMappingProcessorActor.ACTOR_NAME));
        } else {
            log.info("MessageMappingProcessor already instantiated: not initializing again.");
        }
        return Right.apply(messageMappingProcessorActor);
    }

    private String nextChildActorName(final String prefix) {
        return prefix + ++childActorCount;
    }

    private BaseClientData setSession(final BaseClientData data, final ActorRef sender, final DittoHeaders headers) {
        if (!Objects.equals(sender, getSelf()) && !Objects.equals(sender, getContext().system().deadLetters())) {
            return data.setSessionSender(sender)
                    .setSessionHeaders(headers);
        } else {
            return data.resetSession();
        }
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
            log.info("test failed: <{}>", failure.cause());
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

    private void stopMessageMappingProcessorActor() {
        if (messageMappingProcessorActor != null) {
            log.debug("Stopping MessageMappingProcessorActor.");
            getContext().stop(messageMappingProcessorActor);
            messageMappingProcessorActor = null;
        }
    }

    private static String describeEventualCause(final Throwable throwable) {
        final Throwable cause = throwable.getCause();
        if (cause == null || cause == throwable) {
            return "Cause: " + throwable.getMessage();
        } else {
            return describeEventualCause(cause);
        }
    }
}
