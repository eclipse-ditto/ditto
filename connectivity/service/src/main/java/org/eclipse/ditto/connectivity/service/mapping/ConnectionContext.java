/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.mapping;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;

/**
 * Connection-related information relevant to a message mapper.
 */
public interface ConnectionContext {

    /**
     * @return the connection in which the mapper is defined.
     */
    Connection getConnection();

    /**
     * @return the connectivity config for the connection in which the mapper is defined.
     */
    ConnectivityConfig getConnectivityConfig();

    /**
     * Create a copy of this context with a modified connectivity config.
     *
     * @param modifiedConfig the modified config.
     * @return the new context.
     */
    ConnectionContext withConnectivityConfig(ConnectivityConfig modifiedConfig);

    /**
     * Create a copy of this context with a modified connection.
     *
     * @param modifiedConnection the modified connection.
     * @return the new context.
     */
    ConnectionContext withConnection(Connection modifiedConnection);

}
