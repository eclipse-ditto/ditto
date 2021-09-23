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
 * Expression for an attribute.
 *
 * @since 2.0.0
 */
public interface AttributeExpression extends FilterFieldExpression, SortFieldExpression, ExistsFieldExpression {

    /**
     * Creates an expression for an attribute.
     *
     * @param key the attribute key.
     * @return the created AttributeExpression
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    static AttributeExpression of(final String key) {
        return new AttributeExpressionImpl(key);
    }

    /**
     * Returns the attribute key.
     *
     * @return the attribute key
     */
    String getKey();

}
