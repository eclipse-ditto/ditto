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
package org.eclipse.ditto.model.query.criteria;

import org.eclipse.ditto.model.query.criteria.visitors.PredicateVisitor;

/**
 * Interface for search predicates.
 */
public interface Predicate {

    /**
     * Evaluate this predicate according to a {@link PredicateVisitor}.
     *
     * @param visitor The visitor with which to evaluate this predicate.
     * @param <T> The result type.
     * @return The result of evaluation.
     */
    <T> T accept(final PredicateVisitor<T> visitor);
}
