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
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.naming.NamingException;

import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsQueue;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.MessageDispatcher;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;

/**
 * This actor executes operations (connect/disconnect) on JMS Connection/Session. It is separated into an actor
 * because the JMS Client is blocking which makes it impossible to e.g. cancel a pending connection attempts with
 * another actor message when done in the same actor.
 * <p>
 * WARNING: This actor blocks! Start with its own dispatcher!
 * </p>
 */
public class JMSConnectionHandlingActor extends AbstractActor {

    /**
     * The Actor name prefix.
     */
    static final String ACTOR_NAME_PREFIX = "jmsConnectionHandling-";

    /**
     * Config key of the dispatcher for this actor.
     */
    public static final String DISPATCHER_NAME = "jms-connection-handling-dispatcher";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final Connection connection;
    private final ExceptionListener exceptionListener;
    private final JmsConnectionFactory jmsConnectionFactory;

    private JMSConnectionHandlingActor(final Connection connection, final ExceptionListener exceptionListener,
            final JmsConnectionFactory jmsConnectionFactory) {

        this.connection = checkNotNull(connection, "connection");
        this.exceptionListener = exceptionListener;
        this.jmsConnectionFactory = jmsConnectionFactory;
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code JMSConnectionHandlingActor}.
     *
     * @param connection the connection
     * @param exceptionListener the exception listener
     * @param jmsConnectionFactory the jms connection factory
     * @return the Akka configuration Props object.
     */
    static Props props(final Connection connection, final ExceptionListener exceptionListener,
            final JmsConnectionFactory jmsConnectionFactory) {

        return Props.create(JMSConnectionHandlingActor.class, new Creator<JMSConnectionHandlingActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public JMSConnectionHandlingActor create() {
                return new JMSConnectionHandlingActor(connection, exceptionListener, jmsConnectionFactory);
            }
        });
    }

    static Props propsWithOwnDispatcher(final Connection connection, final ExceptionListener exceptionListener,
            final JmsConnectionFactory jmsConnectionFactory) {
        return props(connection, exceptionListener, jmsConnectionFactory)
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
                .match(AmqpClientActor.JmsDisconnect.class, this::handleDisconnect)
                .build();
    }

    private void handleConnect(final AmqpClientActor.JmsConnect connect) {
        maybeConnectAndTell(getSender(), connect.getOrigin().orElse(null));
    }

    private void handleDisconnect(final AmqpClientActor.JmsDisconnect disconnect) {
        final Optional<javax.jms.Connection> connectionOpt = disconnect.getConnection();
        if (connectionOpt.isPresent()) {
            disconnectAndTell(connectionOpt.get(), disconnect.getOrigin().orElse(null));
        } else {
            final Object answer = new AmqpClientActor.JmsDisconnected(disconnect.getOrigin().orElse(null));
            getSender().tell(answer, getSelf());
        }
    }

    /*
     * This method should be thread-safe.
     */
    private void maybeConnectAndTell(final ActorRef sender, @Nullable final ActorRef origin) {
        final ActorRef self = getSelf(); // getSelf() is thread-safe
        try {
            final JmsConnection jmsConnection = createJmsConnection();
            final AmqpClientActor.JmsConnected connectedMessage = tryConnect(jmsConnection, origin);
            sender.tell(connectedMessage, self);
            log.debug("Connection <{}> established successfully.", connection.getId());
        } catch (final ConnectionFailedException e) {
            sender.tell(new ImmutableConnectionFailure(origin, e, e.getMessage()), self);
            log.warning(e.getMessage());
        } catch (final Exception e) {
            sender.tell(new ImmutableConnectionFailure(origin, e, e.getMessage()), self);
            log.error("Unexpected error: {}", e.getMessage());
        }
    }

    private AmqpClientActor.JmsConnected tryConnect(final JmsConnection jmsConnection,
            @Nullable final ActorRef origin) {

        try {
            jmsConnection.start();
            log.debug("Connection started successfully");
        } catch (final JMSException e) {
            terminateConnection(jmsConnection); // probably unnecessary, just to be sure.
            throw ConnectionFailedException.newBuilder(connection.getId())
                    .message("Failed to connect JMS client:" + e.getMessage())
                    .cause(e)
                    .build();
        }

        try {
            @SuppressWarnings("squid:S2095") final Session jmsSession =
                    jmsConnection.createSession(Session.CLIENT_ACKNOWLEDGE);
            log.debug("Session created.");

            final List<ConsumerData> consumers = createConsumers(jmsSession);
            return new AmqpClientActor.JmsConnected(origin, jmsConnection, jmsSession, consumers);
        } catch (final JMSException e) {
            terminateConnection(jmsConnection);
            throw ConnectionFailedException.newBuilder(connection.getId())
                    .message("Failed to create session:" + e.getMessage())
                    .cause(e)
                    .build();
        } catch (final ConnectionFailedException e) {
            terminateConnection(jmsConnection);
            throw e;
        }
    }

    /**
     * Uses the given session to create the specified count of message consumers for every sources addresses.
     *
     * @param session the session
     * @return the consumers
     * @throws org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException if creation of one
     * or more consumers failed
     */
    private List<ConsumerData> createConsumers(final Session session) {
        final Map<String, Exception> failedSources = new HashMap<>();
        final List<ConsumerData> consumers = connection.getSources().stream().flatMap(source ->
                source.getAddresses().stream().flatMap(sourceAddress ->
                        IntStream.range(0, source.getConsumerCount())
                                .mapToObj(i -> sourceAddress + "-" + i)
                                .map(addressWithIndex -> createJmsConsumer(session, failedSources, source,
                                        sourceAddress, addressWithIndex))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()).stream()
                ).collect(Collectors.toList()).stream()
        ).collect(Collectors.toList());

        if (!failedSources.isEmpty()) {
            throw buildConnectionFailedException(failedSources);
        }
        return consumers;
    }

    @Nullable
    private ConsumerData createJmsConsumer(final Session session, final Map<String, Exception> failedSources,
            final Source source, final String sourceAddress, final String addressWithIndex) {
        log.debug("Creating AMQP Consumer for <{}>", addressWithIndex);
        final Destination destination = new JmsQueue(sourceAddress);
        final MessageConsumer messageConsumer;
        try {
            messageConsumer = session.createConsumer(destination);
            return new ConsumerData(source, sourceAddress, addressWithIndex, messageConsumer);
        } catch (final JMSException jmsException) {
            failedSources.put(addressWithIndex, jmsException);
            return null;
        }
    }


    /**
     * @return The JmsConnection
     */
    private JmsConnection createJmsConnection() {
        try {
            return jmsConnectionFactory.createConnection(connection, exceptionListener);
        } catch (final JMSException | NamingException e) {
            throw ConnectionFailedException.newBuilder(connection.getId())
                    .message("Failed to connect JMS client:" + e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private ConnectionFailedException buildConnectionFailedException(final Map<String, Exception> failedSources) {
        return ConnectionFailedException
                .newBuilder(connection.getId())
                .message("Failed to consume sources: " + failedSources.keySet())
                .description(() -> failedSources.entrySet()
                        .stream()
                        .map(e -> e.getKey() + ": " + e.getValue().getMessage())
                        .collect(Collectors.joining(", ")))
                .build();
    }

    private void terminateConnection(final javax.jms.Connection jmsConnection) {
        try {
            jmsConnection.stop();
        } catch (final JMSException e) {
            log.debug("Stopping connection <{}> failed, probably it was already stopped: {}",
                    this.connection.getId(), e.getMessage());
        }
        try {
            jmsConnection.close();
        } catch (final JMSException e) {
            log.debug("Closing connection <{}> failed, probably it was already closed: {}",
                    this.connection.getId(), e.getMessage());
        }
    }

    private void disconnectAndTell(final javax.jms.Connection connection, @Nullable final ActorRef origin) {
        log.debug("Closing JMS connection {}", this.connection.getId());
        terminateConnection(connection);
        log.info("Connection <{}> closed.", this.connection.getId());

        getSender().tell(new AmqpClientActor.JmsDisconnected(origin), getSelf());
    }

}
