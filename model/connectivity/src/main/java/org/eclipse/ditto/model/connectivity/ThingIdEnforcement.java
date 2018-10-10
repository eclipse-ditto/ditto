/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */

package org.eclipse.ditto.model.connectivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Holds the required data to apply enforcement of Thing IDs. The target can be an arbitrary String containing the ID,
 * which must match against the passed filters (which may contain placeholders like {@code {{ thing:id }}} etc.
 */
@Immutable
public final class ThingIdEnforcement {

    private final String target;
    private final Set<String> filters;

    @Nullable
    private final String errorMessage;

    private ThingIdEnforcement(final String target, final Set<String> filters, @Nullable final String errorMessage) {
        this.target = target;
        this.filters = Collections.unmodifiableSet(new HashSet<>(filters));
        this.errorMessage = errorMessage;
    }

    /**
     * Create a ThingIdEnforcement with default error message.
     *
     * @param target target of the signal to apply Thing ID enforcement.
     * @param filters filters to match the target against.
     * @return ThingIdEnforcement with default error message.
     */
    public static ThingIdEnforcement of(final String target, final Set<String> filters) {
        return new ThingIdEnforcement(target, filters, null);
    }

    /**
     * Create a copy of this object with error message set.
     *
     * @param errorMessage The error message if enforcement fails.
     * @return a copy of this object with the error message.
     */
    public ThingIdEnforcement withErrorMessage(final String errorMessage) {
        return new ThingIdEnforcement(target, filters, errorMessage);
    }

    /**
     * Retrieve the string to match against filters.
     *
     * @return the string that is supposed to match one of the filters.
     */
    public String getTarget() {
        return target;
    }

    /**
     * Retrieve filters to match the target against. Filters contain placeholders.
     *
     * @return the filters.
     */
    public Set<String> getFilters() {
        return filters;
    }

    /**
     * Return the error when ID enforcement fails.
     *
     * @param dittoHeaders Ditto headers of the signal subjected to ID enforcement.
     * @return the error.
     */
    public IdEnforcementFailedException getError(final DittoHeaders dittoHeaders) {
        final DittoRuntimeExceptionBuilder<IdEnforcementFailedException> builder;
        if (errorMessage == null) {
            builder = IdEnforcementFailedException.newBuilder(getTarget());
        } else {
            builder = IdEnforcementFailedException.newBuilder().message(errorMessage);
        }
        return builder.dittoHeaders(dittoHeaders).build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ThingIdEnforcement that = (ThingIdEnforcement) o;
        return Objects.equals(target, that.target) &&
                Objects.equals(filters, that.filters) &&
                Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, filters, errorMessage);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "target=" + target +
                ", filters=" + filters +
                ", errorMessage=" + errorMessage +
                "]";
    }
}
