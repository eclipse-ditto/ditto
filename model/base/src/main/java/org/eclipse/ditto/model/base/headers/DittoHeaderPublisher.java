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
package org.eclipse.ditto.model.base.headers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * Default implementation of {@link HeaderPublisher} for Ditto.
 */
@Immutable
public final class DittoHeaderPublisher implements HeaderPublisher {

    private final Map<String, HeaderDefinition> headerDefinitionMap;

    private DittoHeaderPublisher(final HeaderDefinition[]... headerDefinitions) {
        this.headerDefinitionMap = Arrays.stream(headerDefinitions)
                .flatMap(Arrays::stream)
                .collect(Collectors.toMap(HeaderDefinition::getKey, Function.identity()));
    }

    /**
     * Default provider of header publisher for Ditto that knows about all header definitions enumerated in
     * {@link DittoHeaderDefinition}.
     */
    public static final class Provider implements HeaderPublisher.Provider {

        @Override
        public HeaderPublisher get() {
            return of();
        }
    }

    /**
     * Construct a Ditto header publisher that knows about all header definitions enumerated in
     * {@link DittoHeaderDefinition}.
     *
     * @return the Ditto header publisher.
     */
    public static DittoHeaderPublisher of() {
        return of(DittoHeaderDefinition.values());
    }

    /**
     * Construct a Ditto header publisher from arrays of header definitions.
     *
     * @param headerDefinitions arrays of header definitions.
     * @return the Ditto header publisher that knows about the given definitions.
     */
    public static DittoHeaderPublisher of(final HeaderDefinition[]... headerDefinitions) {
        return new DittoHeaderPublisher(headerDefinitions);
    }

    @Override
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

    @Override
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
}
