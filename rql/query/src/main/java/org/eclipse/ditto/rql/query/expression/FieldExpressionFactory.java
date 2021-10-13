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
package org.eclipse.ditto.rql.query.expression;

/**
 * Factory to retrieve {@link FieldExpression}s.
 */
public interface FieldExpressionFactory {

    /**
     * Retrieve the persistence specific field expression for filtering according to the given {@code propertyName}.
     *
     * @param propertyName the property name of the field
     * @return the filter field expression
     * @throws IllegalArgumentException if there is no corresponding field to the given {@code propertyName}.
     */
    FilterFieldExpression filterBy(String propertyName) throws IllegalArgumentException;

    /**
     * Retrieve the persistence specific field expression for checking the existence according to the given {@code
     * propertyName}.
     *
     * @param propertyName the property name of the field
     * @return the exists field expression
     * @throws IllegalArgumentException if there is no corresponding field to the given {@code propertyName}.
     */
    ExistsFieldExpression existsBy(String propertyName) throws IllegalArgumentException;

    /**
     * Retrieve the persistence specific field expression for sorting according to the given {@code propertyName}.
     *
     * @param propertyName the property name of the field
     * @return the sort field expression
     * @throws IllegalArgumentException if there is no corresponding field to the given {@code propertyName}.
     */
    SortFieldExpression sortBy(String propertyName) throws IllegalArgumentException;

}
