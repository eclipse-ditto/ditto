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
package org.eclipse.ditto.model.connectivity;

import java.util.List;
import java.util.Optional;

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
     * @return whether this FilteredTopic has a filter to apply or not
     */
    default boolean hasFilter() {
        return getFilter().isPresent();
    }

    @Override
    String toString();

}
