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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.util.Hashtable;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.concurrent.NotThreadSafe;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.qpid.jms.JmsConnection;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;

/**
 * Factory for creating a {@link javax.jms.Connection} based on a {@link Connection}.
 */
@NotThreadSafe
public final class ConnectionBasedJmsConnectionFactory implements JmsConnectionFactory {

    private static final org.slf4j.Logger LOGGER =
            LoggerFactory.getLogger(ConnectionBasedJmsConnectionFactory.class);

    private static final String SECURE_AMQP_SCHEME = "amqps";

    private final Map<String, String> defaultConfig;
    private final Supplier<SshTunnelState> sshTunnelConfigSupplier;
    private final PlainCredentialsSupplier credentialsSupplier;

    private ConnectionBasedJmsConnectionFactory(final Map<String, String> defaultConfig,
            final Supplier<SshTunnelState> sshTunnelConfigSupplier,
            final PlainCredentialsSupplier credentialsSupplier) {

        this.defaultConfig = checkNotNull(defaultConfig, "defaultConfig");
        this.sshTunnelConfigSupplier = checkNotNull(sshTunnelConfigSupplier, "sshTunnelConfigSupplier");
        this.credentialsSupplier = credentialsSupplier;
    }

    /**
     * Returns an instance of {@code ConnectionBasedJmsConnectionFactory}.
     *
     * @param defaultConfig the default AMQP config.
     * @return the instance.
     */
    public static ConnectionBasedJmsConnectionFactory getInstance(final Map<String, String> defaultConfig,
            final Supplier<SshTunnelState> sshTunnelConfigSupplier,
            final ActorSystem actorSystem) {

        final PlainCredentialsSupplier credentialsSupplier = SaslPlainCredentialsSupplier.of(actorSystem);
        return new ConnectionBasedJmsConnectionFactory(defaultConfig, sshTunnelConfigSupplier, credentialsSupplier);
    }

    @Override
    public JmsConnection createConnection(final Connection connection, final ExceptionListener exceptionListener,
            final ConnectionLogger connectionLogger, final String clientId) throws JMSException, NamingException {

        checkNotNull(connection, "Connection");
        checkNotNull(exceptionListener, "Exception Listener");

        final Context ctx = createContext(connection, clientId);
        final org.apache.qpid.jms.JmsConnectionFactory cf =
                (org.apache.qpid.jms.JmsConnectionFactory) ctx.lookup(connection.getId().toString());

        if (isSecuredConnection(connection)) {
            cf.setSslContext(SSLContextCreator.fromConnection(connection, null, connectionLogger)
                    .withoutClientCertificate());
        }

        @SuppressWarnings("squid:S2095") final JmsConnection jmsConnection = (JmsConnection) cf.createConnection();
        jmsConnection.setExceptionListener(exceptionListener);
        return jmsConnection;
    }

    private String buildAmqpConnectionUri(final Connection connection, final String clientId) {
        return buildAmqpConnectionUri(connection, clientId, sshTunnelConfigSupplier, defaultConfig,
                credentialsSupplier);
    }

    public static String buildAmqpConnectionUri(final Connection connection,
            final String clientId,
            final Supplier<SshTunnelState> sshTunnelConfigSupplier,
            final Map<String, String> defaultConfig,
            final PlainCredentialsSupplier plainCredentialsSupplier) {

        final URI uri = sshTunnelConfigSupplier.get().getURI(connection);
        final var amqpSpecificConfig = AmqpSpecificConfig.withDefault(clientId, connection, defaultConfig,
                plainCredentialsSupplier);
        final var connectionUri = amqpSpecificConfig.render(uri.toString());
        LOGGER.debug("[{}] URI: {}", clientId, connectionUri);
        return connectionUri;
    }

    private Context createContext(final Connection connection, final String clientId) throws NamingException {
        final String connectionUri = buildAmqpConnectionUri(connection, clientId);
        @SuppressWarnings("squid:S1149") final Hashtable<Object, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
        env.put("connectionfactory." + connection.getId(), connectionUri);

        return new InitialContext(env);
    }

    static boolean isSecuredConnection(final Connection connection) {
        return SECURE_AMQP_SCHEME.equalsIgnoreCase(connection.getProtocol());
    }
}
