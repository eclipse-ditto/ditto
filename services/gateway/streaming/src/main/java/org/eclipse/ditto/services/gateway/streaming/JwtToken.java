/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.services.gateway.streaming;

/**
 * Simple event which holds a JWT in string format.
 */
public class JwtToken implements StreamControlMessage {

    private final String connectionCorrelationId;
    private final String jwt;

    public JwtToken(final String connectionCorrelationId, final String jwt) {
        this.connectionCorrelationId = connectionCorrelationId;
        this.jwt = jwt;
    }

    public String getConnectionCorrelationId() {
        return connectionCorrelationId;
    }

    public String getJwtTokenAsString() {
        return jwt;
    }

}
