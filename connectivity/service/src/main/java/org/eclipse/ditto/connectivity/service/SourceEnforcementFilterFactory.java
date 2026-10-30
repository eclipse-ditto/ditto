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

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.PlaceholderFilter;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Creates enforcement filters for messages. Depending on the configured enforcement input,
 * the input is resolved from the message headers or if it uses {@code {{ source:address }}},
 * it's resolved from the source address the message was consumed
 */
public final class SourceEnforcementFilterFactory {

    @Nullable private final Enforcement enforcement;

    private static final List<Placeholder<EntityId>> FILTER_PLACEHOLDERS = List.of(
            ConnectivityPlaceholders.newThingPlaceholder(),
            ConnectivityPlaceholders.newPolicyPlaceholder(),
            ConnectivityPlaceholders.newEntityPlaceholder());

    private SourceEnforcementFilterFactory(@Nullable final Enforcement enforcement) {
        this.enforcement = enforcement;
    }

    public static SourceEnforcementFilterFactory of(final Source source) {
        return new SourceEnforcementFilterFactory(source.getEnforcement().orElse(null));
    }

    public @Nullable EnforcementFilter<Signal<?>> getFilter(final Map<String, String> headers,
            final String sourceAddress) {
        if (enforcement == null) {
            return null;
        }

        final ExpressionResolver inputResolver = PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newPlaceholderResolver(PlaceholderFactory.newHeadersPlaceholder(), headers),
                PlaceholderFactory.newPlaceholderResolver(ConnectivityPlaceholders.newSourceAddressPlaceholder(),
                        sourceAddress));
        final String resolvedInput = PlaceholderFilter.apply(enforcement.getInput(), inputResolver);
        return new SignalEnforcementFilter(enforcement, FILTER_PLACEHOLDERS, resolvedInput);
    }

}
