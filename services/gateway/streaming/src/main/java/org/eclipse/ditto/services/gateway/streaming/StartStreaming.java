/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.streaming;

import java.util.Objects;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * Message indicating a demand to receive entities of a specified {@link StreamingType} via a "streaming" connection.
 */
public final class StartStreaming {

    private final StreamingType streamingType;
    private final String connectionCorrelationId;
    private final AuthorizationContext authorizationContext;

    /**
     * Constructs a new {@link StartStreaming} instance.
     *
     * @param streamingType the type of entity to start the streaming for.
     * @param connectionCorrelationId the correlationId of the connection/session.
     * @param authorizationContext the {@link AuthorizationContext} of the connection/session.
     */
    public StartStreaming(final StreamingType streamingType, final String connectionCorrelationId,
            final AuthorizationContext authorizationContext) {
        this.streamingType = streamingType;
        this.connectionCorrelationId = connectionCorrelationId;
        this.authorizationContext = authorizationContext;
    }

    /**
     * @return the Streaming type of what streaming to start.
     */
    public StreamingType getStreamingType() {
        return streamingType;
    }

    public String getConnectionCorrelationId() {
        return connectionCorrelationId;
    }

    public AuthorizationContext getAuthorizationContext() {
        return authorizationContext;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final StartStreaming that = (StartStreaming) o;
        return streamingType == that.streamingType &&
                Objects.equals(connectionCorrelationId, that.connectionCorrelationId) &&
                Objects.equals(authorizationContext, that.authorizationContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamingType, connectionCorrelationId, authorizationContext);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "streamingType=" + streamingType +
                ", connectionCorrelationId=" + connectionCorrelationId +
                ", authorizationContext=" + authorizationContext +
                "]";
    }
}
