/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.gateway.service.endpoints.routes.connections;

/**
 * An enumeration of the query parameters for the connections HTTP API.
 */
public enum ConnectionsParameter {

    /**
     * Request parameter for doing a dry-run before creating a connection.
     */
    DRY_RUN("dry-run"),

    IDS_ONLY("ids-only"),

    /**
     * Request parameter for including only the selected fields in the Connection JSON document(s).
     */
    FIELDS("fields");

    private final String parameterValue;

    ConnectionsParameter(final String parameterValue) {
        this.parameterValue = parameterValue;
    }

    @Override
    public String toString() {
        return parameterValue;
    }

}
