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
package org.eclipse.ditto.services.amqpbridge.messaging.rabbitmq;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.ConnectionFactory;

/**
 * Factory for creating a RabbitMQ {@link ConnectionFactory} based on a {@link AmqpConnection}.
 */
public class AmqpConnectionBasedRabbitConnectionFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmqpConnectionBasedRabbitConnectionFactory.class);
    private static final String SECURE_AMQP_SCHEME = "amqps";

    private AmqpConnectionBasedRabbitConnectionFactory() {
        // no-op
    }

    /**
     * Returns an instance of {@code AmqpConnectionBasedRabbitConnectionFactory}.
     *
     * @return the instance.
     */
    public static ConnectionFactory createConnection(final AmqpConnection amqpConnection) {
        checkNotNull(amqpConnection, "Connection");

        try {
            final ConnectionFactory connectionFactory = new ConnectionFactory();

            if (SECURE_AMQP_SCHEME.equalsIgnoreCase(amqpConnection.getProtocol())) {
                if (amqpConnection.isValidateCertificates()) {
                    connectionFactory.useSslProtocol(SSLContext.getDefault());
                } else {
                    // attention: this accepts all certificates whether they are valid or not
                    connectionFactory.useSslProtocol();
                }
            }

            connectionFactory.setUri(amqpConnection.getUri());

            if (amqpConnection.isFailoverEnabled()) {
                connectionFactory.setAutomaticRecoveryEnabled(true);
            } else {
                connectionFactory.setAutomaticRecoveryEnabled(false);
            }

            return connectionFactory;
        } catch (NoSuchAlgorithmException | KeyManagementException | URISyntaxException e) {
            LOGGER.warn(e.getMessage());
            throw new IllegalStateException("Failed to create RabbitMQ connection factory.", e);
        }
    }
}
