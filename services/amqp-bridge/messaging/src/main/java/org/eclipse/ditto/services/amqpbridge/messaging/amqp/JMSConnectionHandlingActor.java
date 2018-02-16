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

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;

/**
 * The type Jms connection handling actor.
 */
public class JMSConnectionHandlingActor extends AbstractActor {

    /**
     * The Actor name prefix.
     */
    static final String ACTOR_NAME_PREFIX = "jmsConnectionHandling-";
    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final AmqpConnection amqpConnection;
    private final ExceptionListener exceptionListener;
    private JmsConnectionFactory jmsConnectionFactory;


    private JMSConnectionHandlingActor(
            final AmqpConnection amqpConnection, final ExceptionListener exceptionListener,
            final JmsConnectionFactory jmsConnectionFactory) {
        this.amqpConnection = amqpConnection;
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

    private void handleConnect(AmqpClientActor.JmsConnect connect) {
        try {
            final Connection jmsConnection = jmsConnectionFactory.createConnection(amqpConnection, exceptionListener);
            log.debug("Starting connection.");
            jmsConnection.start();
            log.debug("Connection started successfully, creating session.");
            final Session jmsSession = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            log.debug("Session created.");
            sender().tell(new AmqpClientActor.JmsConnected(connect.getOrigin(), jmsConnection, jmsSession), sender());
            log.debug("Connection <{}> established successfully, stopping myself.", amqpConnection.getId());
        } catch (Exception e) {
            sender().tell(new AmqpClientActor.JmsFailure(connect.getOrigin(), e), sender());
        }
        context().stop(self());
    }

    private void handleDisconnect(AmqpClientActor.JmsDisconnect disconnect) {
        try {
            final Connection connection = disconnect.getConnection();
            if (connection != null) {
                try {
                    log.debug("Closing JMS connection {}", amqpConnection.getId());
                    connection.stop();
                    connection.close();
                    log.info("Connection '{}' closed.", amqpConnection.getId());
                } catch (final JMSException e) {
                    log.debug("Connection '{}' already closed: {}", amqpConnection.getId(), e.getMessage());
                }
            }
            sender().tell(new AmqpClientActor.JmsDisconnected(disconnect.getOrigin()), sender());
        } catch (Exception e) {
            sender().tell(new AmqpClientActor.JmsFailure(disconnect.getOrigin(), e), sender());
        }
        log.info("Stop myself {}", self());
        context().stop(self());
    }
}
