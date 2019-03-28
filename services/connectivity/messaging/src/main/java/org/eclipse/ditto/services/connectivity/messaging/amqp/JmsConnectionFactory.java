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

import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.naming.NamingException;

import org.apache.qpid.jms.JmsConnection;
import org.eclipse.ditto.model.connectivity.Connection;

/**
 * Creates a new {@link javax.jms.Connection}.
 */
public interface JmsConnectionFactory {

    /**
     * Creates a new {@code Connection}.
     *
     * @param connection the connection to use for the returned JMS Connection.
     * @param exceptionListener the ExceptionListener to configure for the returned JMS Connection.
     * @return the JMS Connection.
     * @throws javax.jms.JMSException if the context could not be created.
     * @throws javax.naming.NamingException if the identifier of {@code connection} could not be found in the Context.
     */
    JmsConnection createConnection(Connection connection, ExceptionListener exceptionListener)
            throws JMSException, NamingException;

}
