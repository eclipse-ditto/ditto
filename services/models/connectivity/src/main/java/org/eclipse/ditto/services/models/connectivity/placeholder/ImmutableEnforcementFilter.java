/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.connectivity.placeholder;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.IdEnforcementFailedException;

/**
 * Immutable implementation of an {@link EnforcementFilter}.
 *
 * @param <M> the type that is required to resolve the placeholders in the filters.
 */
@Immutable
public final class ImmutableEnforcementFilter<M> implements EnforcementFilter<M> {

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
        enforcement.getFilters()
                .stream()
                .map(m -> PlaceholderFilter.apply(m, filterInput, filterPlaceholder))
                .filter(resolved -> resolved.equals(inputValue))
                .findFirst()
                .orElseThrow(() -> getIdEnforcementFailedException(dittoHeaders));
    }

    private IdEnforcementFailedException getIdEnforcementFailedException(
            final DittoHeaders dittoHeaders) {
        return IdEnforcementFailedException.newBuilder(inputValue).dittoHeaders(dittoHeaders).build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
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
