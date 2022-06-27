/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers.translator;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;

/**
 * Utility for translating Headers from external sources or to external sources.
 * <p>
 * Does so by applying blocking based on {@link HeaderDefinition}s.
 * </p>
 * @since 3.0.0
 */
@Immutable
public final class HeaderTranslator {

    private final Map<String, HeaderDefinition> headerDefinitions;

    private HeaderTranslator(final Map<String, HeaderDefinition> headerDefinitionMap) {
        headerDefinitions = Collections.unmodifiableMap(headerDefinitionMap);
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
                .collect(Collectors.toMap(HeaderDefinition::getKey, Function.identity(),
                        (headerDefinition, headerDefinition2) -> {
                            if (Objects.equals(headerDefinition.getKey(), DittoHeaderDefinition.TIMEOUT.getKey())) {
                                // special treatment for "timeout" as this was copied from MessageHeaderDefinition
                                //  to DittoHeaderDefinition - and the latter (having SerializationType String) shall
                                //  have priority when merging:
                                return headerDefinition.getSerializationType().equals(String.class) ?
                                        headerDefinition : headerDefinition2;
                            } else {
                                throw new IllegalStateException("Duplicate key: " + headerDefinition.getKey());
                            }
                        })));
    }

    /**
     * Read Ditto headers from external headers.
     *
     * @param externalHeaders external headers as a map.
     * @return an unmodifiable Map of headers to keep from the passed external headers.
     * @throws NullPointerException if {@code externalHeaders} is {@code null}.
     */
    public Map<String, String> fromExternalHeaders(final Map<String, String> externalHeaders) {
        checkNotNull(externalHeaders, "externalHeaders");
        final HeaderEntryFilter headerEntryFilter = HeaderEntryFilters.fromExternalHeadersFilter(headerDefinitions);
        return filterHeaders(externalHeaders, headerEntryFilter);
    }

    /**
     * Publish Ditto headers to external headers.
     *
     * @param dittoHeaders Ditto headers to publish.
     * @return external headers.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public Map<String, String> toExternalHeaders(final DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "dittoHeaders");
        final HeaderEntryFilter headerEntryFilter = HeaderEntryFilters.toExternalHeadersFilter(headerDefinitions);
        return filterHeadersMap(dittoHeaders.asCaseSensitiveMap(), headerEntryFilter);
    }

    /**
     * Retain only header fields which are known to this HeaderTranslator instance based on the configured
     * {@code headerDefinitions}.
     *
     * @param externalHeaders external headers as a map.
     * @return a Map of headers to retain from the passed external headers.
     * @throws NullPointerException if {@code externalHeaders} is {@code null}.
     * @since 1.1.0
     */
    public Map<String, String> retainKnownHeaders(final Map<String, String> externalHeaders) {
        checkNotNull(externalHeaders, "externalHeaders");
        final HeaderEntryFilter headerEntryFilter = HeaderEntryFilters.existsAsHeaderDefinition(headerDefinitions);
        return filterHeaders(externalHeaders, headerEntryFilter);
    }

    /**
     * Publish Ditto headers to external headers and filter out Ditto unknown headers.
     * A combination of {@link #toExternalHeaders(DittoHeaders)} and {@link #retainKnownHeaders(Map)}.
     *
     * @param dittoHeaders Ditto headers to publish.
     * @return external headers.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @since 1.1.0
     */
    public Map<String, String> toExternalAndRetainKnownHeaders(final DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "dittoHeaders");
        if (headerDefinitions.isEmpty()) {
            return dittoHeaders;
        }
        final HeaderEntryFilter headerEntryFilter = HeaderEntryFilters
                .existsAsHeaderDefinitionAndExternal(headerDefinitions);
        return filterHeaders(dittoHeaders, headerEntryFilter);
    }

    private static Map<String, String> filterHeaders(final Map<String, String> headersToFilter,
            final HeaderEntryFilter headerEntryFilter) {

        if (headersToFilter instanceof DittoHeaders) {

            // performance optimization: when filtering on already built DittoHeaders, use the ".toBuilder()"
            // and remove values which were filtered out from the DittoHeadersBuilder
            // return DittoHeaders again so that based on this return value other ".toBuilder()" calls may
            // profit from the performance optimization in DittoHeaders.toBuilder() as no validation has to be done
            return filterDittoHeaders((DittoHeaders) headersToFilter, headerEntryFilter);
        } else {
            return filterHeadersMap(headersToFilter, headerEntryFilter);
        }
    }

    private static DittoHeaders filterDittoHeaders(final DittoHeaders headersToFilter,
            final HeaderEntryFilter headerEntryFilter) {

        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = headersToFilter.toBuilder();
        for (final Map.Entry<String, String> entry : headersToFilter.asCaseSensitiveMap().entrySet()) {
            final String originalKey = entry.getKey();
            final String originalValue = entry.getValue();
            final String key = originalKey.toLowerCase();
            final String filteredValue = headerEntryFilter.apply(key, originalValue);
            if (null == filteredValue) {
                dittoHeadersBuilder.removeHeader(originalKey);
            } else if (!filteredValue.equals(originalValue)) {
                dittoHeadersBuilder.putHeader(originalKey, filteredValue);
            }
        }
        return dittoHeadersBuilder.build();
    }

    private static Map<String, String> filterHeadersMap(final Map<String, String> headersToFilter,
            final HeaderEntryFilter headerEntryFilter) {

        final Map<String, String> result = new LinkedHashMap<>(headersToFilter.size());
        headersToFilter.forEach((originalKey, value) -> {
            final String lowercaseKey = originalKey.toLowerCase();
            final String filteredValue = headerEntryFilter.apply(lowercaseKey, value);
            if (null != filteredValue) {
                result.put(originalKey, filteredValue);
            }
        });
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "headerKeys=" + headerDefinitions.keySet() +
                ']';
    }

}
