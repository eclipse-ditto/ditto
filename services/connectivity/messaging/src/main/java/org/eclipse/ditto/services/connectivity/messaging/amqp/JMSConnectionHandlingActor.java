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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
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
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import scala.concurrent.duration.FiniteDuration;

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
                .match(AmqpClientActor.JmsReconnect.class, this::handleReconnect)
                .match(AmqpClientActor.JmsDisconnect.class, this::handleDisconnect)
                .build();
    }

    private void handleConnect(final AmqpClientActor.JmsConnect connect) {
        doConnect(getSender(), connect.getOrigin().orElse(null));
        getContext().stop(getSelf());
    }

    private void handleReconnect(final AmqpClientActor.JmsReconnect reconnect) {
        final javax.jms.Connection connection = reconnect.getConnection();
        log.info("Reconnecting");
        doDisconnect(connection, null); // do not pass origin as only the disconnect would be acked

        // wait a little until connecting again:
        final ActorRef sender = getSender();
        getContext().getSystem().scheduler().scheduleOnce(FiniteDuration.apply(500, TimeUnit.MILLISECONDS),
                () -> doConnect(sender, reconnect.getOrigin().orElse(null)),
                getContext().getSystem().dispatcher());
    }

    private void handleDisconnect(final AmqpClientActor.JmsDisconnect disconnect) {
        final Optional<javax.jms.Connection> connectionOpt = disconnect.getConnection();
        if (connectionOpt.isPresent()) {
            doDisconnect(connectionOpt.get(), disconnect.getOrigin().orElse(null));
        } else {
            getSender().tell(new AmqpClientActor.JmsDisconnected(disconnect.getOrigin().orElse(null)),
                    disconnect.getOrigin().orElse(null));
        }
        log.debug("Stopping myself {}", getSelf());
        getContext().stop(getSelf());
    }

    private void doConnect(final ActorRef sender, @Nullable final ActorRef origin) {
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
                        for (int i = 0; i < source.getConsumerCount(); i++) {
                            final String addressWithIndex = sourceAddress + "-" + i;
                            log.debug("Creating AMQP Consumer for <{}>", addressWithIndex);
                            final Destination destination = new JmsQueue(sourceAddress);
                            final MessageConsumer messageConsumer;
                            try {
                                messageConsumer = jmsSession.createConsumer(destination);
                                consumerMap.put(addressWithIndex, messageConsumer);
                            } catch (final JMSException jmsException) {
                                failedSources.put(addressWithIndex, jmsException);
                            }
                        }
                    }));

            if (failedSources.isEmpty()) {
                final AmqpClientActor.JmsConnected connectedMessage =
                        new AmqpClientActor.JmsConnected(origin, jmsConnection, jmsSession, consumerMap);
                sender.tell(connectedMessage, origin);
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
                sender.tell(
                        new ImmutableConnectionFailure(origin, failedException, null), getSelf());
            }
        } catch (final Exception e) {
            sender.tell(new ImmutableConnectionFailure(origin, e, null), getSelf());
        }
    }

    private void doDisconnect(final javax.jms.Connection connection, @Nullable final ActorRef origin) {
        try {
            log.debug("Closing JMS connection {}", this.connection.getId());
            connection.stop();
            connection.close();
            log.info("Connection <{}> closed.", this.connection.getId());
        } catch (final JMSException e) {
            log.debug("Connection <{}> already closed: {}", this.connection.getId(), e.getMessage());
        }
        getSender().tell(new AmqpClientActor.JmsDisconnected(origin), origin);
    }

}
