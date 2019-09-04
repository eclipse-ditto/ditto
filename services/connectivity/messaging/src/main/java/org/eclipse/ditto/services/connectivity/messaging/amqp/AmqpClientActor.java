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
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import java.net.URI;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.amqp.status.ConnectionFailureStatusReport;
import org.eclipse.ditto.services.connectivity.messaging.amqp.status.ConnectionRestoredStatusReport;
import org.eclipse.ditto.services.connectivity.messaging.amqp.status.ConsumerClosedStatusReport;
import org.eclipse.ditto.services.connectivity.messaging.amqp.status.ProducerClosedStatusReport;
import org.eclipse.ditto.services.connectivity.messaging.amqp.status.SessionClosedStatusReport;
import org.eclipse.ditto.services.connectivity.messaging.internal.AbstractWithOrigin;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.CloseSession;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectClient;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.DisconnectClient;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.RecoverSession;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;

import akka.actor.ActorRef;
import akka.actor.FSM;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.Patterns;

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

    /*
     * This constructor is called via reflection by the static method propsForTest.
     */
    @SuppressWarnings("unused")
    private AmqpClientActor(final Connection connection,
            final ConnectivityStatus connectionStatus,
            final JmsConnectionFactory jmsConnectionFactory,
            final ActorRef conciergeForwarder) {

        super(connection, connectionStatus, conciergeForwarder);
        this.jmsConnectionFactory = jmsConnectionFactory;
        connectionListener = new StatusReportingListener(getSelf(), connection.getId(), log, connectionLogger);
        consumerByNamePrefix = new HashMap<>();
        recoverSessionOnSessionClosed = isRecoverSessionOnSessionClosedEnabled();
        recoverSessionOnConnectionRestored = isRecoverSessionOnConnectionRestoredEnabled();
    }

    /*
     * This constructor is called via reflection by the static method props(Connection, ActorRef).
     */
    @SuppressWarnings("unused")
    private AmqpClientActor(final Connection connection,
            final ConnectivityStatus connectionStatus,
            final ActorRef conciergeForwarder) {

        this(connection, connectionStatus, ConnectionBasedJmsConnectionFactory.getInstance(),
                conciergeForwarder);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef conciergeForwarder) {

        return Props.create(AmqpClientActor.class, validateConnection(connection), connection.getConnectionStatus(),
                conciergeForwarder);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection connection parameters.
     * @param connectionStatus the desired status of the connection.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @param jmsConnectionFactory the JMS connection factory.
     * @return the Akka configuration Props object.
     */
    static Props propsForTests(final Connection connection,
            final ConnectivityStatus connectionStatus,
            final ActorRef conciergeForwarder,
            final JmsConnectionFactory jmsConnectionFactory) {

        return Props.create(AmqpClientActor.class, validateConnection(connection), connectionStatus,
                jmsConnectionFactory, conciergeForwarder);
    }

    private static Connection validateConnection(final Connection connection) {
        try {
            ProviderFactory.create(
                    URI.create(ConnectionBasedJmsConnectionFactory.buildAmqpConnectionUriFromConnection(connection)));
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
    public SupervisorStrategy supervisorStrategy() {
        return SupervisorStrategy.stoppingStrategy();
    }

    @Override
    public void postStop() {
        ensureJmsConnectionClosed();
        super.postStop();
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
                        (report, currentData) -> this.handleConnectionRestored(currentData))
                .event(ConnectionFailureStatusReport.class, this::handleConnectionFailure)
                .event(ConsumerClosedStatusReport.class, this::handleConsumerClosed)
                .event(ProducerClosedStatusReport.class, this::handleProducerClosed)
                .event(SessionClosedStatusReport.class, this::handleSessionClosed);
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        return Patterns.ask(getTestConnectionHandler(connection),
                new JmsConnect(getSender()), clientConfig.getTestingTimeout())
                // compose the disconnect because otherwise the actor hierarchy might be stopped too fast
                .thenCompose(response -> {
                    log.debug("Closing JMS connection after testing connection.");
                    if (response instanceof JmsConnected) {
                        final JmsConnection jmsConnection = ((JmsConnected) response).connection;
                        final JmsDisconnect jmsDisconnect = new JmsDisconnect(ActorRef.noSender(), jmsConnection);
                        return Patterns.ask(getDisconnectConnectionHandler(connection), jmsDisconnect,
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
                    } else if (response instanceof ConnectionFailure) {
                        return ((ConnectionFailure) response).getFailure();
                    } else {
                        return new Status.Success(response);
                    }
                });
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        getConnectConnectionHandler(connection).tell(new JmsConnect(origin), getSelf());
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin) {
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        getDisconnectConnectionHandler(connection)
                .tell(new JmsDisconnect(origin, jmsConnection), getSelf());
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        if (clientConnected instanceof JmsConnected) {
            final ActorRef jmsActor = getConnectConnectionHandler(connection());
            final JmsConnected c = (JmsConnected) clientConnected;
            log.info("Received JmsConnected");
            ensureJmsConnectionClosed();
            jmsConnection = c.connection;
            jmsConnection.addConnectionListener(connectionListener);
            jmsSession = c.session;
            // note: start order is important (publisher -> mapping -> consumer actor)
            startCommandProducer();
            startCommandConsumers(c.consumerList, jmsActor);
            getSelf().tell(getClientReady(), getSelf());
        } else {
            log.info("ClientConnected was not JmsConnected as expected, ignoring as this probably was a reconnection");
        }
    }

    @Override
    protected void cleanupResourcesForConnection() {
        log.debug("cleaning up resources for connection '{}'", connectionId());
        stopCommandConsumers();
        stopPublisherActor();
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
    protected boolean isEventUpToDate(final Object event, final BaseClientState state,
            @Nullable final ActorRef sender) {
        switch (state) {
            case CONNECTED:
                // while connected, events from publisher or consumer child actors are relevant
                return sender != null &&
                        sender.path().toStringWithoutAddress().startsWith(getSelf().path().toStringWithoutAddress()) &&
                        (sender.path().name().startsWith(AmqpConsumerActor.ACTOR_NAME_PREFIX) ||
                                sender.path().name().startsWith(AmqpPublisherActor.ACTOR_NAME_PREFIX));
            case CONNECTING:
                return Objects.equals(sender, connectConnectionHandler);
            case DISCONNECTING:
                return Objects.equals(sender, disconnectConnectionHandler);
            case TESTING:
            default:
                // no need to check testConnectionHandler because test runs only once during this actor's lifetime
                // ignore random events by default - they could come from a connection handler that is already dead
                return false;
        }
    }

    private void startCommandConsumers(final List<ConsumerData> consumers, final ActorRef jmsActor) {
        if (isConsuming()) {
            stopCommandConsumers();
            consumers.forEach(consumer -> startCommandConsumer(consumer, getMessageMappingProcessorActor(), jmsActor));
            connectionLogger.success("Subscriptions {0} initialized successfully.", consumers);
            log.info("Subscribed Connection <{}> to sources: {}", connectionId(), consumers);
        } else {
            log.debug("Not starting consumers, no sources were configured");
        }
    }

    private void startCommandConsumer(final ConsumerData consumer, final ActorRef messageMappingProcessor,
            final ActorRef jmsActor) {
        final String namePrefix = consumer.getActorNamePrefix();
        final Props props = AmqpConsumerActor.props(connectionId(), consumer, messageMappingProcessor, jmsActor);

        final ActorRef child = startChildActorConflictFree(namePrefix, props);
        consumerByNamePrefix.put(namePrefix, child);
    }

    private void startCommandProducer() {
        stopPublisherActor();
        final String namePrefix = AmqpPublisherActor.ACTOR_NAME_PREFIX;
        if (jmsSession != null) {
            final Props props =
                    AmqpPublisherActor.props(connectionId(), getTargetsOrEmptyList(), jmsSession,
                            connectivityConfig.getConnectionConfig());
            startChildActorConflictFree(namePrefix, props);
        } else {
            throw ConnectionFailedException
                    .newBuilder(connectionId())
                    .message("Could not start publisher actor due to missing JMS session or connection!")
                    .build();
        }
    }

    private void stopCommandConsumers() {
        consumerByNamePrefix.forEach((namePrefix, child) -> {
            final String actorName = child.path().name();
            if (actorName.startsWith(AmqpConsumerActor.ACTOR_NAME_PREFIX)) {
                stopChildActor(child);
            }
        });
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
                JMSConnectionHandlingActor.propsWithOwnDispatcher(connection, this, jmsConnectionFactory);
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
                    // 'log' is final. It is okay to use it in a future.
                    log.error(error, "RESOURCE-LEAK: failed to close JMSConnection");
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
            log.info("Restored connection has closed session, trying to recover...");
            recoverSession(jmsSession);
        }
        return stay().using(currentData.setConnectionStatus(ConnectivityStatus.OPEN)
                .setConnectionStatusDetails("Connection restored"));
    }

    private FSM.State<BaseClientState, BaseClientData> handleConnectionFailure(
            final ConnectionFailureStatusReport statusReport,
            final BaseClientData currentData) {

        final ConnectionFailure failure = statusReport.getFailure();
        final String message = MessageFormat.format("Failure: {0}, Description: {1}",
                failure.getFailure().cause(), failure.getFailureDescription());
        connectionLogger.failure(message);
        return stay().using(currentData.setConnectionStatus(ConnectivityStatus.FAILED)
                .setConnectionStatusDetails(message));
    }

    private FSM.State<BaseClientState, BaseClientData> handleConsumerClosed(
            final ConsumerClosedStatusReport statusReport,
            final BaseClientData currentData) {

        // broadcast event to consumers, who then decide whether the event is meant for them
        consumerByNamePrefix.forEach((namePrefix, consumerActor) -> consumerActor.tell(statusReport, getSelf()));

        return stay().using(currentData);
    }

    private FSM.State<BaseClientState, BaseClientData> handleProducerClosed(
            final ProducerClosedStatusReport statusReport,
            final BaseClientData currentData) {
        if (getPublisherActor() != null) {
            getPublisherActor().tell(statusReport, ActorRef.noSender());
        }
        return stay().using(currentData);
    }

    private FSM.State<BaseClientState, BaseClientData> handleSessionClosed(
            final SessionClosedStatusReport statusReport,
            final BaseClientData currentData) {
        connectionLogger.failure("Session has been closed.");
        if (recoverSessionOnSessionClosed) {
            recoverSession(statusReport.getSession());
        } else {
            log.debug("Not recovering session after session was closed.");
        }
        return stay().using(currentData);
    }

    private void recoverSession(@Nullable final Session session) {
        connectionLogger.failure("Trying to recover the session.");
        log.info("Recovering closed JMS session.");
        // first stop all child actors, they relied on the closed/corrupt session
        stopCommandConsumers();
        stopPublisherActor();
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
        // note: start order is important (publisher -> mapping -> consumer actor)
        startCommandProducer();
        startCommandConsumers(sessionRecovered.getConsumerList(), jmsActor);

        connectionLogger.success("Session has been recovered successfully.");

        return stay().using(currentData);
    }

    private boolean isRecoverSessionOnSessionClosedEnabled() {
        final String recoverOnSessionClosed =
                connection().getSpecificConfig().getOrDefault(SPEC_CONFIG_RECOVER_ON_SESSION_CLOSED, "false");
        return Boolean.parseBoolean(recoverOnSessionClosed);
    }

    private boolean isRecoverSessionOnConnectionRestoredEnabled() {
        final String recoverOnConnectionRestored =
                connection().getSpecificConfig().getOrDefault(SPEC_CONFIG_RECOVER_ON_CONNECTION_RESTORED, "true");
        return Boolean.parseBoolean(recoverOnConnectionRestored);
    }

    @Override
    public void onException(final JMSException exception) {
        connectionLogger.exception("Exception occurred: {0}", exception.getMessage());
        log.warning("{} occurred: {}", exception.getClass().getName(), exception.getMessage());
    }

    /**
     * {@code Connect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static final class JmsConnect extends AbstractWithOrigin implements ConnectClient {

        JmsConnect(@Nullable final ActorRef origin) {
            super(origin);
        }

    }

    /**
     * {@code RecoverSession} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static final class JmsRecoverSession extends AbstractWithOrigin implements RecoverSession {

        private final javax.jms.Connection connection;
        @Nullable private final Session session;

        JmsRecoverSession(@Nullable final ActorRef origin, @Nullable final javax.jms.Connection connection,
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
    }

    /**
     * {@code Disconnect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static final class JmsDisconnect extends AbstractWithOrigin implements DisconnectClient {

        @Nullable private final javax.jms.Connection connection;

        JmsDisconnect(@Nullable final ActorRef origin, @Nullable final javax.jms.Connection connection) {
            super(origin);
            this.connection = connection;
        }

        Optional<javax.jms.Connection> getConnection() {
            return Optional.ofNullable(connection);
        }
    }

    /**
     * Response to {@code Connect} message from {@link JMSConnectionHandlingActor}.
     */
    static final class JmsConnected extends AbstractWithOrigin implements ClientConnected {

        private final JmsConnection connection;
        private final Session session;
        private final List<ConsumerData> consumerList;

        JmsConnected(@Nullable final ActorRef origin,
                final JmsConnection connection,
                final Session session,
                final List<ConsumerData> consumerList) {

            super(origin);
            this.connection = connection;
            this.session = session;
            this.consumerList = consumerList;
        }
    }

    /**
     * Response to {@code RecoverSession} message from {@link JMSConnectionHandlingActor}.
     */
    static final class JmsSessionRecovered extends AbstractWithOrigin {

        private final Session session;
        private final List<ConsumerData> consumerList;

        JmsSessionRecovered(@Nullable final ActorRef origin,
                final Session session,
                final List<ConsumerData> consumerList) {

            super(origin);
            this.session = session;
            this.consumerList = consumerList;
        }

        Session getSession() {
            return session;
        }

        List<ConsumerData> getConsumerList() {
            return consumerList;
        }
    }

    /**
     * Response to {@code Disconnect} message from {@link JMSConnectionHandlingActor}.
     */
    static class JmsDisconnected extends AbstractWithOrigin implements ClientDisconnected {

        JmsDisconnected(@Nullable final ActorRef origin) {
            super(origin);
        }

    }

    /**
     * Listener updates connection status for metrics reporting. Do not alter actor state.
     */
    @Immutable
    static final class StatusReportingListener implements JmsConnectionListener {

        private final ActorRef self;
        private final DiagnosticLoggingAdapter log;
        private final String connectionId;
        private final ConnectionLogger connectionLogger;

        private StatusReportingListener(final ActorRef self, final String connectionId,
                final DiagnosticLoggingAdapter log, final ConnectionLogger connectionLogger) {

            this.self = self;
            this.connectionId = connectionId;
            this.log = log;
            this.connectionLogger = connectionLogger;
        }

        @Override
        public void onConnectionEstablished(final URI remoteURI) {
            log.info("Connection established: {}", remoteURI);
        }

        @Override
        public void onConnectionFailure(final Throwable error) {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
            connectionLogger.failure("Connection failure: {0}", error.getMessage());
            log.warning("Connection Failure: {}", error.getMessage());
            final ConnectionFailure failure =
                    new ImmutableConnectionFailure(ActorRef.noSender(), error, null);
            self.tell(ConnectionFailureStatusReport.get(failure), ActorRef.noSender());
        }

        @Override
        public void onConnectionInterrupted(final URI remoteURI) {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
            connectionLogger.failure("Connection was interrupted.");
            log.warning("Connection interrupted: {}", remoteURI);
            final ConnectionFailure failure =
                    new ImmutableConnectionFailure(ActorRef.noSender(), null, "JMS Interrupted");
            self.tell(ConnectionFailureStatusReport.get(failure), ActorRef.noSender());
        }

        @Override
        public void onConnectionRestored(final URI remoteURI) {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
            connectionLogger.success("Connection was restored.");
            log.info("Connection restored: {}", remoteURI);
            self.tell(ConnectionRestoredStatusReport.get(), ActorRef.noSender());
        }

        @Override
        public void onInboundMessage(final JmsInboundMessageDispatch envelope) {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
            log.debug("Inbound message: {}", envelope);
        }

        @Override
        public void onSessionClosed(final Session session, final Throwable cause) {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
            connectionLogger.failure("Session was closed: {0}", cause.getMessage());
            log.warning("Session closed: {} - {}", session, cause.getMessage());
            final ConnectionFailure failure =
                    new ImmutableConnectionFailure(ActorRef.noSender(), cause, "JMS Session closed");
            self.tell(SessionClosedStatusReport.get(failure, session), ActorRef.noSender());
        }

        @Override
        public void onConsumerClosed(final MessageConsumer consumer, final Throwable cause) {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
            connectionLogger.failure("Consumer {0} was closed: {1}", consumer, cause.getMessage());
            log.warning("Consumer <{}> closed due to {}: {}", consumer, cause.getClass().getSimpleName(),
                    cause.getMessage());
            self.tell(ConsumerClosedStatusReport.get(consumer), ActorRef.noSender());
        }

        @Override
        public void onProducerClosed(final MessageProducer producer, final Throwable cause) {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
            connectionLogger.failure("Producer {0} was closed: {1}", producer.toString(), cause.getMessage());
            log.warning("Producer <{}> closed due to {}: {}", producer, cause.getClass().getSimpleName(),
                    cause.getMessage());
            self.tell(ProducerClosedStatusReport.get(producer), ActorRef.noSender());
        }

    }

}
