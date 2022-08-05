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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.Nullable;

/**
 * Filters the value of a particular header entry.
 * The result depends on the input.
 * If the value is invalid at all the {@link #apply(String, String)} method returns {@code null}.
 * Otherwise it may return the header value as it was provided or some derivative of the provided value.
 */
@FunctionalInterface
interface HeaderEntryFilter {

    /**
     * Applies a filtering of the header entry identified by the passed {@code key} in a way that the passed
     * {@code value} might be changed in the return value or even might be completely filtered out if the return value
     * is {@code null}.
     *
     * @param key the header entry key to apply the filtering on.
     * @param value the value to filter.
     * @return the original passed in {@code value}, an adjusted (reduced/enhanced) form of the passed in value or
     * {@code null} if the header entry should completely be filtered out.
     */
    @Nullable
    String apply(String key, @Nullable String value);

    /**
     * Returns a composed filter that first applies this filter to its input, and then applies the given filter to the
     * result.
     * If any filter throws an exception, it is relayed to the caller of the composed filter.
     *
     * @param after the filter to be applied after this filter is applied.
     * @return the composed filter.
     * @throws NullPointerException if {@code after} is {@code null}.
     */
    default HeaderEntryFilter andThen(final HeaderEntryFilter after) {
        checkNotNull(after, "after");
        return (key, value) -> after.apply(key, apply(key, value));
    }

}
