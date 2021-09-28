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
package org.eclipse.ditto.connectivity.service.messaging.amqp.status;

import java.util.Objects;

import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;

/**
 * Reports a connections failure.
 */
public final class ConnectionFailureStatusReport {

    private final ConnectionFailure failure;
    private final boolean recoverable;

    private ConnectionFailureStatusReport(final ConnectionFailure failure, final boolean recoverable) {
        this.failure = failure;
        this.recoverable = recoverable;
    }

    public static ConnectionFailureStatusReport get(final ConnectionFailure failure, final boolean recoverable) {
        return new ConnectionFailureStatusReport(failure, recoverable);
    }

    /**
     * @return the failure
     */
    public ConnectionFailure getFailure() {
        return failure;
    }

    /**
     * @return whether the occurred failure is recoverable (i.e. the client handles the connection recovery) or not
     * (connection must be closed and re-opened by the client actor)
     */
    public boolean isRecoverable() {
        return recoverable;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConnectionFailureStatusReport that = (ConnectionFailureStatusReport) o;
        return recoverable == that.recoverable && Objects.equals(failure, that.failure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(failure, recoverable);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "failure=" + failure +
                ", recoverable=" + recoverable +
                "]";
    }
}
