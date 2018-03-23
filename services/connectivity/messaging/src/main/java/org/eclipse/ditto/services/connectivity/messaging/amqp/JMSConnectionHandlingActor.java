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
import java.util.Map;
import java.util.stream.Collectors;

import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsQueue;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;

/**
 * This actor executes single operation (connect/disconnect) on JMS Connection/Session. It is separated into an actor
 * because the JMS Client is blocking which makes it impossible to e.g. cancel a pending connection attempts with
 * another actor message when done in the same actor.
 */
public class JMSConnectionHandlingActor extends AbstractActor {

    /**
     * The Actor name prefix.
     */
    static final String ACTOR_NAME_PREFIX = "jmsConnectionHandling-";

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

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AmqpClientActor.JmsConnect.class, this::handleConnect)
                .match(AmqpClientActor.JmsDisconnect.class, this::handleDisconnect)
                .build();
    }

    @SuppressWarnings("squid:S2095") // cannot use try-with-resources, connection has longer lifetime
    private void handleConnect(final AmqpClientActor.JmsConnect connect) {
        try {
            final JmsConnection
                    jmsConnection = jmsConnectionFactory.createConnection(connection, exceptionListener);
            log.debug("Starting connection.");
            jmsConnection.start();
            log.debug("Connection started successfully, creating session.");
            final Session jmsSession = jmsConnection.createSession(Session.CLIENT_ACKNOWLEDGE);
            log.debug("Session created.");

            final Map<String, MessageConsumer> consumerMap = new HashMap<>();
            final Map<String, Exception> failedSources = new HashMap<>();
            connection.getSources().forEach(source ->
                    source.getAddresses().forEach(sourceAddress -> {
                        // TODO TJ what about consumerCount? should be one consumer for each count..
                        final int consumerCount = source.getConsumerCount();
                        log.debug("Creating AMQP Consumer for '{}'", sourceAddress);
                        final Destination destination = new JmsQueue(sourceAddress);
                        final MessageConsumer messageConsumer;
                        try {
                            messageConsumer = jmsSession.createConsumer(destination);
                            consumerMap.put(sourceAddress, messageConsumer);
                        } catch (final JMSException jmsException) {
                            failedSources.put(sourceAddress, jmsException);
                        }
                    }));

            if (failedSources.isEmpty()) {
                final AmqpClientActor.JmsConnected connectedMessage =
                        new AmqpClientActor.JmsConnected(connect.getOrigin().orElse(null), jmsConnection, jmsSession,
                                consumerMap);
                sender().tell(connectedMessage, sender());
                log.debug("Connection <{}> established successfully, stopping myself.", connection.getId());
            } else {
                log.warning("Failed to consume sources: {}.", failedSources);
                final ConnectionFailedException failedException = ConnectionFailedException
                        .newBuilder(connection.getId())
                        .message("Failed to consume sources: " + failedSources.keySet())
                        .description(() -> failedSources.entrySet()
                                .stream()
                                .map(e -> e.getKey() + ": " + e.getValue().getMessage())
                                .collect(Collectors.joining(", ")))
                        .build();
                getSender().tell(
                        new ImmutableConnectionFailure(connect.getOrigin().orElse(null), failedException, null),
                        sender());
            }
        } catch (final Exception e) {
            getSender().tell(new ImmutableConnectionFailure(connect.getOrigin().orElse(null), e, null), getSender());
        }
        getContext().stop(getSelf());
    }

    private void handleDisconnect(final AmqpClientActor.JmsDisconnect disconnect) {
        final javax.jms.Connection connection = disconnect.getConnection();
        try {
            log.debug("Closing JMS connection {}", this.connection.getId());
            connection.stop();
            connection.close();
            log.info("Connection '{}' closed.", this.connection.getId());
        } catch (final JMSException e) {
            log.debug("Connection '{}' already closed: {}", this.connection.getId(), e.getMessage());
        }
        getSender().tell(new AmqpClientActor.JmsDisconnected(disconnect.getOrigin().orElse(null)), getSender());
        log.info("Stop myself {}", getSelf());
        getContext().stop(getSelf());
    }

}
