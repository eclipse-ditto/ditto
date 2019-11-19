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
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.Placeholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderResolver;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Creator of expression resolvers for incoming and outgoing messages.
 */
public final class Resolvers {

    /**
     * Placeholder resolver creators for incoming and outgoing messages.
     */
    private static final List<ResolverCreator<?>> RESOLVER_CREATORS = Arrays.asList(
            // For incoming messages, header mapping injects headers of external messages into Ditto headers.
            ResolverCreator.of(PlaceholderFactory.newHeadersPlaceholder(), (e, s, t, a) -> e),
            ResolverCreator.of(PlaceholderFactory.newThingPlaceholder(), (e, s, t, a) -> s.getEntityId()),
            ResolverCreator.of(PlaceholderFactory.newTopicPathPlaceholder(), (e, s, t, a) -> t),
            ResolverCreator.of(PlaceholderFactory.newRequestPlaceholder(), (e, s, t, a) -> a)
    );

    /**
     * Array of all placeholders for target address and source/target header mappings.
     * MUST be equal to *both* the placeholders in INCOMING_CREATORS *and* those in OUTGOING_CREATORS.
     */
    public static final Placeholder[] PLACEHOLDERS = RESOLVER_CREATORS.stream()
            .map(ResolverCreator::getPlaceholder)
            .toArray(Placeholder[]::new);

    /**
     * Create an expression resolver for an outbound message.
     *
     * @param mappedOutboundSignal the mapped external message.
     * @return the expression resolver.
     */
    public static ExpressionResolver forOutbound(final OutboundSignal.Mapped mappedOutboundSignal) {
        final Signal signal = mappedOutboundSignal.getSource();
        final ExternalMessage externalMessage = mappedOutboundSignal.getExternalMessage();
        final Adaptable adaptable = mappedOutboundSignal.getAdaptable();
        return PlaceholderFactory.newExpressionResolver(
                RESOLVER_CREATORS.stream()
                        .map(creator -> creator.create(adaptable.getHeaders().orElse(DittoHeaders.empty()), signal,
                                externalMessage.getTopicPath().orElse(null),
                                signal.getDittoHeaders().getAuthorizationContext()))
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
     * @return the expression resolver.
     */
    public static ExpressionResolver forInbound(final ExternalMessage externalMessage, final Signal signal,
            @Nullable final TopicPath topicPath, @Nullable final AuthorizationContext authorizationContext) {
        return PlaceholderFactory.newExpressionResolver(
                RESOLVER_CREATORS.stream()
                        .map(creator ->
                                creator.create(externalMessage.getHeaders(), signal, topicPath, authorizationContext))
                        .toArray(PlaceholderResolver[]::new)
        );
    }

    /**
     * Extract data for a placeholder.
     *
     * @param <T> the data required by the placeholder.
     */
    @FunctionalInterface
    private interface ResolverDataExtractor<T> {

        @Nullable
        T extract(final Map<String, String> inputHeaders, final Signal signal, @Nullable final TopicPath topicPath,
                @Nullable final AuthorizationContext authorizationContext);
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

        private PlaceholderResolver<T> create(final Map<String, String> inputHeaders, final Signal signal,
                @Nullable final TopicPath topicPath, @Nullable final AuthorizationContext authorizationContext) {
            return PlaceholderFactory.newPlaceholderResolver(placeholder,
                    dataExtractor.extract(inputHeaders, signal, topicPath, authorizationContext));
        }

        private Placeholder<T> getPlaceholder() {
            return placeholder;
        }
    }
}
