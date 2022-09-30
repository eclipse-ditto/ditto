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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import java.net.URI;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsConnectionListener;
import org.apache.qpid.jms.JmsSession;
import org.apache.qpid.jms.message.JmsInboundMessageDispatch;
import org.apache.qpid.jms.provider.ProviderFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.MetricDirection;
import org.eclipse.ditto.connectivity.model.MetricType;
import org.eclipse.ditto.connectivity.model.RecoveryStatus;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionFailedException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.service.config.Amqp10Config;
import org.eclipse.ditto.connectivity.service.config.ClientConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientActor;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientData;
import org.eclipse.ditto.connectivity.service.messaging.amqp.status.ConnectionFailureStatusReport;
import org.eclipse.ditto.connectivity.service.messaging.amqp.status.ConnectionRestoredStatusReport;
import org.eclipse.ditto.connectivity.service.messaging.amqp.status.ConsumerClosedStatusReport;
import org.eclipse.ditto.connectivity.service.messaging.amqp.status.ProducerClosedStatusReport;
import org.eclipse.ditto.connectivity.service.messaging.amqp.status.SessionClosedStatusReport;
import org.eclipse.ditto.connectivity.service.messaging.internal.AbstractWithOrigin;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientConnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.CloseSession;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectClient;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.internal.DisconnectClient;
import org.eclipse.ditto.connectivity.service.messaging.internal.RecoverSession;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.ThrottledLoggerMetricsAlert;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.FSM;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.Patterns;
import akka.stream.javadsl.Sink;

/**
 * Actor which manages a connection to an AMQP 1.0 server using the Qpid JMS client.
 * This actor delegates interaction with the JMS client to a child actor because the JMS client blocks in most cases
 * which does not work well with actors.
 */
public final class AmqpClientActor extends BaseClientActor implements ExceptionListener {

    private static final String SPEC_CONFIG_RECOVER_ON_SESSION_CLOSED = "recover.on-session-closed";
    private static final String SPEC_CONFIG_RECOVER_ON_CONNECTION_RESTORED = "recover.on-connection-restored";

    private final JmsConnectionFactory jmsConnectionFactory;

    final StatusReportingListener connectionListener;

    @Nullable private JmsConnection jmsConnection;
    @Nullable private Session jmsSession;

    @Nullable private ActorRef testConnectionHandler;
    @Nullable private ActorRef connectConnectionHandler;
    @Nullable private ActorRef disconnectConnectionHandler;

    private final Map<String, ActorRef> consumerByNamePrefix;
    private final boolean recoverSessionOnSessionClosed;
    private final boolean recoverSessionOnConnectionRestored;
    private final Duration clientAskTimeout;
    private final Duration initialConsumerResourceStatusAskTimeout;
    private ActorRef amqpPublisherActor;

    /*
     * This constructor is called via reflection by the static method props.
     */
    @SuppressWarnings("unused")
    private AmqpClientActor(final Connection connection,
            final ActorRef commandForwarderActor,
            final ActorRef connectionActor,
            final Config connectivityConfigOverwrites,
            final DittoHeaders dittoHeaders) {

        super(connection, commandForwarderActor, connectionActor, dittoHeaders, connectivityConfigOverwrites);
        final ConnectionConfig connectionConfig = connectivityConfig().getConnectionConfig();
        final Amqp10Config amqp10Config = connectionConfig.getAmqp10Config();
        jmsConnectionFactory =
                ConnectionBasedJmsConnectionFactory.getInstance(AmqpSpecificConfig.toDefaultConfig(amqp10Config),
                        this::getSshTunnelState, getContext().getSystem());
        connectionListener = new StatusReportingListener(getSelf(), logger, connectionLogger);
        consumerByNamePrefix = new HashMap<>();
        recoverSessionOnSessionClosed = isRecoverSessionOnSessionClosedEnabled(connection);
        recoverSessionOnConnectionRestored = isRecoverSessionOnConnectionRestoredEnabled(connection);
        clientAskTimeout = connectionConfig.getClientActorAskTimeout();
        initialConsumerResourceStatusAskTimeout = amqp10Config.getInitialConsumerStatusAskTimeout();

        connectionCounterRegistry.registerAlertFactory(ConnectionType.AMQP_10, MetricType.THROTTLED,
                MetricDirection.INBOUND,
                ThrottledLoggerMetricsAlert.getFactory(
                        address -> connectionLoggerRegistry.forInboundThrottled(connection, address)));
    }

    /*
     * This constructor is called via reflection by the static method props.
     */
    @SuppressWarnings("unused")
    private AmqpClientActor(final Connection connection,
            final JmsConnectionFactory jmsConnectionFactory,
            final ActorRef commandForwarderActor,
            final ActorRef connectionActor, final DittoHeaders dittoHeaders) {

        super(connection, commandForwarderActor, connectionActor, dittoHeaders, ConfigFactory.empty());

        this.jmsConnectionFactory = jmsConnectionFactory;
        connectionListener = new StatusReportingListener(getSelf(), logger, connectionLogger);
        consumerByNamePrefix = new HashMap<>();
        recoverSessionOnSessionClosed = isRecoverSessionOnSessionClosedEnabled(connection);
        recoverSessionOnConnectionRestored = isRecoverSessionOnConnectionRestoredEnabled(connection);
        clientAskTimeout = Duration.ofSeconds(10L);
        initialConsumerResourceStatusAskTimeout = Duration.ofMillis(500L);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param commandForwarderActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the connectionPersistenceActor which created this client.
     * @param configOverwrites an override for the default connectivity config values -
     * @param actorSystem the actor system.
     * as Typesafe {@code Config} because this one is serializable in Akka by default.
     * @param dittoHeaders headers of the command that caused this actor to be created.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef commandForwarderActor,
            final ActorRef connectionActor, final Config configOverwrites, final ActorSystem actorSystem,
            final DittoHeaders dittoHeaders) {
        return Props.create(AmqpClientActor.class, validateConnection(connection, actorSystem),
                commandForwarderActor, connectionActor, configOverwrites, dittoHeaders);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection connection parameters.
     * @param commandForwarderActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the connectionPersistenceActor which created this client.
     * @param jmsConnectionFactory the JMS connection factory.
     * @param actorSystem the actor system.
     * @return the Akka configuration Props object.
     */
    static Props propsForTest(final Connection connection, @Nullable final ActorRef commandForwarderActor,
            final ActorRef connectionActor, final JmsConnectionFactory jmsConnectionFactory,
            final ActorSystem actorSystem) {
        return Props.create(AmqpClientActor.class, validateConnection(connection, actorSystem),
                jmsConnectionFactory, commandForwarderActor, connectionActor, DittoHeaders.empty());
    }

    private static Connection validateConnection(final Connection connection, final ActorSystem actorSystem) {
        try {
            final String connectionUri = ConnectionBasedJmsConnectionFactory.buildAmqpConnectionUri(connection,
                    connection.getId().toString(),
                    // fake established tunnel state for uri validation
                    () -> SshTunnelState.from(connection).established(22222),
                    Map.of(),
                    SaslPlainCredentialsSupplier.of(actorSystem));
            ProviderFactory.create(URI.create(connectionUri));
            // it is safe to pass an empty map as default config as only default values are loaded via that config
            // of which we can be certain that they are always valid
            return connection;
        } catch (final Exception e) {
            final String msgPattern = "Failed to instantiate an amqp provider from the given configuration: {0}";
            throw ConnectionConfigurationInvalidException
                    .newBuilder(MessageFormat.format(msgPattern, e.getMessage()))
                    .description(e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    @Override
    public void postStop() {
        super.postStop();
        ensureJmsConnectionClosed();
    }

    @Override
    protected Set<Pattern> getExcludedAddressReportingChildNamePatterns() {
        final Set<Pattern> excludedChildNamePatterns =
                new HashSet<>(super.getExcludedAddressReportingChildNamePatterns());
        excludedChildNamePatterns.add(
                Pattern.compile(Pattern.quote(JMSConnectionHandlingActor.ACTOR_NAME_PREFIX) + ".*"));
        return excludedChildNamePatterns;
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectedState() {
        return super.inConnectedState()
                .event(JmsSessionRecovered.class, this::handleSessionRecovered);
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inAnyState() {
        return super.inAnyState()
                .event(ConnectionRestoredStatusReport.class,
                        (report, currentData) -> handleConnectionRestored(currentData))
                .event(ConnectionFailureStatusReport.class, this::handleConnectionFailure)
                .event(ConsumerClosedStatusReport.class, this::handleConsumerClosed)
                .event(ProducerClosedStatusReport.class, this::handleProducerClosed)
                .event(SessionClosedStatusReport.class, this::handleSessionClosed);
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final TestConnection testConnectionCommand) {
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        final Connection connectionToBeTested = testConnectionCommand.getConnection();
        final ClientConfig clientConfig = connectivityConfig().getClientConfig();
        return Patterns.ask(getTestConnectionHandler(connectionToBeTested),
                        jmsConnect(getSender(), connectionToBeTested), clientConfig.getTestingTimeout())
                // compose the disconnect because otherwise the actor hierarchy might be stopped too fast
                .thenCompose(response -> {
                    logger.withCorrelationId(testConnectionCommand)
                            .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connectionToBeTested.getId())
                            .debug("Closing AMQP 1.0 connection after testing connection.");
                    if (response instanceof JmsConnected jmsConnected) {
                        final JmsConnection connectedJmsConnection = jmsConnected.connection;
                        final JmsDisconnect jmsDisconnect = new JmsDisconnect(ActorRef.noSender(),
                                connectedJmsConnection, true);
                        return Patterns.ask(getDisconnectConnectionHandler(connectionToBeTested), jmsDisconnect,
                                        clientConfig.getTestingTimeout())
                                // replace jmsDisconnected message with original response
                                .thenApply(jmsDisconnected -> response);
                    } else {
                        return CompletableFuture.completedFuture(response);
                    }
                })
                .handle((response, throwable) -> {
                    if (throwable != null || response instanceof Status.Failure || response instanceof Throwable) {
                        final Throwable ex =
                                response instanceof Status.Failure ? ((Status.Failure) response).cause() :
                                        response instanceof Throwable ? (Throwable) response : throwable;
                        final ConnectionFailedException failedException =
                                ConnectionFailedException.newBuilder(connectionId())
                                        .description("The requested Connection could not be connected due to '" +
                                                ex.getClass().getSimpleName() + ": " + ex.getMessage() + "'")
                                        .cause(ex).build();
                        return new Status.Failure(failedException);
                    } else if (response instanceof ConnectionFailure connectionFailure) {
                        return connectionFailure.getFailure();
                    } else {
                        return new Status.Success(response);
                    }
                });
    }

    @Override
    protected CompletionStage<Void> stopConsuming() {
        final var timeout = Duration.ofMinutes(2L);
        final CompletableFuture<?>[] futures = streamConsumerActors()
                .map(consumer -> Patterns.ask(consumer, AmqpConsumerActor.Control.STOP_CONSUMER, timeout))
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        getConnectConnectionHandler(connection).tell(jmsConnect(origin, connection), getSelf());
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin,
            final boolean shutdownAfterDisconnect) {
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        getDisconnectConnectionHandler(connection)
                .tell(new JmsDisconnect(origin, jmsConnection, shutdownAfterDisconnect), getSelf());
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        if (clientConnected instanceof JmsConnected client) {
            logger.info("Received JmsConnected");
            ensureJmsConnectionClosed();
            jmsConnection = client.connection;
            jmsConnection.addConnectionListener(connectionListener);
            jmsSession = client.session;
        } else {
            logger.info(
                    "ClientConnected was not JmsConnected as expected, ignoring as this probably was a reconnection");
        }
    }

    @Override
    protected CompletionStage<Status.Status> startPublisherActor() {
        final CompletableFuture<Status.Status> future = new CompletableFuture<>();
        stopChildActor(amqpPublisherActor);
        if (null != jmsSession) {
            final Props props = AmqpPublisherActor.props(connection(),
                    jmsSession,
                    connectivityStatusResolver,
                    connectivityConfig());
            amqpPublisherActor = startChildActorConflictFree(AmqpPublisherActor.ACTOR_NAME_PREFIX, props);
            Patterns.ask(amqpPublisherActor, AmqpPublisherActor.Control.INITIALIZE, clientAskTimeout)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else if (result instanceof Throwable throwable) {
                            future.completeExceptionally(throwable);
                        } else {
                            future.complete(DONE);
                        }
                    });
        } else {
            future.completeExceptionally(ConnectionFailedException
                    .newBuilder(connectionId())
                    .message("Could not start publisher actor due to missing AMQP 1.0 session or connection!")
                    .build());
        }
        return future;
    }

    @Override
    protected CompletionStage<Status.Status> startConsumerActors(@Nullable final ClientConnected clientConnected) {
        if (clientConnected instanceof JmsConnected jmsConnected) {
            final ActorRef jmsActor = getConnectConnectionHandler(connection());
            return startCommandConsumers(jmsConnected.consumerList, jmsActor)
                    .thenApply(ignored -> new Status.Success(Done.getInstance()));
        }
        return CompletableFuture.completedFuture(new Status.Success(Done.getInstance()));
    }

    @Override
    protected void cleanupResourcesForConnection() {
        logger.debug("Cleaning up resources for connection <{}>.", connectionId());
        stopCommandConsumers();
        stopChildActor(amqpPublisherActor);
        // closing JMS connection closes all sessions and consumers
        ensureJmsConnectionClosed();
        jmsConnection = null;
        jmsSession = null;
    }

    /*
     * Kill connection handlers on timeout to be able to handle the next command immediately.
     */
    @Override
    protected void cleanupFurtherResourcesOnConnectionTimeout(final BaseClientState currentState) {
        if (connectConnectionHandler != null) {
            stopChildActor(connectConnectionHandler);
            connectConnectionHandler = null;
        }
        if (disconnectConnectionHandler != null) {
            stopChildActor(disconnectConnectionHandler);
            disconnectConnectionHandler = null;
        }
        super.cleanupFurtherResourcesOnConnectionTimeout(currentState);
    }

    @Override
    public void onException(final JMSException exception) {
        connectionLogger.exception("Exception occurred: {0}", exception.getMessage());
        logger.warning("{} occurred: {}", exception.getClass().getName(), exception.getMessage());
    }

    @Override
    protected ActorRef getPublisherActor() {
        return amqpPublisherActor;
    }

    private CompletionStage<Done> startCommandConsumers(final List<ConsumerData> consumers, final ActorRef jmsActor) {
        if (isConsuming()) {
            stopCommandConsumers();
            // wait a fraction of the configured timeout before asking to allow the consumer to stabilize
            final CompletionStage<Object> identity =
                    new CompletableFuture<>().completeOnTimeout(Done.getInstance(),
                            initialConsumerResourceStatusAskTimeout.toMillis() / 2, TimeUnit.MILLISECONDS);
            final CompletionStage<Object> completionStage = consumers.stream()
                    .map(consumer -> startCommandConsumer(consumer, getInboundMappingSink(), jmsActor))
                    .map(ref -> identity.thenCompose(done -> Patterns.ask(ref, RetrieveAddressStatus.getInstance(),
                            initialConsumerResourceStatusAskTimeout)))
                    .reduce(identity, (done, reply) -> done.thenCombine(reply, (x, y) -> x));
            connectionLogger.success("Subscriptions {0} initialized successfully", consumers);
            logger.info("Subscribed Connection <{}> to sources: {}", connectionId(), consumers);
            return completionStage.thenApply(object -> Done.getInstance()).exceptionally(t -> Done.getInstance());
        } else {
            logger.debug("Not starting consumers, no sources were configured");
            return CompletableFuture.completedStage(Done.getInstance());
        }
    }

    private ActorRef startCommandConsumer(final ConsumerData consumer, final Sink<Object, NotUsed> inboundMappingSink,
            final ActorRef jmsActor) {
        final String namePrefix = consumer.getActorNamePrefix();
        final Props props = AmqpConsumerActor.props(connection(), consumer, inboundMappingSink, jmsActor,
                connectivityStatusResolver, connectivityConfig());

        final ActorRef child = startChildActorConflictFree(namePrefix, props);
        consumerByNamePrefix.put(namePrefix, child);
        return child;
    }

    private void stopCommandConsumers() {
        streamConsumerActors().forEach(this::stopChildActor);
        consumerByNamePrefix.clear();
    }

    private ActorRef getTestConnectionHandler(final Connection connection) {
        if (testConnectionHandler == null) {
            testConnectionHandler = startConnectionHandlingActor("test", connection);
        }
        return testConnectionHandler;
    }

    private ActorRef getConnectConnectionHandler(final Connection connection) {
        if (connectConnectionHandler == null) {
            connectConnectionHandler = startConnectionHandlingActor("connect", connection);
        }
        return connectConnectionHandler;
    }

    private ActorRef getDisconnectConnectionHandler(final Connection connection) {
        if (disconnectConnectionHandler == null) {
            disconnectConnectionHandler = startConnectionHandlingActor("disconnect", connection);
        }
        return disconnectConnectionHandler;
    }

    private ActorRef startConnectionHandlingActor(final String suffix, final Connection connection) {
        final String namePrefix =
                JMSConnectionHandlingActor.ACTOR_NAME_PREFIX + escapeActorName(connectionId() + "-" + suffix);
        final Props props =
                JMSConnectionHandlingActor.propsWithOwnDispatcher(connection, this, jmsConnectionFactory,
                        connectionLogger);
        return startChildActorConflictFree(namePrefix, props);
    }

    /**
     * Close the JMS connection known to this actor in an isolated dispatcher because it is blocking.
     *
     * @return future where the closing operation executes.
     */
    @SuppressWarnings("UnusedReturnValue")
    private CompletableFuture<Void> ensureJmsConnectionClosed() {
        if (jmsConnection != null) {
            final JmsConnection jmsConnectionToClose = jmsConnection;
            final Runnable closeJmsConnectionRunnable = () -> {
                try {
                    jmsConnectionToClose.close();
                } catch (final Throwable error) {

                    // 'logger' is final and thread-safe. It is okay to use it in a future.
                    logger.error(error, "RESOURCE-LEAK: failed to close AMQP 1.0 Connection");
                    throw new RuntimeException(error);
                }
            };
            return CompletableFuture.runAsync(closeJmsConnectionRunnable,
                    JMSConnectionHandlingActor.getOwnDispatcher(getContext().system()));
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private FSM.State<BaseClientState, BaseClientData> handleConnectionRestored(final BaseClientData currentData) {
        if (recoverSessionOnConnectionRestored && (jmsSession == null || ((JmsSession) jmsSession).isClosed())) {
            logger.info("Restored connection has closed session, trying to recover ...");
            recoverSession(jmsSession);
        }
        return stay().using(currentData.setConnectionStatus(ConnectivityStatus.OPEN)
                .setRecoveryStatus(RecoveryStatus.SUCCEEDED)
                .setConnectionStatusDetails("Connection restored"));
    }

    private FSM.State<BaseClientState, BaseClientData> handleConnectionFailure(
            final ConnectionFailureStatusReport statusReport,
            final BaseClientData currentData) {

        final ConnectionFailure failure = statusReport.getFailure();
        connectionLogger.failure(failure.getFailureDescription());
        final ConnectivityStatus newStatus = connectivityStatusResolver.resolve(failure);

        if (!statusReport.isRecoverable()) {
            logger.info("Unrecoverable failure occurred, triggering client actor failure handling: {}", failure);
            getSelf().tell(failure, getSelf());
        }

        return stay().using(currentData.setConnectionStatus(newStatus)
                .setConnectionStatusDetails(failure.getFailureDescription()));
    }

    private FSM.State<BaseClientState, BaseClientData> handleConsumerClosed(
            final ConsumerClosedStatusReport statusReport, final BaseClientData currentData) {

        // broadcast event to consumers, who then decide whether the event is meant for them
        consumerByNamePrefix.forEach((namePrefix, consumerActor) -> consumerActor.tell(statusReport, getSelf()));

        return stay().using(currentData);
    }

    private FSM.State<BaseClientState, BaseClientData> handleProducerClosed(
            final ProducerClosedStatusReport statusReport,
            final BaseClientData currentData) {
        if (amqpPublisherActor != null) {
            amqpPublisherActor.tell(statusReport, ActorRef.noSender());
        }
        return stay().using(currentData);
    }

    private FSM.State<BaseClientState, BaseClientData> handleSessionClosed(final SessionClosedStatusReport statusReport,
            final BaseClientData currentData) {

        connectionLogger.failure("Session has been closed");
        if (recoverSessionOnSessionClosed) {
            recoverSession(statusReport.getSession());
        } else {
            logger.debug("Not recovering session after session was closed");
        }
        return stay().using(currentData);
    }

    private void recoverSession(@Nullable final Session session) {
        connectionLogger.failure("Trying to recover the session");
        logger.info("Recovering closed AMQP 1.0 session.");
        // first stop all child actors, they relied on the closed/corrupt session
        stopCommandConsumers();
        stopChildActor(amqpPublisherActor);
        // create a new session, result will be delivered with JmsSessionRecovered event
        getConnectConnectionHandler(connection()).tell(new JmsRecoverSession(getSender(), jmsConnection, session),
                getSelf());
    }

    private FSM.State<BaseClientState, BaseClientData> handleSessionRecovered(
            final JmsSessionRecovered sessionRecovered,
            final BaseClientData currentData) {

        // make sure that we close any previous session
        final ActorRef jmsActor = getConnectConnectionHandler(connection());
        if (jmsSession != null) {
            jmsActor.tell(new JmsCloseSession(getSender(), jmsSession), getSelf());
        }

        jmsSession = sessionRecovered.getSession();

        final CompletionStage<Status.Status> publisherReady = startPublisherActor();
        startCommandConsumers(sessionRecovered.getConsumerList(), jmsActor);

        publisherReady.thenRun(() -> connectionLogger.success("Session has been recovered successfully"))
                .exceptionally(t -> {
                    final ConnectionFailure failure = ConnectionFailure.of(null, t, "failed to recover session");
                    getSelf().tell(failure, getSelf());
                    return null;
                });

        return stay().using(currentData);
    }

    private boolean isRecoverSessionOnSessionClosedEnabled(final Connection connection) {
        final String recoverOnSessionClosed =
                connection.getSpecificConfig().getOrDefault(SPEC_CONFIG_RECOVER_ON_SESSION_CLOSED, "false");
        return Boolean.parseBoolean(recoverOnSessionClosed);
    }

    private boolean isRecoverSessionOnConnectionRestoredEnabled(final Connection connection) {
        final String recoverOnConnectionRestored =
                connection.getSpecificConfig().getOrDefault(SPEC_CONFIG_RECOVER_ON_CONNECTION_RESTORED, "true");
        return Boolean.parseBoolean(recoverOnConnectionRestored);
    }

    private JmsConnect jmsConnect(@Nullable final ActorRef sender, final Connection connection) {
        return new JmsConnect(sender, getClientId(connection.getId()));
    }

    private Stream<ActorRef> streamConsumerActors() {
        return consumerByNamePrefix.values()
                .stream()
                .filter(child -> child.path().name().startsWith(AmqpConsumerActor.ACTOR_NAME_PREFIX));
    }

    /**
     * {@code Connect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static final class JmsConnect extends AbstractWithOrigin implements ConnectClient {

        private final String clientId;

        JmsConnect(@Nullable final ActorRef origin, final String clientId) {
            super(origin);
            this.clientId = clientId;
        }

        @Override
        public String getClientId() {
            return clientId;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            final var that = (JmsConnect) o;
            return Objects.equals(clientId, that.clientId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), clientId);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" + super.toString() +
                    ", clientId=" + clientId +
                    "]";
        }

    }

    /**
     * {@code RecoverSession} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static final class JmsRecoverSession extends AbstractWithOrigin implements RecoverSession {

        private final javax.jms.Connection connection;
        @Nullable private final Session session;

        JmsRecoverSession(@Nullable final ActorRef origin,
                @Nullable final javax.jms.Connection connection,
                @Nullable final Session session) {

            super(origin);
            this.connection = connection;
            this.session = session;
        }

        Optional<javax.jms.Connection> getConnection() {
            return Optional.ofNullable(connection);
        }

        Optional<javax.jms.Session> getSession() {
            return Optional.ofNullable(session);
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            final var that = (JmsRecoverSession) o;
            return Objects.equals(connection, that.connection) && Objects.equals(session, that.session);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), connection, session);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" + super.toString() +
                    ", connection=" + connection +
                    ", session=" + session +
                    "]";
        }

    }

    /**
     * {@code CloseSession} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static final class JmsCloseSession extends AbstractWithOrigin implements CloseSession {

        private final Session session;

        JmsCloseSession(@Nullable final ActorRef origin, final Session session) {
            super(origin);
            this.session = session;
        }

        javax.jms.Session getSession() {
            return session;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            final var that = (JmsCloseSession) o;
            return Objects.equals(session, that.session);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), session);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" + super.toString() +
                    ", session=" + session +
                    "]";
        }

    }

    /**
     * {@code Disconnect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static final class JmsDisconnect extends AbstractWithOrigin implements DisconnectClient {

        @Nullable private final javax.jms.Connection connection;
        private final boolean shutdownAfterDisconnect;

        JmsDisconnect(@Nullable final ActorRef origin,
                @Nullable final javax.jms.Connection connection,
                final boolean shutdownAfterDisconnect) {

            super(origin);
            this.connection = connection;
            this.shutdownAfterDisconnect = shutdownAfterDisconnect;
        }

        Optional<javax.jms.Connection> getConnection() {
            return Optional.ofNullable(connection);
        }

        boolean isShutdownAfterDisconnect() {
            return shutdownAfterDisconnect;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            final var that = (JmsDisconnect) o;
            return shutdownAfterDisconnect == that.shutdownAfterDisconnect &&
                    Objects.equals(connection, that.connection);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), connection, shutdownAfterDisconnect);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" + super.toString() +
                    ", connection=" + connection +
                    ", shutdownAfterDisconnect=" + shutdownAfterDisconnect +
                    "]";
        }

    }

    /**
     * Response to {@code Connect} message from {@link JMSConnectionHandlingActor}.
     */
    static final class JmsConnected extends AbstractWithOrigin implements ClientConnected {

        private final JmsConnection connection;
        @Nullable private final Session session;
        private final List<ConsumerData> consumerList;

        JmsConnected(@Nullable final ActorRef origin,
                final JmsConnection connection,
                @Nullable final Session session,
                final List<ConsumerData> consumerList) {

            super(origin);
            this.connection = connection;
            this.session = session;
            this.consumerList = consumerList;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            final var that = (JmsConnected) o;
            return Objects.equals(connection, that.connection) &&
                    Objects.equals(session, that.session) &&
                    Objects.equals(consumerList, that.consumerList);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), connection, session, consumerList);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" + super.toString() +
                    ", connection=" + connection +
                    ", session=" + session +
                    ", consumerList=" + consumerList +
                    "]";
        }

    }

    /**
     * Response to {@code RecoverSession} message from {@link JMSConnectionHandlingActor}.
     */
    static final class JmsSessionRecovered extends AbstractWithOrigin {

        @Nullable private final Session session;
        private final List<ConsumerData> consumerList;

        JmsSessionRecovered(@Nullable final ActorRef origin,
                @Nullable final Session session,
                final List<ConsumerData> consumerList) {

            super(origin);
            this.session = session;
            this.consumerList = consumerList;
        }

        @Nullable
        Session getSession() {
            return session;
        }

        List<ConsumerData> getConsumerList() {
            return consumerList;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            final var that = (JmsSessionRecovered) o;
            return Objects.equals(session, that.session) && Objects.equals(consumerList, that.consumerList);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), session, consumerList);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" + super.toString() +
                    ", session=" + session +
                    ", consumerList=" + consumerList +
                    "]";
        }

    }

    /**
     * Listener updates connection status for metrics reporting. Do not alter actor state.
     */
    @Immutable
    static final class StatusReportingListener implements JmsConnectionListener {

        private final ActorRef self;
        private final ThreadSafeDittoLoggingAdapter logger;
        private final ConnectionLogger connectionLogger;

        private StatusReportingListener(final ActorRef self, final ThreadSafeDittoLoggingAdapter logger,
                final ConnectionLogger connectionLogger) {

            this.self = self;
            this.logger = logger;
            this.connectionLogger = connectionLogger;
        }

        @Override
        public void onConnectionEstablished(final URI remoteURI) {
            logger.info("Connection established: {}", remoteURI);
        }

        @Override
        public void onConnectionFailure(final Throwable error) {
            connectionLogger.failure("Connection failure: {0}", error.getMessage());
            logger.warning("Connection Failure: {}", error.getMessage());
            final ConnectionFailure failure = ConnectionFailure.of(ActorRef.noSender(), error, null);
            self.tell(ConnectionFailureStatusReport.get(failure, false), ActorRef.noSender());
        }

        @Override
        public void onConnectionInterrupted(final URI remoteURI) {
            connectionLogger.failure("Connection was interrupted");
            logger.warning("Connection interrupted: {}", remoteURI);
            final ConnectionFailure failure =
                    ConnectionFailure.userRelated(ActorRef.noSender(), null, "JMS Interrupted");
            self.tell(ConnectionFailureStatusReport.get(failure, true), ActorRef.noSender());
        }

        @Override
        public void onConnectionRestored(final URI remoteURI) {
            connectionLogger.success("Connection was restored");
            logger.info("Connection restored: {}", remoteURI);
            self.tell(ConnectionRestoredStatusReport.get(), ActorRef.noSender());
        }

        @Override
        public void onInboundMessage(final JmsInboundMessageDispatch envelope) {
            logger.debug("Inbound message: {}", envelope);
        }

        @Override
        public void onSessionClosed(final Session session, final Throwable cause) {
            connectionLogger.failure("Session was closed: {0}", cause.getMessage());
            logger.warning("Session closed: {} - {}", session, cause.getMessage());
            final ConnectionFailure failure =
                    ConnectionFailure.of(ActorRef.noSender(), cause, "AMQP 1.0 Session closed");
            self.tell(SessionClosedStatusReport.get(failure, session), ActorRef.noSender());
        }

        @Override
        public void onConsumerClosed(final MessageConsumer consumer, final Throwable cause) {
            connectionLogger.failure("Consumer {0} was closed: {1}", consumer, cause.getMessage());
            logger.warning("Consumer <{}> closed due to {}: {}", consumer, cause.getClass().getSimpleName(),
                    cause.getMessage());
            self.tell(ConsumerClosedStatusReport.get(consumer, cause), ActorRef.noSender());
        }

        @Override
        public void onProducerClosed(final MessageProducer producer, final Throwable cause) {
            connectionLogger.failure("Producer {0} was closed: {1}", producer.toString(), cause.getMessage());
            logger.warning("Producer <{}> closed due to {}: {}", producer, cause.getClass().getSimpleName(),
                    cause.getMessage());
            self.tell(ProducerClosedStatusReport.get(producer, cause), ActorRef.noSender());
        }

    }

}
