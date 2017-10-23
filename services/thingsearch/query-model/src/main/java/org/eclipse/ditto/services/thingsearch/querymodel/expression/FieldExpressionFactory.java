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
    ExistsFieldExpression existsBy(final String propertyName) throws IllegalArgumentException;

    /**
     * Retrieve the persistence specific field expression for sorting according to the given {@code propertyName}.
     *
     * @param propertyName the property name of the field
     * @return the sort field expression
     * @throws IllegalArgumentException if there is no corresponding field to the given {@code propertyName}.
     */
    SortFieldExpression sortBy(String propertyName) throws IllegalArgumentException;
}
