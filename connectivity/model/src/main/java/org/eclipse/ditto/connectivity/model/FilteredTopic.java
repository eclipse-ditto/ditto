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
 * A FilteredTopic wraps a {@link Topic} and an optional {@code filter} String which additionally restricts which
 * kind of Signals should be processed/filtered based on an {@code RQL} query.
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
     * Returns the selector for the extra fields and their values to enrich outgoing signals with.
     *
     * @return the selector or an empty Optional if signals should not be enriched.
     */
    Optional<JsonFieldSelector> getExtraFields();

    @Override
    String toString();

}
