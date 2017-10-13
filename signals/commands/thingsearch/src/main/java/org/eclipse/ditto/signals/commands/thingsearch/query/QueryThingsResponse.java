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
package org.eclipse.ditto.signals.commands.thingsearch.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.thingsearch.SearchModelFactory;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;


/**
 * Response to a {@link QueryThings} command.
 */
@Immutable
public final class QueryThingsResponse extends AbstractCommandResponse<QueryThingsResponse>
        implements ThingSearchQueryCommandResponse<QueryThingsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + QueryThings.NAME;

    private final SearchResult searchResult;

    private QueryThingsResponse(final SearchResult searchResult, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.searchResult = searchResult;
    }

    /**
     * Returns a new {@code QueryThingsResponse} instance for the issued query.
     *
     * @param searchResult the Search Result which was retrieved from the Search service.
     * @param dittoHeaders the headers of the command which caused this response.
     * @return a new response for the "Query Things" command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static QueryThingsResponse of(final SearchResult searchResult, final DittoHeaders dittoHeaders) {
        checkNotNull(searchResult, "search result");

        return new QueryThingsResponse(searchResult, dittoHeaders);
    }

    /**
     * Creates a response to a {@link QueryThingsResponse} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the command which caused this response.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static QueryThingsResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link QueryThingsResponse} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the command which caused this response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static QueryThingsResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<QueryThingsResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final JsonObject searchResultJson = jsonObject.getValueOrThrow(JsonFields.PAYLOAD).asObject();
                    final SearchResult extractedSearchResult = SearchModelFactory.newSearchResult(searchResultJson);

                    return of(extractedSearchResult, dittoHeaders);
                });
    }

    /**
     * Returns the SearchResult.
     *
     * @return the SearchResult.
     */
    public SearchResult getSearchResult() {
        return searchResult;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return searchResult.toJson(schemaVersion);
    }

    @Override
    public QueryThingsResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        final SearchResult searchResult = SearchModelFactory.newSearchResult(entity.toString());
        return of(searchResult, getDittoHeaders());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.PAYLOAD, searchResult.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public QueryThingsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(searchResult, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), searchResult);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final QueryThingsResponse that = (QueryThingsResponse) o;
        return that.canEqual(this) && Objects.equals(searchResult, that.searchResult) && super.equals(that);
    }

    @Override
    protected boolean canEqual(final Object other) {
        return (other instanceof QueryThingsResponse);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", searchResult=" + searchResult + "]";
    }
}
