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
import java.util.Set;

import org.eclipse.ditto.placeholders.PlaceholderResolver;

/**
 * Placeholder resolver for group-by.
 * Resolves the group-by placeholders from the given source.
 */
public final class GroupByPlaceholderResolver implements PlaceholderResolver<Map<String, String>> {

    public static final String PREFIX = "group-by";
    private final List<String> supportedNames;
    private final Map<String, String> source;

    public GroupByPlaceholderResolver(final Set<String> supportedNames, final Map<String, String> source) {
        this.supportedNames = List.of(supportedNames.toArray(new String[0]));
        this.source = source;
    }

    @Override
    public List<String> resolveValues(final Map<String, String> placeholderSource, final String name) {
        return Optional.ofNullable(placeholderSource.get(name)).map(List::of).orElse(List.of());
    }

    @Override
    public List<Map<String, String>> getPlaceholderSources() {
        return List.of(source);
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
