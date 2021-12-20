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
package org.eclipse.ditto.thingsearch.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.thingsearch.model.SearchModelFactory;
import org.eclipse.ditto.thingsearch.model.SearchResult;

/**
 * Response to a {@link QueryThings} command.
 */
@Immutable
@JsonParsableCommandResponse(type = QueryThingsResponse.TYPE)
public final class QueryThingsResponse extends AbstractCommandResponse<QueryThingsResponse>
        implements ThingSearchQueryCommandResponse<QueryThingsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + QueryThings.NAME;

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<QueryThingsResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final JsonValue searchResultJsonValue = jsonObject.getValueOrThrow(JsonFields.PAYLOAD);

                        return new QueryThingsResponse(
                                SearchModelFactory.newSearchResult(searchResultJsonValue.asObject()),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final SearchResult searchResult;

    private QueryThingsResponse(final SearchResult searchResult,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        QueryThingsResponse.class),
                dittoHeaders);
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
        return new QueryThingsResponse(checkNotNull(searchResult, "searchResult"), HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a QueryThingsResponse command from a JSON string.
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
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a QueryThingsResponse command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the command which caused this response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static QueryThingsResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
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
        return of(SearchModelFactory.newSearchResult(entity.toString()), getDittoHeaders());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
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
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof QueryThingsResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", searchResult=" + searchResult + "]";
    }

}
