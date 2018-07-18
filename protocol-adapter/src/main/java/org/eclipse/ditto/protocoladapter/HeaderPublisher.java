/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.protocoladapter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;

import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * Default implementation of {@link HeaderPublisher} for Ditto.
 */
@Immutable
public final class HeaderPublisher {

    private final Map<String, HeaderDefinition> headerDefinitionMap;

    private HeaderPublisher(final Map<String, HeaderDefinition> headerDefinitionMap) {
        this.headerDefinitionMap = headerDefinitionMap;
    }

    /**
     * Construct a Ditto header publisher that knows about all header definitions enumerated in
     * {@link DittoHeaderDefinition}.
     *
     * @return the Ditto header publisher.
     */
    public static HeaderPublisher of() {
        return of(DittoHeaderDefinition.values());
    }

    /**
     * Construct a Ditto header publisher from arrays of header definitions.
     *
     * @param headerDefinitions arrays of header definitions.
     * @return the Ditto header publisher that knows about the given definitions.
     */
    public static HeaderPublisher of(final HeaderDefinition[]... headerDefinitions) {
        return new HeaderPublisher(Arrays.stream(headerDefinitions)
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
        externalHeaders.forEach((key, value) -> {
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
     * Build a copy of this header publisher without knowledge of certain headers.
     *
     * @param headerKeys header keys to forget.
     * @return a new header publisher with less knowledge.
     */
    public HeaderPublisher forgetHeaderKeys(final Collection<String> headerKeys) {
        final Map<String, HeaderDefinition> newHeaderDefinitionMap = headerDefinitionMap.entrySet()
                .stream()
                .filter(entry -> !headerKeys.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new HeaderPublisher(newHeaderDefinitionMap);
    }
}
