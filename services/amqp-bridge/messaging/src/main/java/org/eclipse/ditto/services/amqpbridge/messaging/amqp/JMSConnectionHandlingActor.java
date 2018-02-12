/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.amqpbridge.messaging.amqp;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.naming.NamingException;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;

public class JMSConnectionHandlingActor extends AbstractActor {

    static final String ACTOR_NAME_PREFIX = "connectionHandling-";
    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final String connectionId;

    private JMSConnectionHandlingActor(final String connectionId) {
        this.connectionId = connectionId;
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code JMSConnectionHandlingActor}.
     *
     * @param connectionId the connection id.
     * @return the Akka configuration Props object.
     */
    static Props props(final String connectionId) {
        return Props.create(JMSConnectionHandlingActor.class, new Creator<JMSConnectionHandlingActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public JMSConnectionHandlingActor create() {
                return new JMSConnectionHandlingActor(connectionId);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AmqpConnectionActor.Create.class, this::createConnection)
                .match(AmqpConnectionActor.Connect.class, this::startConnection)
                .match(AmqpConnectionActor.Disconnect.class, this::stopConnection)
                .build();
    }

    private void createConnection(AmqpConnectionActor.Create createConnection) {
        final AmqpConnection amqpConnection = createConnection.getAmqpConnection();
        final ExceptionListener exceptionListener = createConnection.getExceptionListener();
        final JmsConnectionFactory jmsConnectionFactory = createConnection.getJmsConnectionFactory();
        try {
            log.debug("Creating jms connection for <{}>.", connectionId);
            final Connection connection = jmsConnectionFactory.createConnection(amqpConnection, exceptionListener);
            getSender().tell(connection, self());
        } catch (JMSException | NamingException e) {
            log.warning("Failed to create JMS connection: {}", e.getMessage());
            getSender().tell(new Status.Failure(e), self());
        }
        context().stop(self());
    }

    private void startConnection(AmqpConnectionActor.Connect connect) {
        try {
            final Connection jmsConnection = connect.getConnection();
            log.debug("Starting connection.");
            jmsConnection.start();
            log.debug("Connection started successfully, creating session.");
            final Session jmsSession = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            log.debug("Session created.");
            sender().tell(new AmqpConnectionActor.Connected(connect.getOrigin(), jmsSession), sender());
            log.debug("Connection <{}> established successfully, stopping myself.", connectionId);
        } catch (Exception e) {
            sender().tell(new AmqpConnectionActor.Failure(connect.getOrigin(), e), sender());
        }
        context().stop(self());
    }

    private void stopConnection(AmqpConnectionActor.Disconnect disconnect) {
        try {
            final Connection connection = disconnect.getConnection();
            if (connection != null) {
                try {
                    log.debug("Closing JMS connection {}", connectionId);
                    connection.stop();
                    connection.close();
                    log.info("Connection '{}' closed.", connectionId);
                } catch (final JMSException e) {
                    log.debug("Connection '{}' already closed: {}", connectionId, e.getMessage());
                }
            }
            sender().tell(new AmqpConnectionActor.Disconnected(disconnect.getOrigin()), sender());
        } catch (Exception e) {
            sender().tell(new AmqpConnectionActor.Failure(disconnect.getOrigin(), e), sender());
        }
        context().stop(self());
    }
}
