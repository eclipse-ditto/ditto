/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Hashtable;
import java.util.Map;

import javax.annotation.concurrent.NotThreadSafe;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.qpid.jms.JmsConnection;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating a {@link javax.jms.Connection} based on a {@link Connection}.
 */
@NotThreadSafe
public final class ConnectionBasedJmsConnectionFactory implements JmsConnectionFactory {

    private static final org.slf4j.Logger LOGGER =
            LoggerFactory.getLogger(ConnectionBasedJmsConnectionFactory.class);

    private static final String SECURE_AMQP_SCHEME = "amqps";

    private final Map<String, String> defaultConfig;

    private ConnectionBasedJmsConnectionFactory(final Map<String, String> defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    /**
     * Returns an instance of {@code ConnectionBasedJmsConnectionFactory}.
     *
     * @param defaultConfig the default AMQP config.
     * @return the instance.
     */
    public static ConnectionBasedJmsConnectionFactory getInstance(final Map<String, String> defaultConfig) {
        return new ConnectionBasedJmsConnectionFactory(defaultConfig);
    }

    @Override
    public JmsConnection createConnection(final Connection connection, final ExceptionListener exceptionListener,
            final ConnectionLogger connectionLogger, final String clientId) throws JMSException, NamingException {

        checkNotNull(connection, "Connection");
        checkNotNull(exceptionListener, "Exception Listener");

        final Context ctx = createContext(connection, clientId);
        final org.apache.qpid.jms.JmsConnectionFactory cf =
                (org.apache.qpid.jms.JmsConnectionFactory) ctx.lookup(connection.getId().toString());

        if (isSecuredConnection(connection) && connection.isValidateCertificates()) {
            cf.setSslContext(SSLContextCreator.fromConnection(connection, null, connectionLogger)
                    .withoutClientCertificate());
        }

        @SuppressWarnings("squid:S2095") final JmsConnection jmsConnection = (JmsConnection) cf.createConnection();
        jmsConnection.setExceptionListener(exceptionListener);
        return jmsConnection;
    }

    private Context createContext(final Connection connection, final String clientId) throws NamingException {
        final String connectionUri = buildAmqpConnectionUriFromConnection(connection, defaultConfig, clientId);
        @SuppressWarnings("squid:S1149") final Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        env.put("connectionfactory." + connection.getId(), connectionUri);

        return new InitialContext(env);
    }

    public static String buildAmqpConnectionUriFromConnection(final Connection connection,
            final Map<String, String> defaultConfig) {
        return buildAmqpConnectionUriFromConnection(connection, defaultConfig, connection.getId().toString());
    }

    public static String buildAmqpConnectionUriFromConnection(final Connection connection,
            final Map<String, String> defaultConfig, final String clientId) {
        final String protocol = connection.getProtocol();
        final String hostname = connection.getHostname();
        final int port = connection.getPort();
        final String baseUri = formatUri(protocol, hostname, port);
        final var amqpSpecificConfig = AmqpSpecificConfig.withDefault(clientId, connection, defaultConfig);
        final var connectionUri = amqpSpecificConfig.render(baseUri);
        LOGGER.debug("[{}] URI: {}", clientId, connectionUri);
        return connectionUri;
    }

    static boolean isSecuredConnection(final Connection connection) {
        return SECURE_AMQP_SCHEME.equalsIgnoreCase(connection.getProtocol());
    }

    private static String formatUri(final String protocol, final String hostname, final int port) {
        final String pattern = "{0}://{1}:{2}";
        return MessageFormat.format(pattern, protocol, hostname, Integer.toString(port));
    }

}
