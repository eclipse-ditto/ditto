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
package org.eclipse.ditto.model.thingsearch;

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
     * Sets a limit for pagination. The maximum allowed count is {@code 200}.
     *
     * @param offset determines, if a search result contains multiple results, from which entry the result entries are
     * returned. This makes it possible to skip unwanted entries.
     * @param count determines, if a search result contains multiple results, how many entries are returned.
     * @return this builder to allow method chaining.
     * @throws IllegalArgumentException if any argument is negative or if {@code count} is zero or greater than {@code
     * 200}.
     */
    SearchQueryBuilder limit(int offset, int count);

    /**
     * Removes a previously set limit for pagination.
     *
     * @return this builder to allow method chaining.
     */
    SearchQueryBuilder removeLimitation();

    /**
     * Creates a new immutable {@code SearchQuery} object based on the properties of this builder.
     *
     * @return the new SearchQuery.
     */
    SearchQuery build();

}
