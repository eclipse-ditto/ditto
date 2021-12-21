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

import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * This entity represents the results of a search query. The found items are returned as JSON array containing the
 * matching Things. The next page offset property indicates, if there are more results available.
 */
@Immutable
public interface SearchResult extends Iterable<JsonValue>, Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Signals that there is no next page.
     */
    long NO_NEXT_PAGE = -1;

    /**
     * Returns a new builder with a fluent API for a {@code SearchResult}.
     *
     * @return the new builder.
     */
    static SearchResultBuilder newBuilder() {
        return SearchModelFactory.newSearchResultBuilder();
    }

    /**
     * Returns a new builder with a fluent API for a {@code SearchResult} which is initialised with the properties of
     * the this SearchResult.
     *
     * @return the new builder.
     */
    default SearchResultBuilder toBuilder() {
        return SearchModelFactory.newSearchResultBuilder(this);
    }

    /**
     * Get the items as a JsonArray. The array can be empty, but is never {@code null}.
     *
     * @return the items as JsonArray.
     */
    JsonArray getItems();

    /**
     * Get the offset of the next page if there are more matching results available or {@link #NO_NEXT_PAGE}, if there
     * is no next page. Superseded by {@code getCursor()}.
     *
     * @return the offset of the next page or {@link #NO_NEXT_PAGE}, if there is no next page.
     */
    Optional<Long> getNextPageOffset();

    /**
     * Get the cursor to the next page.
     *
     * @return the cursor to the next page.
     */
    Optional<String> getCursor();

    /**
     * Returns {@code true} if there is a next page and thus {@link #getNextPageOffset()} does not equal
     * {@link #NO_NEXT_PAGE}, otherwise {@code false}.
     *
     * @return {@code true} if there is a next page, otherwise {@code false}.
     */
    boolean hasNextPage();

    /**
     * Returns the number of items of this search result.
     *
     * @return the number of items this search result contains.
     */
    int getSize();

    /**
     * Indicates whether this search result has items.
     *
     * @return {@code true} if this search result does not contain any items, {@code false} else.
     */
    boolean isEmpty();

    /**
     * Returns a sequential {@code Stream} with the items of this search result as its source.
     *
     * @return a sequential stream of the items of this search result.
     */
    Stream<JsonValue> stream();

    /**
     * Returns all non-hidden marked fields of this object.
     *
     * @return a JSON object representation of this object including only non-hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }

    /**
     * An enumeration of the known {@link JsonField}s of a SearchResult.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@link JsonSchemaVersion}.
         *
         * @deprecated as of 2.3.0 this field definition is not used anymore.
         */
        @Deprecated
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION = JsonFactory.newIntFieldDefinition(
                JsonSchemaVersion.getJsonKey(),
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2
        );

        /**
         * JSON field containing the items.
         */
        public static final JsonFieldDefinition<JsonArray> ITEMS =
                JsonFactory.newJsonArrayFieldDefinition("items", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the nextPageOffset.
         */
        public static final JsonFieldDefinition<Long> NEXT_PAGE_OFFSET =
                JsonFactory.newLongFieldDefinition("nextPageOffset", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the cursor.
         */
        public static final JsonFieldDefinition<String> CURSOR =
                JsonFactory.newStringFieldDefinition("cursor", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
