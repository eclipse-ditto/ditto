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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;

/**
 * Command for triggering the closing of a streaming connection (e. g. WebSocket) because of an exception.
 */
public final class CloseStreamExceptionally {

    private final DittoRuntimeException reason;
    private final String connectionCorrelationId;

    private CloseStreamExceptionally(final DittoRuntimeException reason, final CharSequence connectionCorrelationId) {
        this.reason = checkNotNull(reason, "reason");
        this.connectionCorrelationId = argumentNotEmpty(connectionCorrelationId, "connectionCorrelationId").toString();
    }

    /**
     * Returns an instance of {@code CloseWebSocket}.
     *
     * @param reason the reason for closing the WebSocket.
     * @param connectionCorrelationId the correlation ID of the WebSocket connection to be closed.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if any argument is empty.
     */
    public static CloseStreamExceptionally getInstance(final DittoRuntimeException reason,
            final CharSequence connectionCorrelationId) {

        return new CloseStreamExceptionally(reason, connectionCorrelationId);
    }

    /**
     * Returns the reason for closing the WebSocket.
     *
     * @return the reason.
     */
    public DittoRuntimeException getReason() {
        return reason;
    }

    /**
     * Returns the correlation ID of the WebSocket connection to be closed.
     *
     * @return the correlation ID.
     */
    public String getConnectionCorrelationId() {
        return connectionCorrelationId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CloseStreamExceptionally that = (CloseStreamExceptionally) o;
        return Objects.equals(reason, that.reason) &&
                Objects.equals(connectionCorrelationId, that.connectionCorrelationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reason, connectionCorrelationId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "reason=" + reason +
                ", connectionCorrelationId=" + connectionCorrelationId +
                "]";
    }

}
