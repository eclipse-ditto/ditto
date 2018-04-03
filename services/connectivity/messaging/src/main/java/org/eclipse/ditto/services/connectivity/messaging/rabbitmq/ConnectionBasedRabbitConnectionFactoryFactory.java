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
package org.eclipse.ditto.services.connectivity.messaging.rabbitmq;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;

import org.eclipse.ditto.model.connectivity.Connection;
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

    private ConnectionBasedRabbitConnectionFactoryFactory() {
        // no-op
    }

    /**
     * Returns an instance of {@code ConnectionBasedRabbitConnectionFactoryFactory}.
     *
     * @return the instance.
     */
    public static ConnectionBasedRabbitConnectionFactoryFactory getInstance() {
        return new ConnectionBasedRabbitConnectionFactoryFactory();
    }

    @Override
    public ConnectionFactory createConnectionFactory(final Connection connection,
            final ExceptionHandler exceptionHandler) {
        checkNotNull(connection, "Connection");
        checkNotNull(exceptionHandler, "Exception Handler");

        try {
            final ConnectionFactory connectionFactory = new CustomConnectionFactory();
            if (SECURE_AMQP_SCHEME.equalsIgnoreCase(connection.getProtocol())) {
                if (connection.isValidateCertificates()) {
                    connectionFactory.useSslProtocol(SSLContext.getDefault());
                } else {
                    // attention: this accepts all certificates whether they are valid or not
                    connectionFactory.useSslProtocol();
                }
            }

            connectionFactory.setUri(connection.getUri());

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

    /**
     *
     */
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
