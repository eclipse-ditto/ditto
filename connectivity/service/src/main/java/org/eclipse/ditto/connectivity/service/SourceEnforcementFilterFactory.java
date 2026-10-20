/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.PlaceholderResolver;

/**
 * Creates enforcement filters for messages. Depending on the configured enforcement input,
 * the input is resolved from the message headers or if it uses {@code {{ source:address }}},
 * it's resolved from the source address the message was consumed
 */
@Immutable
public final class SourceEnforcementFilterFactory {

    @Nullable private final EnforcementFilterFactory<ExpressionResolver, Signal<?>> enforcementFilterFactory;
    private final PlaceholderResolver<String> sourceAddressResolver;

    private SourceEnforcementFilterFactory(
            @Nullable final EnforcementFilterFactory<ExpressionResolver, Signal<?>> enforcementFilterFactory,
            final PlaceholderResolver<String> sourceAddressResolver) {
        this.enforcementFilterFactory = enforcementFilterFactory;
        this.sourceAddressResolver = sourceAddressResolver;
    }

    /**
     * Returns a new instance for the enforcement configured in the given source. The source address resolver is
     * created once, because the address is the same for all messages of a consumer.
     *
     * @param source the connection source containing the optional enforcement.
     * @param sourceAddress the address the consumer reads from, used to resolve {@code {{ source:address }}}.
     * @return the new instance.
     */
    public static SourceEnforcementFilterFactory of(final Source source, final String sourceAddress) {
        return new SourceEnforcementFilterFactory(
                source.getEnforcement().map(EnforcementFactoryFactory::newEnforcementFilterFactory).orElse(null),
                newSourceAddressResolver(sourceAddress));
    }

    /**
     * Creates the enforcement filter for a message received on the consumer's source address.
     *
     * @param headers the headers of the message, used to resolve {@code {{ header:<name> }}}.
     * @return the enforcement filter, or {@code null} if the source has no enforcement.
     * @throws org.eclipse.ditto.placeholders.UnresolvedPlaceholderException if the enforcement input cannot be
     * resolved, e.g. because a required header is missing.
     */
    @Nullable
    public EnforcementFilter<Signal<?>> getFilter(final Map<String, String> headers) {
        return getFilter(headers, sourceAddressResolver);
    }

    /**
     * Creates the enforcement filter for a message received on the given address, e.g. a concrete MQTT topic
     * matching a wildcard subscription.
     *
     * @param headers the headers of the message, used to resolve {@code {{ header:<name> }}}.
     * @param sourceAddress the address the message was received on, used to resolve {@code {{ source:address }}}.
     * @return the enforcement filter, or {@code null} if the source has no enforcement.
     * @throws org.eclipse.ditto.placeholders.UnresolvedPlaceholderException if the enforcement input cannot be
     * resolved, e.g. because a required header is missing.
     */
    @Nullable
    public EnforcementFilter<Signal<?>> getFilter(final Map<String, String> headers, final String sourceAddress) {
        return getFilter(headers, newSourceAddressResolver(sourceAddress));
    }

    @Nullable
    private EnforcementFilter<Signal<?>> getFilter(final Map<String, String> headers,
            final PlaceholderResolver<String> addressResolver) {
        if (enforcementFilterFactory == null) {
            return null;
        }
        return enforcementFilterFactory.getFilter(PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(PlaceholderFactory.newHeadersPlaceholder(), headers),
                addressResolver));
    }

    private static PlaceholderResolver<String> newSourceAddressResolver(final String sourceAddress) {
        return PlaceholderFactory.newPlaceholderResolver(ConnectivityPlaceholders.newSourceAddressPlaceholder(),
                sourceAddress);
    }

}
