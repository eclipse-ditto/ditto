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

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.ExistsFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.CriteriaVisitor;

/**
 * Criteria implementation which can handle arbitrary field existence filters.
 */
public class ExistsCriteriaImpl implements Criteria {

    private final ExistsFieldExpression fieldExpression;

    public ExistsCriteriaImpl(final ExistsFieldExpression fieldExpression) {
        this.fieldExpression = requireNonNull(fieldExpression);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExistsCriteriaImpl that = (ExistsCriteriaImpl) o;
        return Objects.equals(fieldExpression, that.fieldExpression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fieldExpression);
    }

    @Override
    public <T> T accept(final CriteriaVisitor<T> visitor) {
        return visitor.visitExists(fieldExpression);
    }
}
