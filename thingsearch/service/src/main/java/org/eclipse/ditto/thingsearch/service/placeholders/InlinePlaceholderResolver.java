/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.thingsearch.service.placeholders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.placeholders.PlaceholderResolver;

/**
 * Placeholder resolver for inline.
 * Resolves the inline placeholders from the given source.
 */
public final class InlinePlaceholderResolver implements PlaceholderResolver<Map<String, String>> {

    public static final String PREFIX = "inline";
    private final Map<String, String> source;
    private final List<String> supportedNames;

    public InlinePlaceholderResolver(final Map<String, String> source) {
        this.source = source;
        this.supportedNames = source.keySet().stream().toList();
    }

    @Override
    public List<Map<String, String>> getPlaceholderSources() {
        return List.of(source);
    }

    @Override
    public List<String> resolveValues(final Map<String, String> placeholderSource, final String name) {
        return Optional.ofNullable(placeholderSource.get(name)).map(List::of).orElse(List.of());
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public List<String> getSupportedNames() {
        return supportedNames;
    }

    @Override
    public boolean supports(final String name) {
        return supportedNames.contains(name);
    }
}
