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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
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
     * @return a Map of headers to keep from the passed external headers.
     * @throws NullPointerException if {@code externalHeaders} is {@code null}.
     */
    public Map<String, String> fromExternalHeaders(final Map<String, String> externalHeaders) {
        checkNotNull(externalHeaders, "externalHeaders");
        final HeaderEntryFilter headerEntryFilter = HeaderEntryFilters.fromExternalHeadersFilter(headerDefinitions);
        return filterHeaders(externalHeaders, headerEntryFilter, true);
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
        return filterHeaders(dittoHeaders, headerEntryFilter, false);
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
        return filterHeaders(externalHeaders, headerEntryFilter, true);
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
        return filterHeaders(dittoHeaders, headerEntryFilter, true);
    }

    /**
     * Build a copy of this header translator without knowledge of certain headers.
     *
     * @param headerKeys header keys to forget.
     * @return a new header translator with less knowledge.
     * @throws NullPointerException if {@code headerKeys} is {@code null}.
     * @deprecated this method will be removed in version 2.0.
     */
    @Deprecated
    public HeaderTranslator forgetHeaderKeys(final Collection<String> headerKeys) {
        checkNotNull(headerKeys, "headerKeys");
        final Map<String, HeaderDefinition> newHeaderDefinitions = new HashMap<>(headerDefinitions);
        headerKeys.forEach(newHeaderDefinitions::remove);
        return new HeaderTranslator(newHeaderDefinitions);
    }

    private static Map<String, String> filterHeaders(final Map<String, String> headersToFilter,
            final HeaderEntryFilter headerEntryFilter, final boolean lowercaseHeaderKeys) {

        if (headersToFilter instanceof DittoHeaders) {

            // performance optimization: when filtering on already built DittoHeaders, use the ".toBuilder()"
            // and remove values which were filtered out from the DittoHeadersBuilder
            // return DittoHeaders again so that based on this return value other ".toBuilder()" calls may
            // profit from the performance optimization in DittoHeaders.toBuilder() as no validation has to be done
            return filterDittoHeaders((DittoHeaders) headersToFilter, headerEntryFilter, lowercaseHeaderKeys);
        } else {
            return filterHeadersMap(headersToFilter, headerEntryFilter, lowercaseHeaderKeys);
        }
    }

    private static DittoHeaders filterDittoHeaders(final DittoHeaders headersToFilter,
            final HeaderEntryFilter headerEntryFilter, final boolean lowerCaseHeaderKeys) {

        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = headersToFilter.toBuilder();
        for (final Map.Entry<String, String> entry : headersToFilter.entrySet()) {
            final String originalKey = entry.getKey();
            final String originalValue = entry.getValue();
            final String key = lowerCaseHeaderKeys ? originalKey.toLowerCase() : originalKey;
            final String filteredValue = headerEntryFilter.apply(key, originalValue);
            if (null == filteredValue) {
                dittoHeadersBuilder.removeHeader(key);
            } else if (!filteredValue.equals(originalValue)) {
                dittoHeadersBuilder.putHeader(key, filteredValue);
            }
        }
        return dittoHeadersBuilder.build();
    }

    private static Map<String, String> filterHeadersMap(final Map<String, String> headersToFilter,
            final HeaderEntryFilter headerEntryFilter, final boolean lowerCaseHeaderKeys) {

        final Map<String, String> result = new HashMap<>(headersToFilter.size());
        for (final Map.Entry<String, String> entry : headersToFilter.entrySet()) {
            final String originalKey = entry.getKey();
            final String key = lowerCaseHeaderKeys ? originalKey.toLowerCase() : originalKey;
            final String filteredValue = headerEntryFilter.apply(key, entry.getValue());
            if (null != filteredValue) {
                result.put(key, filteredValue);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "headerKeys=" + headerDefinitions.keySet() +
                ']';
    }

}
