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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.placeholders.Placeholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;

/**
 * Immutable implementation of an {@link EnforcementFilter}.
 *
 * @param <M> the type that is required to resolve the placeholders in the filters.
 */
@Immutable
final class ImmutableEnforcementFilter<M> implements EnforcementFilter<M> {

    private final Enforcement enforcement;
    private final Placeholder<M> filterPlaceholder;
    private final String inputValue;

    ImmutableEnforcementFilter(final Enforcement enforcement,
            final Placeholder<M> filterPlaceholder, final String inputValue) {
        this.enforcement = enforcement;
        this.filterPlaceholder = filterPlaceholder;
        this.inputValue = inputValue;
    }

    @Override
    public void match(final M filterInput, final DittoHeaders dittoHeaders) {

        final boolean match = enforcement.getFilters()
                .stream()
                .map(filter -> PlaceholderFilter.apply(filter, filterInput, filterPlaceholder))
                .anyMatch(resolved -> resolved.equals(inputValue));
        if (!match) {
            throw getEnforcementFailedException(dittoHeaders);
        }
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
                Objects.equals(filterPlaceholder, that.filterPlaceholder) &&
                Objects.equals(inputValue, that.inputValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enforcement, filterPlaceholder, inputValue);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enforcement=" + enforcement +
                ", filterPlaceholder=" + filterPlaceholder +
                ", inputValue=" + inputValue +
                "]";
    }
}
