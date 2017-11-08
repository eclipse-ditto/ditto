/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.querymodel.criteria;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.PredicateVisitor;

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
