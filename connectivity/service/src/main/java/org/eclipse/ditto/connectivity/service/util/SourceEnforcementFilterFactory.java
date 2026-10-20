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

package org.eclipse.ditto.connectivity.service.util;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.Source;

import javax.annotation.Nullable;

import java.util.Map;
import java.util.Optional;

import static org.eclipse.ditto.connectivity.service.EnforcementFactoryFactory.newEnforcementFilterFactory;
import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newSourceAddressPlaceholder;
import static org.eclipse.ditto.placeholders.PlaceholderFactory.newHeadersPlaceholder;

/**
 * Creates enforcement filters for messages. Depending on the configured enforcement input,
 * the input is resolved from the message headers or if it uses {@code {{ source:address }}},
 * it's resolved from the source address the message was consumed
 */
public final class SourceEnforcementFilterFactory {

    @Nullable private final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerFilterFactory;
    @Nullable private final EnforcementFilterFactory<String, Signal<?>> sourceAddressFilterFactory;

    private SourceEnforcementFilterFactory(
            @Nullable final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerFilterFactory,
            @Nullable final EnforcementFilterFactory<String, Signal<?>> sourceAddressFilterFactory) {
        this.headerFilterFactory = headerFilterFactory;
        this.sourceAddressFilterFactory = sourceAddressFilterFactory;
    }

    public static SourceEnforcementFilterFactory of(final Source source) {
        final Enforcement enforcement = source.getEnforcement().orElse(null);
        if (enforcement == null) {
            return new SourceEnforcementFilterFactory(null, null);
        } else if (usesSourceAddress(enforcement)) {
            return new SourceEnforcementFilterFactory(null,
                    newEnforcementFilterFactory(enforcement, newSourceAddressPlaceholder()));
        } else {
            return new SourceEnforcementFilterFactory(newEnforcementFilterFactory(enforcement, newHeadersPlaceholder()), null);
        }
    }

    private static boolean usesSourceAddress(final Enforcement enforcement) {
        return enforcement.getInput().contains(ConnectivityModelFactory.SOURCE_ADDRESS_ENFORCEMENT);
    }

    public Optional<EnforcementFilter<Signal<?>>> getFilter(final Map<String, String> headers, final String sourceAddress) {
        if (headerFilterFactory != null) {
            return Optional.ofNullable(headerFilterFactory.getFilter(headers));
        } else if (sourceAddressFilterFactory != null) {
            return Optional.ofNullable(sourceAddressFilterFactory.getFilter(sourceAddress));
        } else {
            return Optional.empty();
        }
    }

}
