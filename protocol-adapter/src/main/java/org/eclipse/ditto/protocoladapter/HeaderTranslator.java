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
package org.eclipse.ditto.protocoladapter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;

/**
 * Utility for translating Headers from external sources or to external sources.
 * <p>
 * Does so by applying blacklisting based on {@link HeaderDefinition}s.
 * </p>
 */
@Immutable
public final class HeaderTranslator {

    private final Set<String> headerKeys;
    private final Map<String, HeaderDefinition> headerDefinitionMap;

    private HeaderTranslator(final Map<String, HeaderDefinition> headerDefinitionMap) {
        this.headerDefinitionMap = Collections.unmodifiableMap(headerDefinitionMap);
        this.headerKeys = headerDefinitionMap.keySet();
    }

    /**
     * Construct a Ditto header translator that knows about nothing.
     *
     * @return the Ditto header translator.
     */
    public static HeaderTranslator empty() {
        return new HeaderTranslator(Collections.emptyMap());
    }

    /**
     * Construct a Ditto header translator from arrays of header definitions.
     *
     * @param headerDefinitions arrays of header definitions.
     * @return the Ditto header translator that knows about the given definitions.
     */
    public static HeaderTranslator of(final HeaderDefinition[]... headerDefinitions) {
        return new HeaderTranslator(Arrays.stream(headerDefinitions)
                .flatMap(Arrays::stream)
                .collect(Collectors.toMap(HeaderDefinition::getKey, Function.identity())));
    }

    /**
     * Read Ditto headers from external headers.
     *
     * @param externalHeaders external headers as a map.
     * @return Ditto headers initialized with values from external headers.
     */
    public DittoHeaders fromExternalHeaders(final Map<String, String> externalHeaders) {
        final DittoHeadersBuilder builder = DittoHeaders.newBuilder();
        externalHeaders.forEach((externalKey, value) -> {
            if (value == null) {
                return;
            }
            final String key = externalKey.toLowerCase();
            final HeaderDefinition definition = headerDefinitionMap.get(key);
            if (definition == null || definition.shouldReadFromExternalHeaders()) {
                builder.putHeader(key, value);
            }
        });
        return builder.build();
    }

    /**
     * Publish Ditto headers to external headers.
     *
     * @param dittoHeaders Ditto headers to publish.
     * @return external headers.
     */
    public Map<String, String> toExternalHeaders(final DittoHeaders dittoHeaders) {
        final Map<String, String> headers = new HashMap<>();
        dittoHeaders.forEach((key, value) -> {
            final HeaderDefinition definition = headerDefinitionMap.get(key);
            if (definition == null || definition.shouldWriteToExternalHeaders()) {
                headers.put(key, value);
            }
        });
        return headers;
    }

    /**
     * Build a copy of this header translator without knowledge of certain headers.
     *
     * @param headerKeys header keys to forget.
     * @return a new header translator with less knowledge.
     */
    public HeaderTranslator forgetHeaderKeys(final Collection<String> headerKeys) {
        final Map<String, HeaderDefinition> newHeaderDefinitionMap = headerDefinitionMap.entrySet()
                .stream()
                .filter(entry -> !headerKeys.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new HeaderTranslator(newHeaderDefinitionMap);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "headerKeys=" + headerKeys +
                ']';
    }
}
