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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating a {@link javax.jms.Connection} based on a {@link AmqpConnection}.
 */
@NotThreadSafe
public final class AmqpConnectionBasedJmsConnectionFactory implements JmsConnectionFactory {

    private static final org.slf4j.Logger LOGGER =
            LoggerFactory.getLogger(AmqpConnectionBasedJmsConnectionFactory.class);

    private AmqpConnectionBasedJmsConnectionFactory() {
        // no-op
    }

    /**
     * Returns an instance of {@code AmqpConnectionBasedJmsConnectionFactory}.
     *
     * @return the instance.
     */
    public static AmqpConnectionBasedJmsConnectionFactory getInstance() {
        return new AmqpConnectionBasedJmsConnectionFactory();
    }

    @Override
    public Connection createConnection(final AmqpConnection amqpConnection, final ExceptionListener exceptionListener)
            throws JMSException, NamingException {
        checkNotNull(amqpConnection, "Connection");
        checkNotNull(exceptionListener, "Exception Listener");

        final Context ctx = createContext(amqpConnection);
        final ConnectionFactory cf = (javax.jms.ConnectionFactory) ctx.lookup(amqpConnection.getId());

        @SuppressWarnings("squid:S2095") final Connection connection = cf.createConnection();
        connection.setExceptionListener(exceptionListener);
        return connection;
    }

    private Context createContext(final AmqpConnection amqpConnection) throws NamingException {
        final String id = amqpConnection.getId();
        final String username = amqpConnection.getUsername();
        final String password = amqpConnection.getPassword();
        final String protocol = amqpConnection.getProtocol();
        final String hostname = amqpConnection.getHostname();
        final int port = amqpConnection.getPort();
        final boolean failoverEnabled = amqpConnection.isFailoverEnabled();

        final String baseUri = formatUri(protocol, hostname, port, failoverEnabled);

        final List<String> uriParams = new ArrayList<>();
        if (!amqpConnection.isValidateCertificates()) {
            uriParams.add(getTransportParameters());
        }
        uriParams.add(getJmsParameters(id, username, password));
        uriParams.add(getFailoverParameters());
        uriParams.add(getAmqpParameters(failoverEnabled));

        final String connectionUri = baseUri + uriParams.stream().collect(Collectors.joining("&", "?", ""));

        LOGGER.debug("[{}] URI: {}", id, connectionUri);

        @SuppressWarnings("squid:S1149") final Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        env.put("connectionfactory." + amqpConnection.getId(), connectionUri);

        return new InitialContext(env);
    }

    private static String formatUri(final String protocol, final String hostname, final int port,
            final boolean failoverEnabled) {
        final String pattern = "{0}://{1}:{2}";
        final String uri = MessageFormat.format(pattern, protocol, hostname, Integer.toString(port));
        return failoverEnabled ? wrapWithFailOver(uri) : uri;
    }

    @SuppressWarnings("squid:S2068")
    private static String getJmsParameters(final String id, final String username, final String password) {
        return "jms.clientID=" + id +
                "&jms.username=" + username +
                "&jms.password=" + password;
    }

    private static String getAmqpParameters(final boolean nested) {
        return (nested ? "failover.nested." : "") + "amqp.saslMechanisms=ANONYMOUS,PLAIN";
    }

    private static String getTransportParameters() {
        return "transport.trustAll=true" +
                "&transport.verifyHost=false";
    }

    private static String getFailoverParameters() {
        return "initialReconnectDelay=10s" +
                "&failover.startupMaxReconnectAttempts=1" + // important, we cannot interrupt connection initiation
                "&reconnectDelay=1s" +
                "&maxReconnectDelay=1h" +
                "&failover.useReconnectBackOff=true" +
                "&reconnectBackOffMultiplier=1m";
    }

    private static String wrapWithFailOver(final String uri) {
        return MessageFormat.format("failover:({0})", uri);
    }

}
