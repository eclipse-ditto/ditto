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

import java.util.List;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.PredicateVisitor;

/**
 * In predicate.
 */
public class InPredicateImpl extends AbstractMultiPredicate {

    public InPredicateImpl(final List<?> values) {
        super(values);
    }

    @Override
    public <T> T accept(final PredicateVisitor<T> visitor) {
        return visitor.visitIn(getValues());
    }
}
