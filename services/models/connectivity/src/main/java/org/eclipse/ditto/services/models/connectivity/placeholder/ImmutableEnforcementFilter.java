/*
 *  Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 *  SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.connectivity.placeholder;

import java.util.Objects;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.IdEnforcementFailedException;

public final class ImmutableEnforcementFilter<M> implements EnforcementFilter<M> {

    private static final PlaceholderFilter FILTER = new PlaceholderFilter();
    private final Enforcement enforcement;
    private final Placeholder<M> matcherFilter;
    private final String inputValue;

    ImmutableEnforcementFilter(final Enforcement enforcement,
            final Placeholder<M> matcherFilter, final String inputValue) {
        this.enforcement = enforcement;
        this.matcherFilter = matcherFilter;
        this.inputValue = inputValue;
    }

    @Override
    public void match(final M matcherSource, final DittoHeaders dittoHeaders) {
        enforcement.getMatchers()
                .stream()
                .map(m -> FILTER.apply(m, matcherSource, matcherFilter))
                .filter(resolved -> resolved.equals(inputValue))
                .findFirst()
                .orElseThrow(() -> getException(dittoHeaders));
    }

    private IdEnforcementFailedException getException(
            final DittoHeaders dittoHeaders) {
        return IdEnforcementFailedException.newBuilder(inputValue).dittoHeaders(dittoHeaders).build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableEnforcementFilter<?> that = (ImmutableEnforcementFilter<?>) o;
        return Objects.equals(enforcement, that.enforcement) &&
                Objects.equals(matcherFilter, that.matcherFilter) &&
                Objects.equals(inputValue, that.inputValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enforcement, matcherFilter, inputValue);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enforcement=" + enforcement +
                ", matcherFilter=" + matcherFilter +
                ", inputValue=" + inputValue +
                "]";
    }
}
