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
package org.eclipse.ditto.signals.commands.thingsearch;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.ThingSearchErrorRegistry;

/**
 * Response to a {@link ThingSearchCommand} which wraps the exception thrown by SearchService when processing the
 * SearchCommand.
 */
@Immutable
public final class SearchErrorResponse extends AbstractCommandResponse<SearchErrorResponse>
        implements ThingSearchCommandResponse<SearchErrorResponse>, ErrorResponse<SearchErrorResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + "errorResponse";
    private final static ThingSearchErrorRegistry SEARCH_ERROR_REGISTRY = ThingSearchErrorRegistry.newInstance();
    private final DittoRuntimeException dittoRuntimeException;

    private SearchErrorResponse(final DittoRuntimeException dittoRuntimeException, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoRuntimeException.getStatusCode(), dittoHeaders);
        this.dittoRuntimeException =
                requireNonNull(dittoRuntimeException, "The DittoRuntimeException must not be null");
    }

    /**
     * Creates a new {@code SearchErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param dittoRuntimeException the exception.
     * @param dittoHeaders the headers of the command which caused the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static SearchErrorResponse of(final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {
        return new SearchErrorResponse(dittoRuntimeException, dittoHeaders);
    }

    /**
     * Creates a new {@code SearchErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonString}.
     *
     * @param jsonString the JSON string representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the SearchErrorResponse.
     */
    public static SearchErrorResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(SEARCH_ERROR_REGISTRY, jsonString, dittoHeaders);
    }

    /**
     * Creates a new {@code SearchErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonString} using a special {@code SearchErrorRegistry}.
     *
     * @param searchErrorRegistry the special {@code SearchErrorRegistry} to use for deserializing the
     * DittoRuntimeException.
     * @param jsonString the JSON string representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the KeystoreErrorResponse.
     */
    public static SearchErrorResponse fromJson(final ThingSearchErrorRegistry searchErrorRegistry,
            final String jsonString,
            final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return fromJson(searchErrorRegistry, jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code SearchErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonObject}.
     *
     * @param jsonObject the JSON representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the SearchErrorResponse.
     */
    public static SearchErrorResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromJson(SEARCH_ERROR_REGISTRY, jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code SearchErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonObject} using a special {@code SearchErrorRegistry}.
     *
     * @param searchErrorRegistry the special {@code SearchErrorRegistry} to use for deserializing the
     * DittoRuntimeException.
     * @param jsonObject the JSON representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the SearchErrorResponse.
     */
    public static SearchErrorResponse fromJson(final ThingSearchErrorRegistry searchErrorRegistry,
            final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final JsonObject payload = jsonObject.getValue(JsonFields.PAYLOAD)
                .map(JsonValue::asObject)
                .orElseThrow(() -> new JsonMissingFieldException(JsonFields.PAYLOAD.getPointer()));
        final DittoRuntimeException exception = searchErrorRegistry.parse(payload, dittoHeaders);
        return of(exception, dittoHeaders);
    }

    /**
     * Returns the wrapped {@code DittoRuntimeException}.
     *
     * @return the wrapped exception.
     */
    public DittoRuntimeException getDittoRuntimeException() {
        return dittoRuntimeException;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.PAYLOAD, dittoRuntimeException.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public SearchErrorResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(dittoRuntimeException, dittoHeaders);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(dittoRuntimeException);
        return result;
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
        final SearchErrorResponse that = (SearchErrorResponse) o;
        return that.canEqual(this) && Objects.equals(dittoRuntimeException, that.dittoRuntimeException) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(final Object other) {
        return (other instanceof SearchErrorResponse);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", dittoRuntimeException=" +
                dittoRuntimeException +
                "]";
    }
}
