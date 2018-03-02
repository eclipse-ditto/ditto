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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

        final String baseUri = formatUri(protocol, hostname, port);

        final List<String> parameters = new ArrayList<>(getAmqpParameters());
        if (!amqpConnection.isValidateCertificates()) {
            parameters.addAll(getTransportParameters());
        }
        final String nestedUri = baseUri + parameters.stream().collect(Collectors.joining("&", "?", ""));

        final List<String> globalParameters = new ArrayList<>(getJmsParameters(id, username, password));
        if (failoverEnabled) {
            globalParameters.addAll(getFailoverParameters());
        }
        final String connectionUri = wrapWithFailOver(nestedUri, failoverEnabled)
                + globalParameters.stream().collect(Collectors.joining("&", "?", ""));

        LOGGER.debug("[{}] URI: {}", id, connectionUri);
        @SuppressWarnings("squid:S1149") final Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        env.put("connectionfactory." + amqpConnection.getId(), connectionUri);

        return new InitialContext(env);
    }

    private static String formatUri(final String protocol, final String hostname, final int port) {
        final String pattern = "{0}://{1}:{2}";
        return MessageFormat.format(pattern, protocol, hostname, Integer.toString(port));
    }

    @SuppressWarnings("squid:S2068")
    private static List<String> getJmsParameters(final String id, final String username, final String password) {
        String encodedId;
        try {
            encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8.displayName());
        } catch (final UnsupportedEncodingException e) {
            LOGGER.info("Enconding not supported: {}", e.getMessage());
            //fallback: replace special characters
            encodedId = id.replaceAll("[^a-zA-Z0-9]+", "");
        }
        return Arrays.asList("jms.clientID=" + encodedId,
                "jms.username=" + username,
                "jms.password=" + password);
    }

    private static List<String> getAmqpParameters() {
        return Collections.singletonList("amqp.saslMechanisms=PLAIN");
    }

    private static List<String> getTransportParameters() {
        return Arrays.asList("transport.trustAll=true",
                "transport.verifyHost=false");
    }

    private static List<String> getFailoverParameters() {
        return Arrays.asList("initialReconnectDelay=10s",
                "failover.startupMaxReconnectAttempts=1", // important, we cannot interrupt connection initiation
                "reconnectDelay=1s",
                "maxReconnectDelay=1h",
                "failover.useReconnectBackOff=true",
                "reconnectBackOffMultiplier=1m");
    }

    private static String wrapWithFailOver(final String uri, final boolean failoverEnabled) {
        return failoverEnabled ? MessageFormat.format("failover:({0})", uri) : uri;
    }

}
