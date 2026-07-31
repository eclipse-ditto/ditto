/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFieldSelector;

/**
 * A FilteredTopic wraps a {@link Topic} and optional {@code filter} Strings which additionally restrict which
 * kind of Signals should be processed/filtered. Each filter is either an {@code RQL} query or a placeholder
 * pipeline expression starting with {@code fn:}; all filters of one topic must match for a signal to be
 * processed (AND semantics).
 */
public interface FilteredTopic extends CharSequence {

    /**
     * @return the {@code Topic} of this FilteredTopic
     */
    Topic getTopic();

    /**
     * @return the namespaces for which the filter should be applied - if empty, all namespaces are considered.
     */
    List<String> getNamespaces();

    /**
     * @return the first filter string of this FilteredTopic, or an empty Optional if no filter is set.
     * @deprecated as of 3.10.0 a FilteredTopic may carry multiple filters; use {@link #getFilters()} instead.
     */
    @Deprecated
    Optional<String> getFilter();

    /**
     * Returns the filter strings of this FilteredTopic in insertion order. All filters of one topic must match
     * for a signal to be processed (AND semantics). At most one entry may be an RQL expression; any number of
     * entries may be placeholder pipeline expressions starting with {@code fn:}.
     *
     * @return the filter strings, or an empty list if no filter is set.
     * @since 3.10.0
     */
    List<String> getFilters();

    /**
     * Returns the selector for the extra fields and their values to enrich outgoing signals with.
     *
     * @return the selector or an empty Optional if signals should not be enriched.
     */
    Optional<JsonFieldSelector> getExtraFields();

    @Override
    String toString();

}
