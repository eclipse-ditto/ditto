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
package org.eclipse.ditto.services.amqpbridge.messaging.amqp;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.qpid.jms.JmsQueue;
import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.services.utils.akka.LogUtil;

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

    private final AmqpConnection amqpConnection;
    private final ExceptionListener exceptionListener;
    private final JmsConnectionFactory jmsConnectionFactory;


    private JMSConnectionHandlingActor(final AmqpConnection amqpConnection, final ExceptionListener exceptionListener,
            final JmsConnectionFactory jmsConnectionFactory) {

        this.amqpConnection = checkNotNull(amqpConnection, "amqpConnection");
        this.exceptionListener = exceptionListener;
        this.jmsConnectionFactory = jmsConnectionFactory;
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code JMSConnectionHandlingActor}.
     *
     * @param amqpConnection the amqp connection
     * @param exceptionListener the exception listener
     * @param jmsConnectionFactory the jms connection factory
     * @return the Akka configuration Props object.
     */
    static Props props(final AmqpConnection amqpConnection, final ExceptionListener exceptionListener,
            final JmsConnectionFactory jmsConnectionFactory) {

        return Props.create(JMSConnectionHandlingActor.class, new Creator<JMSConnectionHandlingActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public JMSConnectionHandlingActor create() {
                return new JMSConnectionHandlingActor(amqpConnection, exceptionListener, jmsConnectionFactory);
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
            final Connection jmsConnection = jmsConnectionFactory.createConnection(amqpConnection, exceptionListener);
            log.debug("Starting connection.");
            jmsConnection.start();
            log.debug("Connection started successfully, creating session.");
            final Session jmsSession = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            log.debug("Session created.");

            final Map<String, MessageConsumer> consumerMap = new HashMap<>();
            if (amqpConnection.getSources().isPresent()) {
                final Set<String> sources = amqpConnection.getSources().get();
                for (final String source : sources) {
                    log.debug("Creating AMQP Consumer for '{}'", source);
                    final Destination destination = new JmsQueue(source);
                    final MessageConsumer messageConsumer = jmsSession.createConsumer(destination);
                    consumerMap.put(source, messageConsumer);
                }
            }

            sender().tell(new AmqpClientActor.JmsConnected(connect.getOrigin(), jmsConnection, jmsSession, consumerMap),
                    sender());
            log.debug("Connection <{}> established successfully, stopping myself.", amqpConnection.getId());
        } catch (final Exception e) {
            sender().tell(new AmqpClientActor.JmsFailure(connect.getOrigin(), e), sender());
        }
        context().stop(self());
    }

    private void handleDisconnect(final AmqpClientActor.JmsDisconnect disconnect) {
        final Connection connection = disconnect.getConnection();
        try {
            log.debug("Closing JMS connection {}", amqpConnection.getId());
            connection.stop();
            connection.close();
            log.info("Connection '{}' closed.", amqpConnection.getId());
        } catch (final JMSException e) {
            log.debug("Connection '{}' already closed: {}", amqpConnection.getId(), e.getMessage());
        }
        sender().tell(new AmqpClientActor.JmsDisconnected(disconnect.getOrigin()), sender());
        log.info("Stop myself {}", self());
        context().stop(self());
    }
}
