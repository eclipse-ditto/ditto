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
package org.eclipse.ditto.connectivity.service.messaging.rabbitmq;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ExceptionHandler;

/**
 * Factory for creating a RabbitMQ {@link ConnectionFactory} based on a {@link Connection}.
 */
public final class ConnectionBasedRabbitConnectionFactoryFactory implements RabbitConnectionFactoryFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionBasedRabbitConnectionFactoryFactory.class);

    private static final String SECURE_AMQP_SCHEME = "amqps";

    private final Supplier<SshTunnelState> tunnelConfigSupplier;

    private ConnectionBasedRabbitConnectionFactoryFactory(final Supplier<SshTunnelState> tunnelConfigSupplier) {
        this.tunnelConfigSupplier = tunnelConfigSupplier;
    }

    /**
     * Returns an instance of {@code ConnectionBasedRabbitConnectionFactoryFactory}.
     *
     * @return the instance.
     */
    public static ConnectionBasedRabbitConnectionFactoryFactory getInstance(final Supplier<SshTunnelState> tunnelConfigSupplier) {
        return new ConnectionBasedRabbitConnectionFactoryFactory(tunnelConfigSupplier);
    }

    @Override
    public ConnectionFactory createConnectionFactory(final Connection connection,
            final ExceptionHandler exceptionHandler, final ConnectionLogger connectionLogger) {
        checkNotNull(connection, "Connection");
        checkNotNull(exceptionHandler, "Exception Handler");

        try {
            final ConnectionFactory connectionFactory = new CustomConnectionFactory();
            if (SECURE_AMQP_SCHEME.equalsIgnoreCase(connection.getProtocol())) {
                if (connection.isValidateCertificates()) {
                    final SSLContextCreator sslContextCreator =
                            SSLContextCreator.fromConnection(connection, null, connectionLogger);
                    connectionFactory.useSslProtocol(sslContextCreator.withoutClientCertificate());
                } else {
                    // attention: this accepts all certificates whether they are valid or not
                    connectionFactory.useSslProtocol();
                }
            }

            final URI uri = tunnelConfigSupplier.get().getURI(connection);
            connectionFactory.setUri(uri.toString());

            // this makes no difference as the used newmotion client always sets the AutomaticRecoveryEnabled to false:
            connectionFactory.setAutomaticRecoveryEnabled(connection.isFailoverEnabled());

            connectionFactory.setExceptionHandler(exceptionHandler);

            configureConnectionFactory(connectionFactory, connection.getSpecificConfig());

            return connectionFactory;
        } catch (final NoSuchAlgorithmException | KeyManagementException | URISyntaxException e) {
            LOGGER.warn(e.getMessage());
            throw new IllegalStateException("Failed to create RabbitMQ connection factory.", e);
        }
    }

    private void configureConnectionFactory(final ConnectionFactory connectionFactory,
            final Map<String, String> specificConfig) {
        Optional.ofNullable(specificConfig.get("channelRpcTimeout"))
                .map(Integer::parseInt)
                .ifPresent(connectionFactory::setChannelRpcTimeout);
        Optional.ofNullable(specificConfig.get("connectionTimeout"))
                .map(Integer::parseInt)
                .ifPresent(connectionFactory::setConnectionTimeout);
        Optional.ofNullable(specificConfig.get("handshakeTimeout"))
                .map(Integer::parseInt)
                .ifPresent(connectionFactory::setHandshakeTimeout);
        Optional.ofNullable(specificConfig.get("channelShouldCheckRpcResponseType"))
                .map(Boolean::parseBoolean)
                .ifPresent(connectionFactory::setChannelShouldCheckRpcResponseType);
        Optional.ofNullable(specificConfig.get("networkRecoveryInterval"))
                .map(Integer::parseInt)
                .ifPresent(connectionFactory::setNetworkRecoveryInterval);
        Optional.ofNullable(specificConfig.get("requestedChannelMax"))
                .map(Integer::parseInt)
                .ifPresent(connectionFactory::setRequestedChannelMax);
        Optional.ofNullable(specificConfig.get("requestedFrameMax"))
                .map(Integer::parseInt)
                .ifPresent(connectionFactory::setRequestedFrameMax);
        Optional.ofNullable(specificConfig.get("requestedHeartbeat"))
                .map(Integer::parseInt)
                .ifPresent(connectionFactory::setRequestedHeartbeat);
        Optional.ofNullable(specificConfig.get("topologyRecoveryEnabled"))
                .map(Boolean::parseBoolean)
                .ifPresent(connectionFactory::setTopologyRecoveryEnabled);
        Optional.ofNullable(specificConfig.get("shutdownTimeout"))
                .map(Integer::parseInt)
                .ifPresent(connectionFactory::setShutdownTimeout);
    }


    private static class CustomConnectionFactory extends ConnectionFactory {

        @Override
        public com.rabbitmq.client.Connection newConnection() throws IOException, TimeoutException {
            try {
                return super.newConnection();
            } catch (final IOException | TimeoutException e) {
                // this is custom - normally the ExceptionHandler is not called if there is no connection yet:
                getExceptionHandler().handleUnexpectedConnectionDriverException(null, e);
                throw e;
            }
        }
    }


}
