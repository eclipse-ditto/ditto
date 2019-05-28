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
package org.eclipse.ditto.model.query.expression;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import org.eclipse.ditto.model.query.expression.visitors.ExistsFieldExpressionVisitor;
import org.eclipse.ditto.model.query.expression.visitors.FieldExpressionVisitor;
import org.eclipse.ditto.model.query.expression.visitors.FilterFieldExpressionVisitor;
import org.eclipse.ditto.model.query.expression.visitors.SortFieldExpressionVisitor;

/**
 * Expression for an attribute.
 */
public class AttributeExpressionImpl implements FilterFieldExpression, SortFieldExpression, ExistsFieldExpression {

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
    public int hashCode() {
        return Objects.hash(key);
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
