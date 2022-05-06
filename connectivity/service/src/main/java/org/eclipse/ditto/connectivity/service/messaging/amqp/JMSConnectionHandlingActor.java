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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.naming.NamingException;

import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsQueue;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionFailedException;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionUnauthorizedException;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.dispatch.MessageDispatcher;
import akka.event.DiagnosticLoggingAdapter;

/**
 * This actor executes operations (connect/disconnect) on JMS Connection/Session. It is separated into an actor
 * because the JMS Client is blocking which makes it impossible to e.g. cancel a pending connection attempts with
 * another actor message when done in the same actor.
 * <p>
 * WARNING: This actor blocks! Start with its own dispatcher!
 * </p>
 */
public final class JMSConnectionHandlingActor extends AbstractActor {

    /**
     * Magic number to activate individual message acknowledgement. Qpid JMS client provides no public constant for
     * this value but permits its use for session creation.
     * <p>
     * Reference:
     * JmsAcknowledgeCallback.java:51 individual acknowledgement is performed when envelope is set
     * JmsMessageConsumer.java:500    envelope is set when session has ack mode 101
     * JmsConnection.java:315         ack mode is passed verbatim to JmsSession constructor after validation
     * JmsConnection.java:554         call to JmsSession.validateSessionMode, which accepts 101 as valid
     */
    private static final int JMS_INDIVIDUAL_ACKNOWLEDGE_MODE_MAGIC_NUMBER = 101;

    /**
     * The Actor name prefix.
     */
    static final String ACTOR_NAME_PREFIX = "jmsConnectionHandling-";

    /**
     * Config key of the dispatcher for this actor.
     */
    private static final String DISPATCHER_NAME = "jms-connection-handling-dispatcher";

    private final DiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final Connection connection;
    private final ExceptionListener exceptionListener;
    private final JmsConnectionFactory jmsConnectionFactory;
    private final ConnectionLogger connectionLogger;

    @Nullable private Session currentSession = null;

    @SuppressWarnings("unused")
    private JMSConnectionHandlingActor(final Connection connection,
            final ExceptionListener exceptionListener,
            final JmsConnectionFactory jmsConnectionFactory, final ConnectionLogger connectionLogger) {

        this.connection = checkNotNull(connection, "connection");
        this.exceptionListener = exceptionListener;
        this.jmsConnectionFactory = jmsConnectionFactory;
        this.connectionLogger = connectionLogger;
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code JMSConnectionHandlingActor}.
     *
     * @param connection the connection
     * @param exceptionListener the exception listener
     * @param jmsConnectionFactory the jms connection factory
     * @param connectionLogger used to log failures during certificate validation.
     * @return the Akka configuration Props object.
     */
    static Props props(final Connection connection, final ExceptionListener exceptionListener,
            final JmsConnectionFactory jmsConnectionFactory, final ConnectionLogger connectionLogger) {

        return Props.create(JMSConnectionHandlingActor.class, connection, exceptionListener,
                jmsConnectionFactory,
                connectionLogger);
    }

    static Props propsWithOwnDispatcher(final Connection connection,
            final ExceptionListener exceptionListener,
            final JmsConnectionFactory jmsConnectionFactory,
            final ConnectionLogger connectionLogger) {
        return props(connection, exceptionListener, jmsConnectionFactory, connectionLogger)
                .withDispatcher(DISPATCHER_NAME);
    }

    /**
     * Get dispatcher of this actor, which should be good for blocking operations.
     *
     * @param actorSystem actor system where this actor is configured.
     * @return the dispatcher.
     */
    static MessageDispatcher getOwnDispatcher(final ActorSystem actorSystem) {
        return actorSystem.dispatchers().lookup(DISPATCHER_NAME);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AmqpClientActor.JmsConnect.class, this::handleConnect)
                .match(AmqpClientActor.JmsRecoverSession.class, this::handleRecoverSession)
                .match(AmqpClientActor.JmsCloseSession.class, this::handleCloseSession)
                .match(AmqpClientActor.JmsDisconnect.class, this::handleDisconnect)
                .match(AmqpConsumerActor.CreateMessageConsumer.class, this::createMessageConsumer)
                .build();
    }

    private void createMessageConsumer(final AmqpConsumerActor.CreateMessageConsumer command) {
        final Throwable error;
        if (currentSession != null) {
            // create required consumer
            final ConsumerData consumerData = command.getConsumerData();
            final ConsumerData newConsumerData =
                    createJmsConsumer(currentSession, new HashMap<>(), consumerData.getSource(),
                            consumerData.getAddress(), consumerData.getAddressWithIndex());
            if (newConsumerData != null) {
                final Object response = command.toResponse(newConsumerData.getMessageConsumer());
                getSender().tell(response, getSelf());
                error = null;
            } else {
                error = new IllegalStateException("Failed to create message consumer");
            }
        } else {
            error = new IllegalStateException("No session");
        }
        if (error != null) {
            getSender().tell(new Status.Failure(error), getSelf());
        }
    }

    private void handleCloseSession(final AmqpClientActor.JmsCloseSession closeSession) {
        log.debug("Processing JmsCloseSession message.");
        final Session session = closeSession.getSession();
        try {
            safelyExecuteJmsOperation(null, "close session", () -> {
                session.close();
                return null;
            });
            if (Objects.equals(session, currentSession)) {
                currentSession = null;
            }
        } catch (final Exception e) {
            log.debug("Closing session failed: {}", e.getMessage());
        }
    }

    private void handleRecoverSession(final AmqpClientActor.JmsRecoverSession recoverSession) {

        log.debug("Processing JmsRecoverSession message.");
        final ActorRef sender = getSender();
        final ActorRef origin = recoverSession.getOrigin().orElse(null);
        final ActorRef self = getSelf();

        // try to close an existing session first
        recoverSession.getSession().ifPresent(session -> {
            try {
                session.close();
            } catch (final JMSException e) {
                log.debug("Failed to close previous session, ignore.");
            }
        });

        final Optional<javax.jms.Connection> connectionOptional = recoverSession.getConnection();

        if (connectionOptional.isPresent()) {
            final JmsConnection jmsConnection = (JmsConnection) connectionOptional.get();
            try {
                log.debug("Creating new JMS session.");
                final Session session = createSession(jmsConnection);
                log.debug("Creating consumers for new session <{}>.", session);
                final List<ConsumerData> consumers = createConsumers(session);
                final AmqpClientActor.JmsSessionRecovered r =
                        new AmqpClientActor.JmsSessionRecovered(origin, session, consumers);
                sender.tell(r, self);
                log.debug("Session of connection <{}> recovered successfully.", connection.getId());
            } catch (final ConnectionFailedException e) {
                sender.tell(ConnectionFailure.of(origin, e, null), self);
                log.warning(e.getMessage());
            } catch (final ConnectionUnauthorizedException e) {
                sender.tell(ConnectionFailure.userRelated(origin, e, null), self);
                log.warning(e.getMessage());
            } catch (final Exception e) {
                sender.tell(ConnectionFailure.of(origin, e, null), self);
                log.error("Unexpected error: {}", e.getMessage());
            }
        } else {
            log.info("Recovering session failed, no connection available.");
            sender.tell(ConnectionFailure.of(origin, null,
                    "Session recovery failed, no connection available."), self);
        }
    }

    private void handleConnect(final AmqpClientActor.JmsConnect connect) {
        maybeConnectAndTell(getSender(), connect.getOrigin().orElse(null), connect.getClientId());
    }

    private void handleDisconnect(final AmqpClientActor.JmsDisconnect disconnect) {
        final Optional<javax.jms.Connection> connectionOpt = disconnect.getConnection();
        if (connectionOpt.isPresent()) {
            disconnectAndTell(connectionOpt.get(), disconnect.getOrigin().orElse(null),
                    disconnect.isShutdownAfterDisconnect());
        } else {
            final Object answer = ClientDisconnected.of(disconnect.getOrigin().orElse(null),
                    disconnect.isShutdownAfterDisconnect());
            getSender().tell(answer, getSelf());
        }
    }

    /*
     * This method should be thread-safe.
     */
    private void maybeConnectAndTell(final ActorRef sender, @Nullable final ActorRef origin, final String clientId) {
        final ActorRef self = getSelf(); // getSelf() is thread-safe
        try {
            final AmqpClientActor.JmsConnected connectedMessage = tryConnect(origin, clientId);
            sender.tell(connectedMessage, self);
            log.debug("Connection <{}> established successfully.", connection.getId());
        } catch (final ConnectionFailedException e) {
            sender.tell(ConnectionFailure.of(origin, e, null), self);
            log.warning(e.getMessage());
        } catch (final ConnectionUnauthorizedException e) {
            sender.tell(ConnectionFailure.userRelated(origin, e, null), self);
            log.warning(e.getMessage());
        } catch (final Exception e) {
            sender.tell(ConnectionFailure.of(origin, e, null), self);
            log.error("Unexpected error: {}", e.getMessage());
        }
    }

    private AmqpClientActor.JmsConnected tryConnect(@Nullable final ActorRef origin, final String clientId) {
        final JmsConnection jmsConnection = createJmsConnection(clientId);
        if (null != jmsConnection) {
            try {
                startConnection(jmsConnection);
                final Session session = createSession(jmsConnection);
                final List<ConsumerData> consumers = createConsumers(session);
                return new AmqpClientActor.JmsConnected(origin, jmsConnection, session, consumers);
            } catch (final ConnectionFailedException | ConnectionUnauthorizedException e) {
                // thrown by createConsumers
                terminateConnection(jmsConnection);
                throw e;
            } catch (final RuntimeException e) {
                log.error(e, "An unexpected exception occurred. Terminating JMS connection.");
                terminateConnection(jmsConnection);
                throw e;
            }
        } else {
            log.error("Created JMS connection for clientId <{}> was null, " +
                    "not creating connection as a result!", clientId);
            throw ConnectionFailedException.newBuilder(connection.getId()).build();
        }
    }

    private void startConnection(final JmsConnection jmsConnection) {
        safelyExecuteJmsOperation(jmsConnection, "connect JMS client", () -> {
            jmsConnection.start();
            log.debug("Connection started successfully");
            return null;
        });
    }

    @Nullable
    private Session createSession(final JmsConnection jmsConnection) {
        final Session session = safelyExecuteJmsOperation(jmsConnection, "create session",
                () -> jmsConnection.createSession(JMS_INDIVIDUAL_ACKNOWLEDGE_MODE_MAGIC_NUMBER));
        currentSession = session;
        return session;
    }

    @Nullable
    private <T> T safelyExecuteJmsOperation(@Nullable final JmsConnection jmsConnection,
            final String task, final ThrowingSupplier<T> jmsOperation) {

        try {
            return jmsOperation.get();
        } catch (final JMSSecurityException e) {
            terminateConnection(jmsConnection);
            throw ConnectionUnauthorizedException.forConnectionId(connection.getId(), e.getMessage());
        } catch (final JMSException | NamingException e) {
            terminateConnection(jmsConnection);
            throw ConnectionFailedException.newBuilder(connection.getId())
                    .message("Failed to " + task + ":" + e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    /**
     * Uses the given session to create the specified count of message consumers for every sources addresses.
     *
     * @param session the session
     * @return the consumers
     * @throws org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionFailedException if creation of one
     * or more consumers failed
     */
    private List<ConsumerData> createConsumers(@Nullable final Session session) {
        if (null == session) {
            log.warning("Trying to create consumer for 'null' session, throwing ConnectionFailedException");
            throw ConnectionFailedException.newBuilder(connection.getId()).build();
        }

        final Map<String, Exception> failedSources = new HashMap<>();
        final List<ConsumerData> consumers = connection.getSources().stream().flatMap(source ->
                source.getAddresses().stream().flatMap(sourceAddress ->
                        IntStream.range(0, source.getConsumerCount())
                                .mapToObj(i -> sourceAddress + "-" + i)
                                .map(addressWithIndex -> createJmsConsumer(session, failedSources, source,
                                        sourceAddress, addressWithIndex))
                                .filter(Objects::nonNull)
                )
        ).toList();

        if (!failedSources.isEmpty()) {
            if (log.isDebugEnabled()) {
                final String errorDetails = failedSources.values().stream()
                        .map(error -> error.toString() + " with cause: " + error.getCause())
                        .collect(Collectors.joining(","));
                log.debug("Detected failures in consumer: {}", errorDetails);
            }
            throw buildConnectionFailedException(failedSources);
        }
        return consumers;
    }

    @Nullable
    private ConsumerData createJmsConsumer(final Session session,
            final Map<String, Exception> failedSources,
            final Source source,
            final String sourceAddress,
            final String addressWithIndex) {

        log.debug("Creating AMQP Consumer for <{}>", addressWithIndex);
        try {
            final Destination destination = new JmsQueue(sourceAddress);
            final MessageConsumer messageConsumer = session.createConsumer(destination);
            return ConsumerData.of(source, sourceAddress, addressWithIndex, messageConsumer);
        } catch (final JMSException jmsException) {
            failedSources.put(addressWithIndex, jmsException);
            return null;
        }
    }


    /**
     * @return The JmsConnection
     */
    @Nullable
    private JmsConnection createJmsConnection(final String clientId) {
        return safelyExecuteJmsOperation(null, "create JMS connection", () -> {
            final JmsConnection jmsConnection =
                    jmsConnectionFactory.createConnection(connection, exceptionListener, connectionLogger, clientId);
            if (log.isDebugEnabled()) {
                log.debug("Attempt to create connection {} for URI [{}]", connection.getId(),
                        jmsConnection.getConfiguredURI());
            }
            return jmsConnection;
        });
    }

    private ConnectionFailedException buildConnectionFailedException(final Map<String, Exception> failedSources) {
        return ConnectionFailedException
                .newBuilder(connection.getId())
                .message("Failed to consume sources: " + failedSources.keySet())
                .description(() -> failedSources.entrySet()
                        .stream()
                        .map(e -> e.getKey() + ": " + e.getValue().getMessage())
                        .collect(Collectors.joining(", ")))
                .cause(failedSources.values().stream().findAny().orElse(null))
                .build();
    }

    private void terminateConnection(@Nullable final javax.jms.Connection jmsConnection) {
        if (jmsConnection != null) {
            try {
                jmsConnection.stop();
            } catch (final JMSException e) {
                log.debug("Stopping connection <{}> failed, probably it was already stopped: {}", connection.getId(),
                        e.getMessage());
            }
            try {
                jmsConnection.close();
            } catch (final JMSException e) {
                log.debug("Closing connection <{}> failed, probably it was already closed: {}", connection.getId(),
                        e.getMessage());
            }
        }
    }

    private void disconnectAndTell(final javax.jms.Connection connection, @Nullable final ActorRef origin,
            final boolean shutdownAfterDisconnect) {
        log.debug("Closing JMS connection {}", this.connection.getId());
        terminateConnection(connection);
        log.info("Connection <{}> closed.", this.connection.getId());

        getSender().tell(ClientDisconnected.of(origin, shutdownAfterDisconnect), getSelf());
    }


    /**
     * Supplier that may throw a {@link JMSException} or {@link NamingException}.
     *
     * @param <T> Type of supplied values.
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {

        /**
         * Try to obtain a value.
         *
         * @return the value.
         * @throws JMSException if the supplier throws a {@link JMSException}.
         * @throws NamingException if the identifier of connection could not be found in the Context.
         */
        @Nullable
        T get() throws JMSException, NamingException;

    }

}
