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
import static org.eclipse.ditto.placeholders.PlaceholderFactory.newHeadersPlaceholder;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.Placeholders;
import org.eclipse.ditto.edge.service.placeholders.EntityIdPlaceholder;
import org.eclipse.ditto.edge.service.placeholders.FeaturePlaceholder;
import org.eclipse.ditto.edge.service.placeholders.RequestPlaceholder;
import org.eclipse.ditto.edge.service.placeholders.ThingPlaceholder;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.placeholders.PlaceholderFilter;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.protocol.placeholders.ResourcePlaceholder;
import org.eclipse.ditto.protocol.placeholders.TopicPathPlaceholder;
import org.eclipse.ditto.things.model.ThingFieldSelector;

/**
 * Message indicating a demand to receive entities of a specified {@link StreamingType} via a "streaming" connection.
 */
public final class StartStreaming implements StreamControlMessage {

    private final StreamingType streamingType;
    private final String connectionCorrelationId;
    private final AuthorizationContext authorizationContext;
    private final List<String> namespaces;
    @Nullable private final String filter;
    @Nullable private final ThingFieldSelector extraFields;
    @Nullable private final CharSequence correlationId;

    private StartStreaming(final StartStreamingBuilder builder) {
        streamingType = builder.streamingType;
        connectionCorrelationId = builder.connectionCorrelationId;
        authorizationContext = builder.authorizationContext;
        @Nullable final Collection<String> namespacesFromBuilder = builder.namespaces;
        namespaces = null != namespacesFromBuilder ? List.copyOf(namespacesFromBuilder) : Collections.emptyList();
        filter = Objects.toString(builder.filter, null);
        extraFields = validateExtraFields(builder.extraFields);
        correlationId = builder.correlationId;
    }

    @Nullable
    private static ThingFieldSelector validateExtraFields(@Nullable final ThingFieldSelector extraFields) {
        if (extraFields == null) {
            return null;
        }
        final String fieldSelector = extraFields.toString();
        if (Placeholders.containsAnyPlaceholder(fieldSelector)) {
            PlaceholderFilter.validate(fieldSelector,
                    newHeadersPlaceholder(),
                    EntityIdPlaceholder.getInstance(),
                    ThingPlaceholder.getInstance(),
                    FeaturePlaceholder.getInstance(),
                    TopicPathPlaceholder.getInstance(),
                    ResourcePlaceholder.getInstance(),
                    TimePlaceholder.getInstance(),
                    RequestPlaceholder.getInstance());
        }
        return extraFields;
    }

    /**
     * Returns a mutable builder with a fluent API for creating an instance of StartStreaming.
     *
     * @param streamingType the type of entity to start the streaming for.
     * @param connectionCorrelationId the correlationId of the connection/session.
     * @param authorizationContext the {@link AuthorizationContext} of the connection/session.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static StartStreamingBuilder getBuilder(final StreamingType streamingType,
            final CharSequence connectionCorrelationId, final AuthorizationContext authorizationContext) {

        return new StartStreamingBuilder(streamingType, connectionCorrelationId, authorizationContext);
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

    public Optional<CharSequence> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    /**
     * @return the List of namespaces for which {@link org.eclipse.ditto.base.model.signals.Signal}s should be emitted to the
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

    /**
     * Returns the selector for the extra fields and their values to enrich outgoing signals with.
     *
     * @return the selector or an empty Optional if signals should not be enriched.
     */
    public Optional<ThingFieldSelector> getExtraFields() {
        return Optional.ofNullable(extraFields);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StartStreaming that = (StartStreaming) o;
        return streamingType == that.streamingType &&
                Objects.equals(connectionCorrelationId, that.connectionCorrelationId) &&
                Objects.equals(authorizationContext, that.authorizationContext) &&
                Objects.equals(namespaces, that.namespaces) &&
                Objects.equals(filter, that.filter) &&
                Objects.equals(extraFields, that.extraFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamingType, connectionCorrelationId, authorizationContext, namespaces, filter,
                extraFields);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "streamingType=" + streamingType +
                ", connectionCorrelationId=" + connectionCorrelationId +
                ", authorizationContext=" + authorizationContext +
                ", namespaces=" + namespaces +
                ", filter=" + filter +
                ", extraFields=" + extraFields +
                "]";
    }

    /**
     * A mutable builder with a fluent API for creating an instance of StartStreaming.
     */
    @NotThreadSafe
    public static final class StartStreamingBuilder {

        private final StreamingType streamingType;
        private final String connectionCorrelationId;
        private final AuthorizationContext authorizationContext;

        @Nullable private Collection<String> namespaces;
        @Nullable private CharSequence filter;
        @Nullable private ThingFieldSelector extraFields;
        @Nullable private CharSequence correlationId;

        private StartStreamingBuilder(final StreamingType streamingType, final CharSequence connectionCorrelationId,
                final AuthorizationContext authorizationContext) {

            this.streamingType = checkNotNull(streamingType, "streamingType");
            this.connectionCorrelationId = checkNotNull(connectionCorrelationId, "connectionCorrelationId")
                    .toString();
            this.authorizationContext = checkNotNull(authorizationContext, "authorizationContext");
            namespaces = null;
            filter = null;
            extraFields = null;
        }

        /**
         * Sets the namespaces for which the filter should be applied.
         *
         * @param namespaces the namespaces for which the filter should be applied &ndash; if empty or {@code null},
         * all namespaces are considered.
         * @return this builder instance to allow method chaining.
         */
        public StartStreamingBuilder withNamespaces(@Nullable final Collection<String> namespaces) {
            this.namespaces = namespaces;
            return this;
        }

        /**
         * Sets the filter to be applied to events.
         *
         * @param filter the filter string (RQL) to apply for event filtering or {@code null} if none should be applied.
         * @return this builder instance to allow method chaining.
         */
        public StartStreamingBuilder withFilter(@Nullable final CharSequence filter) {
            // policy announcements do not support filter.
            if (streamingType != StreamingType.POLICY_ANNOUNCEMENTS) {
                this.filter = filter;
            }
            return this;
        }

        /**
         * Determines the extra fields and their values to be additionally set to outgoing signals.
         *
         * @param extraFields selector for the extra fields or {@code null} if outgoing signals should not be enriched.
         * @return this builder instance to allow method chaining.
         */
        public StartStreamingBuilder withExtraFields(@Nullable final ThingFieldSelector extraFields) {
            // policy announcements do not support extra fields.
            if (streamingType != StreamingType.POLICY_ANNOUNCEMENTS) {
                this.extraFields = extraFields;
            }
            return this;
        }

        /**
         * Determines the correlation-id of the request.
         *
         * @param correlationId the correlationId read from the parameters.
         * @return this builder instance to allow method chaining.
         */
        public StartStreamingBuilder withCorrelationId(@Nullable final CharSequence correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        /**
         * Builds an instance of StartStreaming with the properties of this builder.
         *
         * @return the StartStreaming instance.
         */
        public StartStreaming build() {
            return new StartStreaming(this);
        }

    }

}
