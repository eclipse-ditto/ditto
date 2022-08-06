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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.PlaceholderResolver;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;

/**
 * Creator of expression resolvers for incoming and outgoing messages.
 */
public final class Resolvers {

    private Resolvers() {
        throw new AssertionError();
    }

    private static final DittoProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    /**
     * Placeholder resolver creators for incoming and outgoing messages.
     */
    private static final List<ResolverCreator<?>> RESOLVER_CREATORS = Arrays.asList(
            // For incoming messages, header mapping injects headers of external messages into Ditto headers.
            ResolverCreator.of(PlaceholderFactory.newHeadersPlaceholder(), (e, s, t, a, c) -> e),
            ResolverCreator.of(ConnectivityPlaceholders.newEntityPlaceholder(),
                    (e, s, t, a, c) -> WithEntityId.getEntityIdOfType(EntityId.class, s).orElse(null)),
            ResolverCreator.of(ConnectivityPlaceholders.newThingPlaceholder(),
                    (e, s, t, a, c) -> WithEntityId.getEntityIdOfType(EntityId.class, s).orElse(null)),
            ResolverCreator.of(ConnectivityPlaceholders.newFeaturePlaceholder(), (e, s, t, a, c) -> s),
            ResolverCreator.of(ConnectivityPlaceholders.newTopicPathPlaceholder(), (e, s, t, a, c) -> t),
            ResolverCreator.of(ConnectivityPlaceholders.newResourcePlaceholder(), (e, s, t, a, c) -> s),
            ResolverCreator.of(ConnectivityPlaceholders.newTimePlaceholder(), (e, s, t, a, c) -> new Object()),
            ResolverCreator.of(ConnectivityPlaceholders.newRequestPlaceholder(), (e, s, t, a, c) -> a),
            ResolverCreator.of(ConnectivityPlaceholders.newConnectionIdPlaceholder(), (e, s, t, a, c) -> c)
    );

    private static final List<Placeholder<?>> PLACEHOLDERS = Collections.unmodifiableList(
            RESOLVER_CREATORS.stream()
                    .map(ResolverCreator::getPlaceholder)
                    .toList()
    );

    /**
     * @return Array of all placeholders for target address and source/target header mappings.
     */
    @SuppressWarnings({"rawtypes", "java:S3740"})
    public static Placeholder[] getPlaceholders() {
        return PLACEHOLDERS.toArray(new Placeholder[0]);
    }

    /**
     * Create an expression resolver for an outbound message.
     *
     * @param mappedOutboundSignal the mapped external message.
     * @param sendingConnectionId the ID of the connection sending the message.
     * @return the expression resolver.
     */
    public static ExpressionResolver forOutbound(final OutboundSignal.Mapped mappedOutboundSignal,
            final ConnectionId sendingConnectionId) {

        final Signal<?> signal = mappedOutboundSignal.getSource();
        final ExternalMessage externalMessage = mappedOutboundSignal.getExternalMessage();
        final Adaptable adaptable = mappedOutboundSignal.getAdaptable();
        return PlaceholderFactory.newExpressionResolver(
                RESOLVER_CREATORS.stream()
                        .map(creator -> creator.create(adaptable.getDittoHeaders(), signal,
                                externalMessage.getTopicPath().orElse(null),
                                signal.getDittoHeaders().getAuthorizationContext(),
                                sendingConnectionId))
                        .toArray(PlaceholderResolver[]::new)
        );
    }

    /**
     * Create an expression resolver for an signal.
     *
     * @param signal the signal.
     * @param connectionId the ID of the connection that handles the signal
     * @return the expression resolver.
     * @since 1.3.0
     */
    public static ExpressionResolver forSignal(final Signal<?> signal,
            final ConnectionId connectionId) {
        return PlaceholderFactory.newExpressionResolver(
                RESOLVER_CREATORS.stream()
                        .map(creator -> creator.create(signal.getDittoHeaders(), signal,
                                PROTOCOL_ADAPTER.toTopicPath(signal),
                                signal.getDittoHeaders().getAuthorizationContext(),
                                connectionId))
                        .toArray(PlaceholderResolver[]::new)
        );
    }

    /**
     * Create an expression resolver for an external Message.
     *
     * @param message the external message.
     * @param receivingConnectionId the ID of the connection receiving the message.
     * @return the expression resolver.
     * @since 1.3.0
     */
    public static ExpressionResolver forExternalMessage(final ExternalMessage message,
            final ConnectionId receivingConnectionId) {

        return PlaceholderFactory.newExpressionResolver(
                RESOLVER_CREATORS.stream()
                        .map(creator -> creator.create(makeCaseInsensitive(message.getHeaders()), null,
                                message.getTopicPath().orElse(null),
                                message.getAuthorizationContext().orElse(null),
                                receivingConnectionId))
                        .toArray(PlaceholderResolver[]::new)
        );
    }

    /**
     * Create an expression resolver for an mappable outbound signal.
     *
     * @param outboundSignal the outbound signal.
     * @param sendingConnectionId the ID of the connection sending the message.
     * @return the expression resolver.
     * @since 1.2.0
     */
    public static ExpressionResolver forOutboundSignal(final OutboundSignal.Mappable outboundSignal, final
    ConnectionId sendingConnectionId) {

        return PlaceholderFactory.newExpressionResolver(
                RESOLVER_CREATORS.stream()
                        .map(creator -> creator.create(outboundSignal.getSource().getDittoHeaders(),
                                outboundSignal.getSource(),
                                PROTOCOL_ADAPTER.toTopicPath(outboundSignal.getSource()),
                                outboundSignal.getSource().getDittoHeaders().getAuthorizationContext(),
                                sendingConnectionId))
                        .toArray(PlaceholderResolver[]::new)
        );
    }

    /**
     * Create an expression resolver for an inbound message.
     *
     * @param externalMessage the inbound external message.
     * @param signal the mapped Ditto signal without internal headers.
     * @param topicPath the topic path of the inbound message, or null if it cannot be determined.
     * @param authorizationContext the authorization context of the inbound message, or null if it cannot be determined.
     * @param connectionId The ID of the connection that received the inbound message.
     * @return the expression resolver.
     */
    public static ExpressionResolver forInbound(final ExternalMessage externalMessage, final Signal<?> signal,
            @Nullable final TopicPath topicPath, @Nullable final AuthorizationContext authorizationContext,
            @Nullable final ConnectionId connectionId) {
        return PlaceholderFactory.newExpressionResolver(
                RESOLVER_CREATORS.stream()
                        .map(creator ->
                                creator.create(makeCaseInsensitive(externalMessage.getHeaders()), signal, topicPath,
                                        authorizationContext, connectionId))
                        .toArray(PlaceholderResolver[]::new)
        );
    }

    private static DittoHeaders makeCaseInsensitive(final Map<String, String> externalHeaders) {
        return DittoHeaders.of(externalHeaders);
    }

    /**
     * Extract data for a placeholder.
     *
     * @param <T> the data required by the placeholder.
     */
    @FunctionalInterface
    private interface ResolverDataExtractor<T> {

        @Nullable
        T extract(Map<String, String> inputHeaders, @Nullable Signal<?> signal, @Nullable TopicPath topicPath,
                @Nullable AuthorizationContext authorizationContext, @Nullable final ConnectionId connectionId);
    }

    /**
     * Creator of a placeholder resolver.
     * Must be an inner class due to the lack of existential types in Java 8.
     *
     * @param <T> the data required by the placeholder.
     */
    private static final class ResolverCreator<T> {

        private final Placeholder<T> placeholder;
        private final ResolverDataExtractor<T> dataExtractor;

        private ResolverCreator(final Placeholder<T> placeholder, final ResolverDataExtractor<T> dataExtractor) {
            this.placeholder = placeholder;
            this.dataExtractor = dataExtractor;
        }

        private static <T> ResolverCreator<T> of(final Placeholder<T> placeholder,
                final ResolverDataExtractor<T> dataExtractor) {

            return new ResolverCreator<>(placeholder, dataExtractor);
        }

        private PlaceholderResolver<T> create(final Map<String, String> inputHeaders, @Nullable final Signal<?> signal,
                @Nullable final TopicPath topicPath, @Nullable final AuthorizationContext authorizationContext,
                @Nullable ConnectionId connectionId) {
            return PlaceholderFactory.newPlaceholderResolver(placeholder,
                    dataExtractor.extract(inputHeaders, signal, topicPath, authorizationContext, connectionId));
        }

        private Placeholder<T> getPlaceholder() {
            return placeholder;
        }
    }
}
