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
