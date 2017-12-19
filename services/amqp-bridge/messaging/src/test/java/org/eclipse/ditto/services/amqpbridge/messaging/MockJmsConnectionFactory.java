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
package org.eclipse.ditto.services.amqpbridge.messaging;

import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.NamingException;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;

/**
 * Mock for a {@link javax.jms.JMSConnectionFactory} which abstracts away a real connection.
 */
final class MockJmsConnectionFactory implements JmsConnectionFactory {

    @Override
    public Connection createConnection(final AmqpConnection amqpConnection, final ExceptionListener exceptionListener)
            throws NamingException {
        return new MockJmsConnection(amqpConnection);
    }

    private static final class MockJmsConnection implements Connection {

        private final AmqpConnection amqpConnection;

        private MockJmsConnection(final AmqpConnection amqpConnection) {
            this.amqpConnection = amqpConnection;
        }


        @Override
        public Session createSession(final boolean transacted, final int acknowledgeMode) throws JMSException {
            return null;
        }

        @Override
        public Session createSession(final int sessionMode) throws JMSException {
            return null;
        }

        @Override
        public Session createSession() throws JMSException {
            return null;
        }

        @Override
        public String getClientID() throws JMSException {
            return null;
        }

        @Override
        public void setClientID(final String clientID) throws JMSException {

        }

        @Override
        public ConnectionMetaData getMetaData() throws JMSException {
            return null;
        }

        @Override
        public ExceptionListener getExceptionListener() throws JMSException {
            return null;
        }

        @Override
        public void setExceptionListener(final ExceptionListener listener) throws JMSException {

        }

        @Override
        public void start() throws JMSException {

        }

        @Override
        public void stop() throws JMSException {

        }

        @Override
        public void close() throws JMSException {

        }

        @Override
        public ConnectionConsumer createConnectionConsumer(final Destination destination, final String messageSelector,
                final ServerSessionPool sessionPool, final int maxMessages) throws JMSException {
            return null;
        }

        @Override
        public ConnectionConsumer createSharedConnectionConsumer(final Topic topic, final String subscriptionName,
                final String messageSelector, final ServerSessionPool sessionPool, final int maxMessages)
                throws JMSException {
            return null;
        }

        @Override
        public ConnectionConsumer createDurableConnectionConsumer(final Topic topic, final String subscriptionName,
                final String messageSelector, final ServerSessionPool sessionPool, final int maxMessages)
                throws JMSException {
            return null;
        }

        @Override
        public ConnectionConsumer createSharedDurableConnectionConsumer(final Topic topic,
                final String subscriptionName,
                final String messageSelector, final ServerSessionPool sessionPool, final int maxMessages)
                throws JMSException {
            return null;
        }
    }
}
