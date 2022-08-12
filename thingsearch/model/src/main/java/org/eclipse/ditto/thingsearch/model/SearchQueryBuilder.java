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
package org.eclipse.ditto.thingsearch.model;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A mutable builder with a fluent API for an immutable {@link SearchQuery}.
 */
@NotThreadSafe
public interface SearchQueryBuilder {

    /**
     * Sets the sort order for the given property path to ascending. An already set sort order for the given path is
     * replaced. The order of the insertion is maintained.
     *
     * @param propertyPath the path of the property to set the sort option for.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    SearchQueryBuilder sortAsc(CharSequence propertyPath);

    /**
     * Sets the sort order for the given property path to descending. An already set sort order for the given path is
     * replaced. The order of the insertion is maintained.
     *
     * @param propertyPath the path of the property to set the sort option for.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    SearchQueryBuilder sortDesc(CharSequence propertyPath);

    /**
     * Removes the set sort option for the given property path.
     *
     * @param propertyPath the path of the property to remove the sort option for.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    SearchQueryBuilder removeSortOptionFor(CharSequence propertyPath);

    /**
     * Creates a new immutable {@code SearchQuery} object based on the properties of this builder.
     *
     * @return the new SearchQuery.
     */
    SearchQuery build();

}
