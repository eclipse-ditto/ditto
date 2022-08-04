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

import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.HeaderDefinition;

/**
 * This factory provides various {@link HeaderEntryFilter}s both atomic ones and composed ones.
 */
@Immutable
final class HeaderEntryFilters {

    private HeaderEntryFilters() {
        throw new AssertionError();
    }

    /**
     * Creates a {@code HeaderEntryFilter} which will
     * <ul>
     * <li>filter out header entries which should not be written to external headers as specified by
     * {@link HeaderDefinition#shouldWriteToExternalHeaders()} of the entry</li>
     * <li>discard {@code AcknowledgementRequest} header entries which are internal to Ditto as defined in
     * {@link org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel}</li>
     * </ul>
     *
     * @param headerDefinitionMap the header definitions for creating the filter used to determining whether a header
     * entry will be filtered or adjusted accordingly.
     * @return the created HeaderEntryFilter.
     * @throws NullPointerException if {@code headerDefinitionMap} is {@code null}.
     * @see CheckExternalFilter#shouldWriteToExternal(Map)
     * @see DittoAckRequestsFilter#getInstance()
     */
    static HeaderEntryFilter toExternalHeadersFilter(final Map<String, HeaderDefinition> headerDefinitionMap) {
        final HeaderEntryFilter headerEntryFilter = shouldWriteToExternal(headerDefinitionMap);
        if (headerDefinitionMap.isEmpty()) {
            return headerEntryFilter;
        } else {
            // only discard Ditto ack request if any headerDefinitions were set - e.g. that is case for the Ditto backend
            //  the Ditto client should not discard ack requests
            return headerEntryFilter.andThen(discardDittoAckRequests());
        }
    }

    private static HeaderEntryFilter shouldWriteToExternal(final Map<String, HeaderDefinition> headerDefinitions) {
        return CheckExternalFilter.shouldWriteToExternal(headerDefinitions);
    }

    private static HeaderEntryFilter readJsonArraysFromHeaders(final Map<String, HeaderDefinition> headerDefinitions) {
        return ReadJsonArrayHeadersFilter.getInstance(headerDefinitions);
    }

    private static HeaderEntryFilter discardDittoAckRequests() {
        return DittoAckRequestsFilter.getInstance();
    }

    /**
     * Creates a {@code HeaderEntryFilter} which will filter out header entries which should not be read from external
     * headers as specified by {@link HeaderDefinition#shouldReadFromExternalHeaders()} of the entry.
     *
     * @param headerDefinitionMap the header definitions for determining whether a header entry may be read from
     * external headers or not.
     * @return the created HeaderEntryFilter.
     * @throws NullPointerException if {@code headerDefinitionMap} is {@code null}.
     * @see HeaderDefinition#shouldReadFromExternalHeaders()
     */
    static HeaderEntryFilter fromExternalHeadersFilter(final Map<String, HeaderDefinition> headerDefinitionMap) {
        return CheckExternalFilter.shouldReadFromExternal(headerDefinitionMap)
                .andThen(readJsonArraysFromHeaders(headerDefinitionMap));
    }

    /**
     * Creates a {@code HeaderEntryFilter} which will filter out header entries which do not exist as defined
     * {@link HeaderDefinition} (e.g. user defined headers).
     *
     * @param headerDefinitionMap the header definitions for determining whether a header entry is defined by a
     * HeaderDefinition or not.
     * @return the created HeaderEntryFilter.
     * @throws NullPointerException if {@code headerDefinitionMap} is {@code null}.
     * @since 1.1.0
     */
    static HeaderEntryFilter existsAsHeaderDefinition(final Map<String, HeaderDefinition> headerDefinitionMap) {
        return CheckExternalFilter.existsAsHeaderDefinition(headerDefinitionMap);
    }

    /**
     * Creates a {@code HeaderEntryFilter} which will
     * <ul>
     * <li>filter out header entries which do not exist as defined {@link HeaderDefinition} (e.g. user defined headers)
     * and filters out only.</li>
     * <li>filter out header entries which should not be written to external headers as specified by
     * {@link HeaderDefinition#shouldWriteToExternalHeaders()} of the entry</li>
     * </ul>
     *
     * @param headerDefinitionMap the header definitions for determining whether a header entry is defined by a
     * HeaderDefinition or not.
     * @return the created HeaderEntryFilter.
     * @throws NullPointerException if {@code headerDefinitionMap} is {@code null}.
     * @since 1.1.0
     */
    static HeaderEntryFilter existsAsHeaderDefinitionAndExternal(final Map<String, HeaderDefinition> headerDefinitionMap) {
        return existsAsHeaderDefinition(headerDefinitionMap)
                .andThen(toExternalHeadersFilter(headerDefinitionMap));
    }

}
