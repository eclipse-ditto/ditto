/*
 * Copyright Bosch Software Innovations GmbH 2017
 *
 * All rights reserved, also regarding any disposal, exploitation,
 * reproduction, editing, distribution, as well as in the event of
 * applications for industrial property rights.
 *
 * This software is the confidential and proprietary information
 * of Bosch Software Innovations GmbH. You shall not disclose
 * such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you
 * entered into with Bosch Software Innovations GmbH.
 */
package org.eclipse.ditto.gateway.service.endpoints.routes.connections;

/**
 * An enumeration of the query parameters for the connections HTTP API.
 */
public enum ConnectionsParameter {

    /**
     * Request parameter for doing a dry-run before creating a connection.
     */
    DRY_RUN("dry-run");

    private final String parameterValue;

    ConnectionsParameter(final String parameterValue) {
        this.parameterValue = parameterValue;
    }

    @Override
    public String toString() {
        return parameterValue;
    }

}
