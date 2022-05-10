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
package org.eclipse.ditto.gateway.service.streaming.signals;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.pubsub.StreamingType;

/**
 * Message indicating a demand to receive entities of a specified {@link StreamingType} via a "streaming" connection.
 */
@Immutable
public final class StopStreaming implements StreamControlMessage {

    private final StreamingType streamingType;
    private final String connectionCorrelationId;

    /**
     * Constructs a new {@code StopStreaming} object.
     *
     * @param streamingType the type of entity to start the streaming for.
     * @param connectionCorrelationId the correlation ID of the connection/session.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public StopStreaming(final StreamingType streamingType, final CharSequence connectionCorrelationId) {
        this.streamingType = checkNotNull(streamingType, "streamingType");
        this.connectionCorrelationId = checkNotNull(connectionCorrelationId, "connectionCorrelationId")
                .toString();
    }

    /**
     * @return the Streaming type of what streaming to stop.
     */
    public StreamingType getStreamingType() {
        return streamingType;
    }

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
        final StopStreaming that = (StopStreaming) o;
        return streamingType == that.streamingType &&
                Objects.equals(connectionCorrelationId, that.connectionCorrelationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamingType, connectionCorrelationId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "streamingType=" + streamingType +
                ", connectionCorrelationId=" + connectionCorrelationId +
                "]";
    }

}
