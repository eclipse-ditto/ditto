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

public class JwtToken {

    private final String jwtToken;
    private final String connectionCorrelationId;

    public JwtToken(final String jwtToken, final String connectionCorrelationId) {
        this.jwtToken = jwtToken;
        this.connectionCorrelationId = connectionCorrelationId;
    }

    public String getJwtTokenAsString() {
        return jwtToken;
    }

    public String getConnectionCorrelationId() {
        return connectionCorrelationId;
    }
}
