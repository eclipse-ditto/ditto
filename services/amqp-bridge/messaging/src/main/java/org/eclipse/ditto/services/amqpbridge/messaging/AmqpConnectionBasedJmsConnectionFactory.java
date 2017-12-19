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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Hashtable;

import javax.annotation.concurrent.NotThreadSafe;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;

/**
 * Factory for creating a {@link javax.jms.Connection} based on a {@link AmqpConnection}.
 */
@NotThreadSafe
public final class AmqpConnectionBasedJmsConnectionFactory implements JmsConnectionFactory {

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
        connection.setClientID(amqpConnection.getId());
        return connection;
    }

    private Context createContext(final AmqpConnection amqpConnection) throws NamingException {
        final String username = amqpConnection.getUsername();
        final String password = amqpConnection.getPassword();
        final String protocol = amqpConnection.getProtocol();
        final String hostname = amqpConnection.getHostname();
        final int port = amqpConnection.getPort();

        final String uri = formatUri(protocol, hostname, port);
        final String uriWithFailoverEnabled = amqpConnection.isFailoverEnabled() ? wrapWithFailOver(uri) : uri;
        final String connectionUri = appendParameters("amqps".equals(protocol), uriWithFailoverEnabled, username, password);

        @SuppressWarnings("squid:S1149") final Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        env.put("connectionfactory." + amqpConnection.getId(), connectionUri);

        return new InitialContext(env);
    }

    private static String formatUri(final String protocol, final String hostname, final int port) {
        final String pattern = "{0}://{1}:{2}";
        return MessageFormat.format(pattern, protocol, hostname, Integer.toString(port));
    }

    private static String appendParameters(final boolean useSsl, final String uri, final String username, final String password) {

        @SuppressWarnings("squid:S2068") final String pattern;
        if (useSsl) {
            pattern = "{0}?jms.username={1}&jms.password={2}" +
                    "&transport.trustAll=true&transport.verifyHost=false&amqp.saslMechanisms=PLAIN";
        } else {
            pattern = "{0}?jms.username={1}&jms.password={2}&amqp.saslMechanisms=PLAIN";
        }
        return MessageFormat.format(pattern, uri, username, password);
    }

    private static String wrapWithFailOver(final String uri) {
        return MessageFormat.format("failover:({0})", uri);
    }

}
