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
import javax.jms.JMSRuntimeException;
import javax.jms.Session;
import javax.naming.NamingException;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.services.amqpbridge.messaging.ConnectionActor;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.OpenConnection;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;

/**
 * Actor which manages a connection to AMQP 1.0 server.
 */
public class AmqpConnectionActor extends ConnectionActor implements ExceptionListener {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private AmqpConnection amqpConnection;
    private final JmsConnectionFactory jmsConnectionFactory;

    private Connection jmsConnection;
    private Session jmsSession;

    private AmqpConnectionActor(final String connectionId, final ActorRef pubSubMediator,
            final String pubSubTargetActorPath) {
        super(connectionId, pubSubMediator, pubSubTargetActorPath);
        this.jmsConnectionFactory = AmqpConnectionBasedJmsConnectionFactory.getInstance();
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @return the Akka configuration Props object
     */
    public static Props props(final String connectionId, final ActorRef pubSubMediator,
            final String pubSubTargetActorPath) {
        return Props.create(AmqpConnectionActor.class, new Creator<AmqpConnectionActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public AmqpConnectionActor create() {
                return new AmqpConnectionActor(connectionId, pubSubMediator, pubSubTargetActorPath);
            }
        });
    }

    @Override
    protected void doCreateConnection(final CreateConnection createConnection) {
        amqpConnection = createConnection.getAmqpConnection();

        try {
            startConnection();
            startCommandConsumers();
            log.info("Connection '{}' created.", amqpConnection.getId());
        } catch (final JMSRuntimeException | JMSException | NamingException e) {
            log.error(e, "Failed to create Connection '{}' with Error: '{}'.", amqpConnection.getId(), e.getMessage());
        }

    }

    @Override
    protected void doOpenConnection(final OpenConnection openConnection) {
        try {
            startConnection();
            startCommandConsumers();
        } catch (NamingException | JMSException e) {
            // TODO error handling
            e.printStackTrace();
        }
    }

    @Override
    protected void doCloseConnection(final CloseConnection closeConnection) {
        stopCommandConsumers();
    }

    @Override
    protected void doDeleteConnection(final DeleteConnection deleteConnection) {
        log.info("Deleting {}", deleteConnection.getConnectionId());
        stopCommandConsumers();
    }

    @Override
    protected void doUpdateConnection(final AmqpConnection amqpConnection) {
        this.amqpConnection = amqpConnection;
    }

    private void startCommandConsumers() {
        for (final String source : amqpConnection.getSources()) {
            startCommandConsumer(source);
        }
        log.info("Subscribed Connection '{}' to sources: {}", amqpConnection.getId(), amqpConnection.getSources());
    }

    private void stopCommandConsumers() {
        if (amqpConnection != null) {
            for (final String source : amqpConnection.getSources()) {
                stopChildActor(source);
            }

            log.info("Unsubscribed Connection '{}' from sources: {}", amqpConnection.getId(),
                    amqpConnection.getSources());
            stopConnection();
        }
    }

    private void startCommandConsumer(final String source) {
        final String name = CommandConsumerActor.ACTOR_NAME_PREFIX + source;
        if (!getContext().findChild(name).isPresent()) {
            final Props props = CommandConsumerActor.props(jmsSession, source, commandProcessor);
            startChildActor(name, props);
        } else {
            log.debug("Child actor {} already exists.", name);
        }
    }

    private void startConnection() throws JMSException, NamingException {

        if (jmsConnection == null) {
            jmsConnection = jmsConnectionFactory.createConnection(amqpConnection, this);
        }

        jmsConnection.start();
        jmsSession = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        log.info("Connection '{}' opened.", amqpConnection.getId());
    }

    private void stopConnection() {
        if (jmsSession != null) {
            try {
                jmsSession.close();
                jmsSession = null;
            } catch (final JMSException e) {
                log.debug("Session of connection '{}' already closed: {}", amqpConnection.getId(), e.getMessage());
            }
        }
        if (jmsConnection != null) {
            try {
                jmsConnection.stop();
                jmsConnection.close();
                jmsConnection = null;
                log.info("Connection '{}' closed.", amqpConnection.getId());
            } catch (final JMSException e) {
                log.debug("Connection '{}' already closed: {}", amqpConnection.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void onException(final JMSException exception) {
        log.error("{} occurred: {}", exception.getClass().getName(), exception.getMessage());
    }
}