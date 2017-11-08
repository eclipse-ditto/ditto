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
package org.eclipse.ditto.services.thingsearch.querymodel.expression;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.ExistsFieldExpressionVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.FieldExpressionVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.FilterFieldExpressionVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.PolicyRestrictedFieldExpressionVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.SortFieldExpressionVisitor;

/**
 * Expression for an attribute.
 */
public class AttributeExpressionImpl implements FilterFieldExpression, SortFieldExpression, ExistsFieldExpression,
        PolicyRestrictedFieldExpression {

    private final String key;

    /**
     * Constructor.
     *
     * @param key the attribute key
     */
    public AttributeExpressionImpl(final String key) {
        this.key = requireNonNull(key);
    }

    /**
     * @return the key
     */
    public String getKey() {
        return key;
    }

    @Override
    public <T> T acceptSortVisitor(final SortFieldExpressionVisitor<T> visitor) {
        return visitor.visitAttribute(key);
    }

    @Override
    public <T> T acceptFilterVisitor(final FilterFieldExpressionVisitor<T> visitor) {
        return visitor.visitAttribute(key);
    }

    @Override
    public <T> T acceptExistsVisitor(final ExistsFieldExpressionVisitor<T> visitor) {
        return visitor.visitAttribute(key);
    }

    @Override
    public <T> T accept(final FieldExpressionVisitor<T> visitor) {
        return visitor.visitAttribute(key);
    }

    @Override
    public <T> T acceptPolicyRestrictedVisitor(final PolicyRestrictedFieldExpressionVisitor<T> visitor) {
        return visitor.visitAttribute(key);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((key == null) ? 0 : key.hashCode());
        return result;
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AttributeExpressionImpl other = (AttributeExpressionImpl) obj;
        if (key == null) {
            if (other.key != null) {
                return false;
            }
        } else if (!key.equals(other.key)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "AttributeExpression [key=" + key + "]";
    }
}
