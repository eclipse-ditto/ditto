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

import org.eclipse.ditto.model.connectivity.Connection;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ExceptionHandler;

/**
 * Creates a new RabbitMQ {@link ConnectionFactory}.
 */
public interface RabbitConnectionFactoryFactory {

    /**
     * Creates a new {@code ConnectionFactory}.
     *
     * @param connection the connection to use for the returned Rabbit ConnectionFactory.
     * @param exceptionHandler the ExceptionHandler to configure for the returned Rabbit ConnectionFactory.
     * @return the Rabbit ConnectionFactory.
     * @throws NullPointerException when any argument is null
     */
    ConnectionFactory createConnectionFactory(Connection connection, ExceptionHandler exceptionHandler);

}
