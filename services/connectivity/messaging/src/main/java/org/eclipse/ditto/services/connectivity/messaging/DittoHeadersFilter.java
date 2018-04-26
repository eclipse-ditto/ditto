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

package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * A filter implementation to remove headers from existing {@link DittoHeaders}.
 */
final class DittoHeadersFilter implements Function<DittoHeaders, DittoHeaders> {

    /**
     * A simple enum describing the filter mode
     */
    public enum Mode {
        EXCLUDE,
        INCLUDE
    }

    private final Mode mode;
    private final Set<String> headerNames;

    /**
     * Creates a new filter
     * @param mode the filter mode
     * @param headerNames the header names
     */
    DittoHeadersFilter(final Mode mode, final String... headerNames) {
         this(mode, Arrays.asList(checkNotNull(headerNames, "HeaderNames")));
    }

    /**
     * Creates a new filter
     * @param mode the filter mode
     * @param headerNames the header names
     */
    DittoHeadersFilter(final Mode mode, final Collection<String> headerNames) {
         this.mode = checkNotNull(mode, "Mode");
         this.headerNames = Collections.unmodifiableSet(new HashSet<>(checkNotNull(headerNames, "HeaderNames")));
    }

    /**
     * Apply this filter to {@link DittoHeaders}. This will create a copy of the headers .
     * @param dittoHeaders the ditto headers that should be filtered
     * @return the filtered ditto headers
     */
    public DittoHeaders apply(final DittoHeaders dittoHeaders) {
        return DittoHeaders.of(dittoHeaders.entrySet().stream()
                .filter(e -> filterPredicate(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private boolean filterPredicate(final String headerName) {
        switch (mode) {
            case EXCLUDE:
                return !headerNames.contains(headerName);
            case INCLUDE:
                return headerNames.contains(headerName);
            default:
                throw new IllegalStateException("Mode '" + mode + "' not supported.");
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DittoHeadersFilter that = (DittoHeadersFilter) o;
        return mode == that.mode &&
                Objects.equals(headerNames, that.headerNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, headerNames);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mode=" + mode +
                ", headerNames=" + headerNames +
                "]";
    }
}
