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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;

/**
 * Message indicating a demand to receive entities of a specified {@link StreamingType} via a "streaming" connection.
 */
public final class StartStreaming {

    private final StreamingType streamingType;
    private final String connectionCorrelationId;
    private final AuthorizationContext authorizationContext;
    private final List<String> namespaces;
    @Nullable private final String filter;

    /**
     * Constructs a new {@link StartStreaming} instance.
     *
     * @param streamingType the type of entity to start the streaming for.
     * @param connectionCorrelationId the correlationId of the connection/session.
     * @param authorizationContext the {@link AuthorizationContext} of the connection/session.
     * @param namespaces the namespaces for which the filter should be applied - if empty, all namespaces are
     * considered.
     * @param filter the filter string (RQL) to apply for event filtering or {@code null} if none should be applied.
     */
    public StartStreaming(final StreamingType streamingType, final String connectionCorrelationId,
            final AuthorizationContext authorizationContext, final List<String> namespaces,
            @Nullable final String filter) {
        this.streamingType = streamingType;
        this.connectionCorrelationId = connectionCorrelationId;
        this.authorizationContext = authorizationContext;
        this.namespaces = Collections.unmodifiableList(new ArrayList<>(namespaces));
        this.filter = filter;
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

    /**
     * @return the List of namespaces for which {@link org.eclipse.ditto.signals.base.Signal}s should be emitted to the
     * stream
     */
    public List<String> getNamespaces() {
        return namespaces;
    }

    /**
     * @return the optional RQL filter to apply for events before publishing to the stream
     */
    public Optional<String> getFilter() {
        return Optional.ofNullable(filter);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final StartStreaming that = (StartStreaming) o;
        return streamingType == that.streamingType &&
                Objects.equals(connectionCorrelationId, that.connectionCorrelationId) &&
                Objects.equals(authorizationContext, that.authorizationContext) &&
                Objects.equals(namespaces, that.namespaces) &&
                Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamingType, connectionCorrelationId, authorizationContext, namespaces, filter);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "streamingType=" + streamingType +
                ", connectionCorrelationId=" + connectionCorrelationId +
                ", authorizationContext=" + authorizationContext +
                ", namespaces=" + namespaces +
                ", eventFilter=" + filter +
                "]";
    }
}
