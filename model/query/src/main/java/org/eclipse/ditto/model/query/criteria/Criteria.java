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


import org.eclipse.ditto.model.query.criteria.visitors.CriteriaVisitor;

/**
 * Search criteria.
 */
public interface Criteria {

    /**
     * Evaluates the search criteria by a visitor.
     *
     * @param <T> Result type of the evaluation.
     * @param visitor The visitor that performs the evaluation.
     * @return Result of the evaluation.
     */
    <T> T accept(final CriteriaVisitor<T> visitor);
}
