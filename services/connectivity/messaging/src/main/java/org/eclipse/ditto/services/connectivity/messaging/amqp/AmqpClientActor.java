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
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsConnectionListener;
import org.apache.qpid.jms.message.JmsInboundMessageDispatch;
import org.apache.qpid.jms.provider.ProviderFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.internal.AbstractWithOrigin;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientConnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectClient;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.DisconnectClient;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ReconnectClient;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.Pair;
import akka.pattern.PatternsCS;
import akka.util.Timeout;

/**
 * Actor which manages a connection to an AMQP 1.0 server using the Qpid JMS client.
 * This actor delegates interaction with the JMS client to a child actor because the JMS client blocks in most cases
 * which does not work well with actors.
 */
public final class AmqpClientActor extends BaseClientActor implements ExceptionListener {

    private static final int TEST_CONNECTION_TIMEOUT = 5;

    private final JmsConnectionFactory jmsConnectionFactory;
    private final ConnectionListener connectionListener;
    private final List<ConsumerData> consumers;

    @Nullable private JmsConnection jmsConnection;
    @Nullable private Session jmsSession;
    @Nullable private ActorRef amqpPublisherActor;

    @Nullable private ActorRef testConnectionHandler;
    @Nullable private ActorRef connectConnectionHandler;
    @Nullable private ActorRef disconnectConnectionHandler;

    private final Map<String, ActorRef> consumerByNamePrefix;

    /*
     * This constructor is called via reflection by the static method propsForTest.
     */
    @SuppressWarnings("unused")
    private AmqpClientActor(final Connection connection, final ConnectionStatus connectionStatus,
            final JmsConnectionFactory jmsConnectionFactory, final ActorRef conciergeForwarder) {
        super(connection, connectionStatus, conciergeForwarder);
        this.jmsConnectionFactory = jmsConnectionFactory;
        connectionListener = new ConnectionListener();
        consumers = new LinkedList<>();
        consumerByNamePrefix = new HashMap<>();
    }

    /*
     * This constructor is called via reflection by the static method props(Connection, ActorRef).
     */
    @SuppressWarnings("unused")
    private AmqpClientActor(final Connection connection, final ConnectionStatus connectionStatus,
            final ActorRef conciergeForwarder) {
        this(connection, connectionStatus, ConnectionBasedJmsConnectionFactory.getInstance(), conciergeForwarder);
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
    static Props propsForTests(final Connection connection, final ConnectionStatus connectionStatus,
            final ActorRef conciergeForwarder, final JmsConnectionFactory jmsConnectionFactory) {
        return Props.create(AmqpClientActor.class, validateConnection(connection), connectionStatus,
                jmsConnectionFactory, conciergeForwarder);
    }

    private static Connection validateConnection(final Connection connection) {
        try {
            final URI uri =
                    URI.create(ConnectionBasedJmsConnectionFactory.buildAmqpConnectionUriFromConnection(connection));
            ProviderFactory.create(uri);
            return connection;
        } catch (final Exception e) {
            final String errorMessageTemplate =
                    "Failed to instantiate an amqp provider from the given configuration: {0}";
            final String errorMessage = MessageFormat.format(errorMessageTemplate, e.getMessage());
            throw ConnectionConfigurationInvalidException
                    .newBuilder(errorMessage)
                    .description(e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final Connection connection) {
        // delegate to child actor because the QPID JMS client is blocking until connection is opened/closed
        return PatternsCS.ask(getTestConnectionHandler(connection),
                new JmsConnect(getSender()), Timeout.apply(TEST_CONNECTION_TIMEOUT, TimeUnit.SECONDS))
                .handle((response, throwable) -> {
                    if (throwable != null || response instanceof Status.Failure || response instanceof Throwable) {
                        final Throwable ex =
                                (response instanceof Status.Failure) ? ((Status.Failure) response).cause() :
                                        (response instanceof Throwable) ? (Throwable) response : throwable;
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
    protected CompletionStage<Map<String, AddressMetric>> getSourceConnectionStatus(final Source source) {
        return collectAsList(consumers.stream()
                .map(consumerData -> {
                    final String namePrefix = consumerData.getActorNamePrefix();
                    final ActorRef child = consumerByNamePrefix.get(namePrefix);
                    return retrieveAddressMetric(consumerData.getAddressWithIndex(), namePrefix, child);
                }))
                .thenApply(entries ->
                        entries.stream().collect(Collectors.toMap(Pair::first, Pair::second)))
                .handle((result, error) -> {
                    if (error == null) {
                        return result;
                    } else {
                        log.error(error, "Error while aggregating sources ConnectionStatus: {}", error.getMessage());
                        return Collections.emptyMap();
                    }
                });
    }

    @Override
    protected CompletionStage<Map<String, AddressMetric>> getTargetConnectionStatus(final Target target) {

        final CompletionStage<Pair<String, AddressMetric>> targetEntryFuture =
                retrieveAddressMetric(target.getAddress(), AmqpPublisherActor.ACTOR_NAME, amqpPublisherActor);
        return targetEntryFuture
                .thenApply(targetEntry -> Collections.singletonMap(targetEntry.first(), targetEntry.second()))
                .handle((result, error) -> {
                    if (error == null) {
                        return result;
                    } else {
                        log.error(error, "Error while aggregating target ConnectionStatus: {}", error.getMessage());
                        return Collections.emptyMap();
                    }
                });
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        if (clientConnected instanceof JmsConnected) {
            final JmsConnected c = (JmsConnected) clientConnected;
            log.info("Received JmsConnected");
            this.jmsConnection = c.connection;
            this.jmsConnection.addConnectionListener(connectionListener);
            this.jmsSession = c.session;
            consumers.clear();
            consumers.addAll(c.consumerList);
            startCommandConsumers(consumers);
            startAmqpPublisherActor();
        } else {
            log.info("ClientConnected was not JmsConnected as expected, ignoring as this probably was a reconnection");
        }
    }

    @Override
    protected void cleanupResourcesForConnection() {
        log.debug("cleaning up");
        stopCommandConsumers();
        stopCommandProducer();
        this.jmsSession = null;
        if (jmsConnection != null) {
            jmsConnection.removeConnectionListener(connectionListener);
        }
        this.jmsConnection = null;
        if (amqpPublisherActor != null) {
            stopChildActor(amqpPublisherActor);
            amqpPublisherActor = null;
        }
        this.consumers.clear();
    }

    /*
     * Kill connection handlers on timeout to be able to handle the next command immediately.
     */
    @Override
    protected void cleanupFurtherResourcesOnConnectionTimeout(final BaseClientState currentState) {
        switch (currentState) {
            case CONNECTING:
                if (connectConnectionHandler != null) {
                    stopChildActor(connectConnectionHandler);
                    connectConnectionHandler = null;
                }
                break;
            case DISCONNECTING:
                if (disconnectConnectionHandler != null) {
                    stopChildActor(disconnectConnectionHandler);
                    disconnectConnectionHandler = null;
                }
                break;
            case TESTING:
                // no need to handle TESTING state because this actor stops after test timeout.
                break;
        }
        super.cleanupFurtherResourcesOnConnectionTimeout(currentState);
    }

    @Override
    protected Optional<ActorRef> getPublisherActor() {
        return Optional.ofNullable(amqpPublisherActor);
    }

    @Override
    protected boolean isEventUpToDate(final Object event, final BaseClientState state, final ActorRef sender) {
        switch (state) {
            case CONNECTING:
                return Objects.equals(sender, connectConnectionHandler);
            case DISCONNECTING:
                return Objects.equals(sender, disconnectConnectionHandler);
            case TESTING:
                // no need to check testConnectionHandler because test runs only once during this actor's lifetime
        }
        // ignore random events by default - they could come from a connection handler that is already dead
        return false;
    }

    private void startCommandConsumers(final List<ConsumerData> consumers) {
        final Optional<ActorRef> messageMappingProcessor = getMessageMappingProcessorActor();
        if (messageMappingProcessor.isPresent()) {
            if (isConsuming()) {
                stopCommandConsumers();
                consumers.forEach(consumer -> startCommandConsumer(consumer, messageMappingProcessor.get()));
                log.info("Subscribed Connection <{}> to sources: {}", connectionId(), consumers);
            } else {
                log.debug("Not starting consumers, no sources were configured");
            }
        } else {
            log.warning("The MessageMappingProcessor was not available and therefore no consumers were started!");
        }
    }

    private void startCommandConsumer(final ConsumerData consumer, final ActorRef messageMappingProcessor) {
        final String namePrefix = consumer.getActorNamePrefix();
        final AuthorizationContext authorizationContext = resolveAuthorizationContext(consumer.getSource());
        final Props props = AmqpConsumerActor.props(consumer.getAddress(), consumer.getMessageConsumer(),
                messageMappingProcessor, authorizationContext);

        final ActorRef child = startChildActorConflictFree(namePrefix, props);
        consumerByNamePrefix.put(namePrefix, child);
    }

    private void startAmqpPublisherActor() {
        if (isPublishing()) {
            stopCommandProducer();
            final String namePrefix = AmqpPublisherActor.ACTOR_NAME;
            if (jmsSession != null) {
                final Props props = AmqpPublisherActor.props(jmsSession);
                amqpPublisherActor = startChildActorConflictFree(namePrefix, props);
            } else {
                throw new IllegalStateException(
                        "Could not start AmqpPublisherActor due to missing jmsSession or connection");
            }
        } else {
            log.info("This client is not configured for publishing, not starting AmqpPublisherActor");
        }
    }

    private void stopCommandProducer() {
        if (amqpPublisherActor != null) {
            stopChildActor(amqpPublisherActor);
            amqpPublisherActor = null;
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

    @Override
    public void onException(final JMSException exception) {
        log.warning("{} occurred: {}", exception.getClass().getName(), exception.getMessage());
    }

    /**
     * {@code Connect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class JmsConnect extends AbstractWithOrigin implements ConnectClient {

        JmsConnect(@Nullable final ActorRef origin) {
            super(origin);
        }
    }

    /**
     * {@code Reconnect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class JmsReconnect extends AbstractWithOrigin implements ReconnectClient {

        private final javax.jms.Connection connection;

        JmsReconnect(@Nullable final ActorRef origin, @Nullable final javax.jms.Connection connection) {
            super(origin);
            this.connection = checkNotNull(connection, "connection");
        }

        javax.jms.Connection getConnection() {
            return connection;
        }
    }

    /**
     * {@code Disconnect} message for internal communication with {@link JMSConnectionHandlingActor}.
     */
    static class JmsDisconnect extends AbstractWithOrigin implements DisconnectClient {

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
    static class JmsConnected extends AbstractWithOrigin implements ClientConnected {

        private final JmsConnection connection;
        private final Session session;
        private final List<ConsumerData> consumerList;

        JmsConnected(@Nullable final ActorRef origin, final JmsConnection connection, final Session session,
                final List<ConsumerData> consumerList) {
            super(origin);
            this.connection = connection;
            this.session = session;
            this.consumerList = consumerList;
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

    /*
     * TODO: try not to modify actor state or read from mutable fields like amqpPublisherActor.
     */
    private class ConnectionListener implements JmsConnectionListener {

        @Override
        public void onConnectionEstablished(final URI remoteURI) {
            log.info("Connection established: {}", remoteURI);
        }

        @Override
        public void onConnectionFailure(final Throwable error) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.warning("Connection Failure: {}", error.getMessage());
            getSelf().tell(new ImmutableConnectionFailure(ActorRef.noSender(), error, null), ActorRef.noSender());
        }

        @Override
        public void onConnectionInterrupted(final URI remoteURI) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.warning("Connection interrupted: {}", remoteURI);
            getSelf().tell(new ImmutableConnectionFailure(ActorRef.noSender(), null, "JMS Interrupted"),
                    ActorRef.noSender());
        }

        @Override
        public void onConnectionRestored(final URI remoteURI) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.info("Connection restored: {}", remoteURI);
            getSelf().tell((ClientConnected) Optional::empty, ActorRef.noSender());
        }

        @Override
        public void onInboundMessage(final JmsInboundMessageDispatch envelope) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.debug("Inbound message: {}", envelope);
            incrementConsumedMessageCounter();
        }

        @Override
        public void onSessionClosed(final Session session, final Throwable cause) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.warning("Session closed: {} - {}", session, cause.getMessage());
            getSelf().tell(new ImmutableConnectionFailure(ActorRef.noSender(), cause, "JMS Session closed"),
                    ActorRef.noSender());
        }

        @Override
        public void onConsumerClosed(final MessageConsumer consumer, final Throwable cause) {

            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());

            consumers.stream()
                    .filter(c -> c.getMessageConsumer().equals(consumer))
                    .findFirst()
                    .ifPresent(c -> {
                        log.warning("Consumer <{}> closed due to {}: {}", c.getAddress(),
                                cause.getClass().getSimpleName(), cause.getMessage());
                        final ActorRef consumerActor = consumerByNamePrefix.get(c.getActorNamePrefix());
                        if (consumerActor != null) {
                            final Object message = ConnectivityModelFactory.newAddressMetric(ConnectionStatus.FAILED,
                                    "Consumer closed at " + Instant.now(),
                                    0, null);
                            consumerActor.tell(message, ActorRef.noSender());
                        }
                    });
        }

        @Override
        public void onProducerClosed(final MessageProducer producer, final Throwable cause) {
            LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId());
            log.warning("Producer <{}> closed due to {}: {}", producer, cause.getClass().getSimpleName(),
                    cause.getMessage());

            if (amqpPublisherActor != null) {
                final Object message = ConnectivityModelFactory.newAddressMetric(ConnectionStatus.FAILED,
                        "Producer closed at " + Instant.now(), 0, null);
                amqpPublisherActor.tell(message, ActorRef.noSender());
            }
        }
    }

}
