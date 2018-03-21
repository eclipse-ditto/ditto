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
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.DefaultExceptionHandler;

import akka.actor.ActorRef;

/**
 * Factory for creating a RabbitMQ {@link ConnectionFactory} based on a {@link Connection}.
 */
public final class ConnectionBasedRabbitConnectionFactory extends ConnectionFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionBasedRabbitConnectionFactory.class);

    private static final String SECURE_AMQP_SCHEME = "amqps";

    /**
     * Returns an instance of {@code ConnectionBasedRabbitConnectionFactory}.
     *
     * @param dittoConnection the connection
     * @param createConnectionSender the sender which requested creating the connection used for responding with errors
     * to
     * @return the instance.
     */
    public static ConnectionFactory createConnection(final Connection dittoConnection,
            @Nullable final ActorRef createConnectionSender) {
        checkNotNull(dittoConnection, "Connection");

        try {
            final ConnectionFactory connectionFactory = new ConnectionBasedRabbitConnectionFactory();

            if (SECURE_AMQP_SCHEME.equalsIgnoreCase(dittoConnection.getProtocol())) {
                if (dittoConnection.isValidateCertificates()) {
                    connectionFactory.useSslProtocol(SSLContext.getDefault());
                } else {
                    // attention: this accepts all certificates whether they are valid or not
                    connectionFactory.useSslProtocol();
                }
            }

            connectionFactory.setUri(dittoConnection.getUri());

            // this makes no difference as the used newmotion client always sets the AutomaticRecoveryEnabled to false:
            connectionFactory.setAutomaticRecoveryEnabled(dittoConnection.isFailoverEnabled());

            connectionFactory.setExceptionHandler(
                    new RabbitMQExceptionHandler(dittoConnection, createConnectionSender));

            return connectionFactory;
        } catch (final NoSuchAlgorithmException | KeyManagementException | URISyntaxException e) {
            LOGGER.warn(e.getMessage());
            throw new IllegalStateException("Failed to create RabbitMQ connection factory.", e);
        }
    }

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

    /**
     * Handles exceptions by telling back the passed {@code createConnectionSender} a {@link ConnectionFailedException}
     * with details why it failed.
     */
    private static class RabbitMQExceptionHandler extends DefaultExceptionHandler {

        private final Connection dittoConnection;
        @Nullable private final ActorRef createConnectionSender;

        RabbitMQExceptionHandler(final Connection dittoConnection,
                @Nullable final ActorRef createConnectionSender) {
            this.dittoConnection = dittoConnection;
            this.createConnectionSender = createConnectionSender;
        }

        @Override
        public void handleUnexpectedConnectionDriverException(final com.rabbitmq.client.Connection conn,
                final Throwable exception) {

            // establishing the connection was not possible (maybe wrong host, port, credentials, ...)
            LOGGER.warn("Got unexpected ConnectionDriver exception on connection <{}> {}: {}", dittoConnection.getId(),
                    exception.getClass().getSimpleName(), exception.getMessage());
            if (createConnectionSender != null) {
                createConnectionSender.tell(
                        ConnectionFailedException.newBuilder(dittoConnection.getId())
                                .description("The requested Connection could not be connected due to '" +
                                        exception.getClass().getSimpleName() + ": " + exception.getMessage() + "'")
                                .cause(exception)
                                .build(), null);
            } else {
                super.handleUnexpectedConnectionDriverException(conn, exception);
            }
        }
    }
}
