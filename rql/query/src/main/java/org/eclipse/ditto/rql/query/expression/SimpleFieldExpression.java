/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.rql.query.expression;

/**
 * Expression for a simple field.
 *
 * @since 2.0.0
 */
public interface SimpleFieldExpression extends FilterFieldExpression, SortFieldExpression, ExistsFieldExpression {

    /**
     * Creates an expression for a simple field.
     *
     * @param fieldName the field name.
     * @return the created SimpleFieldExpression
     * @throws NullPointerException if {@code fieldName} is {@code null}.
     */
    static SimpleFieldExpression of(final String fieldName) {
        return new SimpleFieldExpressionImpl(fieldName);
    }

    /**
     * @return the field name.
     */
    String getFieldName();

}
