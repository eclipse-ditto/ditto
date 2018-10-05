/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import static org.apache.qpid.jms.provider.failover.FailoverProviderFactory.FAILOVER_OPTION_PREFIX;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.qpid.jms.JmsConnection;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.internal.SSLContextCreator;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating a {@link javax.jms.Connection} based on a {@link Connection}.
 */
@NotThreadSafe
public final class ConnectionBasedJmsConnectionFactory implements JmsConnectionFactory {

    private static final org.slf4j.Logger LOGGER =
            LoggerFactory.getLogger(ConnectionBasedJmsConnectionFactory.class);

    private static final String SECURE_AMQP_SCHEME = "amqps";

    private ConnectionBasedJmsConnectionFactory() {
        // no-op
    }

    /**
     * Returns an instance of {@code ConnectionBasedJmsConnectionFactory}.
     *
     * @return the instance.
     */
    public static ConnectionBasedJmsConnectionFactory getInstance() {
        return new ConnectionBasedJmsConnectionFactory();
    }

    @Override
    public JmsConnection createConnection(final Connection connection, final ExceptionListener exceptionListener)
            throws JMSException, NamingException {
        checkNotNull(connection, "Connection");
        checkNotNull(exceptionListener, "Exception Listener");

        final Context ctx = createContext(connection);
        final org.apache.qpid.jms.JmsConnectionFactory cf =
                (org.apache.qpid.jms.JmsConnectionFactory) ctx.lookup(connection.getId());

        if (isSecuredConnection(connection) && connection.isValidateCertificates()) {
            cf.setSslContext(SSLContextCreator.fromConnection(connection, null).withoutClientCertificate());
        }

        @SuppressWarnings("squid:S2095") final JmsConnection jmsConnection = (JmsConnection) cf.createConnection();
        jmsConnection.setExceptionListener(exceptionListener);
        return jmsConnection;
    }

    private Context createContext(final Connection connection) throws NamingException {
        final String connectionUri = buildAmqpConnectionUriFromConnection(connection);
        @SuppressWarnings("squid:S1149") final Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        env.put("connectionfactory." + connection.getId(), connectionUri);

        return new InitialContext(env);
    }

    public static String buildAmqpConnectionUriFromConnection(final Connection connection) {
        final String id = connection.getId();
        final String username = connection.getUsername().orElse(null);
        final String password = connection.getPassword().orElse(null);
        final String protocol = connection.getProtocol();
        final String hostname = connection.getHostname();
        final int port = connection.getPort();
        final boolean failoverEnabled = connection.isFailoverEnabled();

        final Map<String, String> specificConfig = connection.getSpecificConfig();

        final String baseUri = formatUri(protocol, hostname, port);

        final boolean anonymous = username == null || username.isEmpty() || password == null || password.isEmpty();
        final List<String> parameters = new ArrayList<>(getAmqpParameters(anonymous, specificConfig));
        final boolean isSecuredConnectionWithAcceptInvalidCertificates =
                isSecuredConnection(connection) && !connection.isValidateCertificates();
        parameters.addAll(getTransportParameters(isSecuredConnectionWithAcceptInvalidCertificates, specificConfig));
        final String nestedUri = baseUri + parameters.stream().collect(Collectors.joining("&", "?", ""));

        final List<String> globalParameters =
                new ArrayList<>(getJmsParameters(id, username, password, specificConfig));
        final String connectionUri;
        if (failoverEnabled) {
            globalParameters.addAll(getFailoverParameters(specificConfig));
            connectionUri =
                    wrapWithFailOver(nestedUri) + globalParameters.stream().collect(Collectors.joining("&", "?", ""));
        } else {
            connectionUri = nestedUri + globalParameters.stream().collect(Collectors.joining("&", "&", ""));
        }
        LOGGER.debug("[{}] URI: {}", id, connectionUri);
        return connectionUri;
    }

    private static boolean isSecuredConnection(final Connection connection) {
        return SECURE_AMQP_SCHEME.equalsIgnoreCase(connection.getProtocol());
    }

    private static String formatUri(final String protocol, final String hostname, final int port) {
        final String pattern = "{0}://{1}:{2}";
        return MessageFormat.format(pattern, protocol, hostname, Integer.toString(port));
    }

    @SuppressWarnings("squid:S2068")
    private static List<String> getJmsParameters(final String id, @Nullable final String username,
            @Nullable final String password, final Map<String, String> specificConfig) {
        String encodedId;
        try {
            encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8.displayName());
        } catch (final UnsupportedEncodingException e) {
            LOGGER.info("Encoding not supported: {}", e.getMessage());
            //fallback: replace special characters
            encodedId = id.replaceAll("[^a-zA-Z0-9]+", "");
        }
        final List<String> jmsParams = specificConfig.entrySet().stream()
                .filter(e -> e.getKey().startsWith("jms"))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toList());

        jmsParams.add("jms.clientID=" + encodedId);
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            jmsParams.add("jms.username=" + username);
            jmsParams.add("jms.password=" + password);
        }
        return jmsParams;
    }

    private static List<String> getAmqpParameters(final boolean anonymous,
            final Map<String, String> specificConfig) {

        final List<String> amqpParams = specificConfig.entrySet().stream()
                .filter(e -> e.getKey().startsWith("amqp"))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toList());

        if (anonymous) {
            amqpParams.add("amqp.saslMechanisms=ANONYMOUS");
        } else {
            amqpParams.add("amqp.saslMechanisms=PLAIN");
        }

        return amqpParams;
    }

    private static List<String> getTransportParameters(final boolean securedConnectionWithAcceptInvalidCertificates,
            final Map<String, String> specificConfig) {

        final List<String> transportParams = specificConfig.entrySet().stream()
                .filter(e -> e.getKey().startsWith("transport"))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toList());

        if (securedConnectionWithAcceptInvalidCertificates) {
            // these setting can only be applied for amqps connections:
            transportParams.add("transport.trustAll=true");
            transportParams.add("transport.verifyHost=false");
        }
        return transportParams;
    }

    private static List<String> getFailoverParameters(
            final Map<String, String> specificConfig) {

        final List<String> failoverParams = specificConfig.entrySet().stream()
                .filter(e -> e.getKey().startsWith(FAILOVER_OPTION_PREFIX))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toList());

        final long fifteenMinutes = Duration.ofMinutes(15L).toMillis();

        final List<String> defaultFailoverParams =
                // Important: we cannot interrupt connection initiation.
                // These failover parameters ensure qpid client gives up after at most
                // 128 + 256 + 512 + 1024 + 2048 + 4096 = 8064 ms < 10_000 ms = 10 s
                // at the first attempt. The client will retry endlessly after the connection
                // is established with reasonable max reconnect delay until the user terminates
                // the connection manually.
                Stream.of(FAILOVER_OPTION_PREFIX + "startupMaxReconnectAttempts=5",
                        FAILOVER_OPTION_PREFIX + "maxReconnectAttempts=-1",
                        FAILOVER_OPTION_PREFIX + "initialReconnectDelay=128",
                        FAILOVER_OPTION_PREFIX + "reconnectDelay=128",
                        FAILOVER_OPTION_PREFIX + "maxReconnectDelay=" + fifteenMinutes,
                        FAILOVER_OPTION_PREFIX + "reconnectBackOffMultiplier=2",
                        FAILOVER_OPTION_PREFIX + "useReconnectBackOff=true").collect(Collectors.toList());

        defaultFailoverParams.addAll(failoverParams);
        return defaultFailoverParams;
    }

    private static String wrapWithFailOver(final String uri) {
        return MessageFormat.format("failover:({0})", uri);
    }

}
