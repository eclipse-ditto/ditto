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

package org.eclipse.ditto.gateway.service.streaming.signals;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Simple event which holds a JWT in string format.
 */
public final class Jwt implements StreamControlMessage {

    private final String stringRepresentation;
    private final String connectionCorrelationId;

    private Jwt(final String stringRepresentation, final CharSequence connectionCorrelationId) {
        this.connectionCorrelationId = checkNotNull(connectionCorrelationId, "connectionCorrelationId").toString();
        this.stringRepresentation = checkNotNull(stringRepresentation, "stringRepresentation");
    }

    /**
     * Returns a new instance of {@code Jwt}.
     *
     * @param tokenStringRepresentation the string representation of the token.
     * @param connectionCorrelationId the correlation ID of the connection that received the token.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Jwt newInstance(final String tokenStringRepresentation, final CharSequence connectionCorrelationId) {
        return new Jwt(tokenStringRepresentation, connectionCorrelationId);
    }

    /**
     * Returns the correlation ID of the connection that received the token.
     *
     * @return the correlation ID.
     */
    public String getConnectionCorrelationId() {
        return connectionCorrelationId;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Jwt jwtToken = (Jwt) o;
        return Objects.equals(connectionCorrelationId, jwtToken.connectionCorrelationId) &&
                Objects.equals(stringRepresentation, jwtToken.stringRepresentation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionCorrelationId, stringRepresentation);
    }

    /**
     * @return this token as string.
     */
    @Override
    public String toString() {
        return stringRepresentation;
    }

}
