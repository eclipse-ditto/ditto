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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link SearchResult}.
 */
@Immutable
final class ImmutableSearchResult implements SearchResult {

    private final JsonArray items;
    @Nullable private final Long nextPageOffset;
    @Nullable private final String cursor;

    private ImmutableSearchResult(final JsonArray items, @Nullable final Long nextPageOffset,
            @Nullable final String cursor) {
        this.items = checkNotNull(items, "items");
        this.nextPageOffset = nextPageOffset;
        this.cursor = cursor;
    }

    /**
     * Returns a new empty {@code ImmutableSearchResult} instance.
     *
     * @return instance.
     */
    public static SearchResult empty() {
        return of(JsonFactory.newArray(), NO_NEXT_PAGE, null);
    }

    /**
     * Returns a new {@code ImmutableSearchResult} instance initialized with the given items, next-page-offset and
     * cursor.
     *
     * @param items the search result items.
     * @param nextPageOffset the next-page-offset.
     * @param cursor the cursor.
     * @return a new {@code ImmutableSearchResult}.
     */
    public static ImmutableSearchResult of(final JsonArray items,
            @Nullable final Long nextPageOffset,
            @Nullable final String cursor) {

        return new ImmutableSearchResult(items, nextPageOffset, cursor);
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
        final Long extractedNextPageOffset = jsonObject.getValue(JsonFields.NEXT_PAGE_OFFSET).orElse(null);
        final String extractedCursor = jsonObject.getValue(JsonFields.CURSOR).orElse(null);

        return of(extractedItems, extractedNextPageOffset, extractedCursor);
    }

    @Override
    public JsonArray getItems() {
        return items;
    }

    @Override
    public Optional<Long> getNextPageOffset() {
        return Optional.ofNullable(nextPageOffset);
    }

    @Override
    public Optional<String> getCursor() {
        return Optional.ofNullable(cursor);
    }

    @Override
    public boolean hasNextPage() {
        return cursor != null || nextPageOffset != null && nextPageOffset != NO_NEXT_PAGE;
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

        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFields.ITEMS, items, predicate);
        getNextPageOffset().ifPresent(offset -> builder.set(JsonFields.NEXT_PAGE_OFFSET, offset, predicate));
        getCursor().ifPresent(cur -> builder.set(JsonFields.CURSOR, cur, predicate));
        return builder.build();
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
        return Objects.equals(nextPageOffset, that.nextPageOffset) &&
                Objects.equals(cursor, that.cursor) &&
                Objects.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, nextPageOffset, cursor);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [items=" + items +
                ", nextPageOffset=" + nextPageOffset +
                ", cursor=" + cursor +
                "]";
    }

}
