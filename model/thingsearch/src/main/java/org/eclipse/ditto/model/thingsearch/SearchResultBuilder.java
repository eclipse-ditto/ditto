/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.thingsearch;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonValue;

/**
 * A mutable builder with a fluent API for a {@link SearchResult}.
 */
@NotThreadSafe
public interface SearchResultBuilder {

    /**
     * Set the offset of the next page if there are more matching results available or {@link
     * SearchResult#NO_NEXT_PAGE}, if there is no next page.
     *
     * @param nextPageOffset the offset of the next page.
     * @return this builder to allow method chaining.
     */
    SearchResultBuilder nextPageOffset(long nextPageOffset);

    /**
     * Adds at least one {@link JsonValue} to the {@code SearchResult} to be built.
     *
     * @param item the item to add to the results.
     * @param furtherItems additional items to be added to the array.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    SearchResultBuilder add(JsonValue item, JsonValue... furtherItems);

    /**
     * Adds all given items to the {@code SearchResult} to be built.
     *
     * @param items the items to add to the results.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code items} is {@code null}.
     */
    SearchResultBuilder addAll(Iterable<? extends JsonValue> items);

    /**
     * Removes the given item from the {@code SearchResult} to be built.
     *
     * @param item the item to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code item} is {@code null}.
     */
    SearchResultBuilder remove(JsonValue item);

    /**
     * Creates a new {@link SearchResult} object containing all items which were set to this builder beforehand.
     *
     * @return the new {@code SearchResult} object.
     */
    SearchResult build();

}
