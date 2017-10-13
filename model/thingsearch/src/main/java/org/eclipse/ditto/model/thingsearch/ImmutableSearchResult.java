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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link SearchResult}.
 */
@Immutable
final class ImmutableSearchResult implements SearchResult {

    private final JsonArray items;
    private final long nextPageOffset;

    private ImmutableSearchResult(final JsonArray items, final long nextPageOffset) {
        this.items = checkNotNull(items, "items");
        this.nextPageOffset = nextPageOffset;
    }

    /**
     * Returns a new empty {@code ImmutableSearchResult} instance.
     *
     * @return instance.
     */
    public static SearchResult empty() {
        return of(JsonFactory.newArray(), NO_NEXT_PAGE);
    }

    /**
     * Returns a new {@code ImmutableSearchResult} instance initialized with the given items and the given next page
     * offset.
     *
     * @param items the initial items of the result.
     * @param nextPageOffset the offset of the next page.
     * @return a new {@code ImmutableSearchResult}.
     * @throws NullPointerException if {@code items} is {@code null}.
     */
    public static ImmutableSearchResult of(final JsonArray items, final long nextPageOffset) {
        return new ImmutableSearchResult(items, nextPageOffset);
    }

    /**
     * Returns a new instance of {@code ImmutableSearchResult} based on the specified JSON object.
     *
     * @param jsonObject the JSON object to be parsed.
     * @return the instance.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} cannot be parsed.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain all required data.
     */
    public static ImmutableSearchResult fromJson(final JsonObject jsonObject) {
        final JsonArray extractedItems = jsonObject.getValueOrThrow(JsonFields.ITEMS);
        final long extractedNextPageOffset = jsonObject.getValueOrThrow(JsonFields.NEXT_PAGE_OFFSET);

        return of(extractedItems, extractedNextPageOffset);
    }

    @Override
    public JsonArray getItems() {
        return items;
    }

    @Override
    public long getNextPageOffset() {
        return nextPageOffset;
    }

    @Override
    public boolean hasNextPage() {
        return nextPageOffset != NO_NEXT_PAGE;
    }

    @Override
    public int getSize() {
        return items.getSize();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public Stream<JsonValue> stream() {
        return items.stream();
    }

    @Override
    public Iterator<JsonValue> iterator() {
        return items.iterator();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder()
                .set(SearchResult.JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate)
                .set(JsonFields.ITEMS, items, predicate)
                .set(JsonFields.NEXT_PAGE_OFFSET, nextPageOffset, predicate)
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSearchResult that = (ImmutableSearchResult) o;
        return nextPageOffset == that.nextPageOffset && Objects.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, nextPageOffset);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [items=" + items + ", nextPageOffset=" + nextPageOffset + "]";
    }

}
