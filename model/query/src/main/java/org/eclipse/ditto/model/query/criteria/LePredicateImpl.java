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
package org.eclipse.ditto.model.query.criteria;

import org.eclipse.ditto.model.query.criteria.visitors.PredicateVisitor;

/**
 * Lower than or equals predicate.
 */
public class LePredicateImpl extends AbstractSinglePredicate {

    public LePredicateImpl(final Object value) {
        super(value);
    }

    @Override
    public <T> T accept(final PredicateVisitor<T> visitor) {
        return visitor.visitLe(getValue());
    }
}
