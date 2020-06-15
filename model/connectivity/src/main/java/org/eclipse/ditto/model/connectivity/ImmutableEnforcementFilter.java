/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.connectivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.placeholders.Placeholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;

/**
 * Immutable implementation of an {@link EnforcementFilter}.
 *
 * @param <M> the type that is required to resolve the placeholders in the filters.
 */
@Immutable
final class ImmutableEnforcementFilter<M> implements EnforcementFilter<M> {

    private final Enforcement enforcement;
    private final List<Placeholder<M>> filterPlaceholders;
    private final String inputValue;

    ImmutableEnforcementFilter(final Enforcement enforcement,
            final List<Placeholder<M>> filterPlaceholders,
            final String inputValue) {
        this.enforcement = enforcement;
        this.filterPlaceholders = Collections.unmodifiableList(new ArrayList<>(filterPlaceholders));
        this.inputValue = inputValue;
    }

    @Override
    public void match(final M filterInput, final DittoHeaders dittoHeaders) {

        final List<DittoRuntimeException> exceptions = new LinkedList<>();

        for (final Placeholder<M> filterPlaceholder : filterPlaceholders) {
            for (final String filter : enforcement.getFilters()) {
                try {
                    final String resolved = PlaceholderFilter.apply(filter, filterInput, filterPlaceholder);
                    if (inputValue.equals(resolved)) {
                        return;
                    }
                } catch (final UnresolvedPlaceholderException unresolved) {
                    exceptions.add(unresolved);
                }
            }
        }

        /*
            exceptions.size() == filterPlaceholders.size():
              the configured filter could not be resolved by any of the placeholders
                -> re-throw UnresolvedPlaceholderException
        */
        if (exceptions.size() == filterPlaceholders.size()) {
            throw exceptions.get(0);
        }

        /*
            exceptions.size() < filterPlaceholders.size()
                  at least one of the placeholder could resolve the filter but it did not match
                    -> throw ConnectionSignalIdEnforcementFailedException
         */
        throw getEnforcementFailedException(dittoHeaders);
    }

    private ConnectionSignalIdEnforcementFailedException getEnforcementFailedException(
            final DittoHeaders dittoHeaders) {
        return ConnectionSignalIdEnforcementFailedException.newBuilder(inputValue)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableEnforcementFilter<?> that = (ImmutableEnforcementFilter<?>) o;
        return Objects.equals(enforcement, that.enforcement) &&
                Objects.equals(filterPlaceholders, that.filterPlaceholders) &&
                Objects.equals(inputValue, that.inputValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enforcement, filterPlaceholders, inputValue);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enforcement=" + enforcement +
                ", filterPlaceholders=" + filterPlaceholders +
                ", inputValue=" + inputValue +
                "]";
    }
}
