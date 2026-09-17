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
 * A FilteredTopic wraps a {@link Topic} and optional query parameters which additionally restrict which kind of
 * Signals should be processed/filtered: an optional {@code filter} holding an {@code RQL} expression and an optional
 * {@code fn-filter} - which may be repeated - holding a placeholder pipeline expression. All given filters must
 * match (AND semantics).
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
     * @return the optional filter string as RQL query
     */
    Optional<String> getFilter();

    /**
     * Returns the placeholder pipeline expressions of this FilteredTopic, given via the repeatable {@code fn-filter}
     * query parameter, in the order they were given. Each expression starts with the placeholder whose value is
     * filtered and ends with its only {@code fn:filter} stage (e.g.
     * {@code header:ditto-originator|fn:filter('ne','some:subject')}); the topic is only published for a signal if
     * the pipeline of <em>every</em> expression resolves to a value (AND semantics).
     *
     * @return an unmodifiable list of the placeholder pipeline expressions, empty if no {@code fn-filter} is set.
     * @since 3.10.0
     */
    List<String> getFnFilters();

    /**
     * Returns the selector for the extra fields and their values to enrich outgoing signals with.
     *
     * @return the selector or an empty Optional if signals should not be enriched.
     */
    Optional<JsonFieldSelector> getExtraFields();

    @Override
    String toString();

}
