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

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;

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
    ConnectionFactory createConnectionFactory(Connection connection, ExceptionHandler exceptionHandler,
            ConnectionLogger connectionLogger);

}
