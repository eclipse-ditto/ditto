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


import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command for searching things.
 */
@Immutable
public final class QueryThings extends AbstractCommand<QueryThings> implements ThingSearchQueryCommand<QueryThings> {

    /**
     * Name of the command.
     */
    public static final String NAME = "queryThings";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition JSON_FILTER =
            JsonFactory.newFieldDefinition("filter", String.class, FieldType.REGULAR,
                    // available in schema versions:
                    JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition JSON_OPTIONS =
            JsonFactory.newFieldDefinition("options", JsonArray.class, FieldType.REGULAR,
                    // available in schema versions:
                    JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition JSON_FIELDS =
            JsonFactory.newFieldDefinition("fields", String.class, FieldType.REGULAR,
                    // available in schema versions:
                    JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

    @Nullable
    private final String filter;
    @Nullable
    private final List<String> options;
    @Nullable
    private final JsonFieldSelector fields;

    private QueryThings(final DittoHeaders dittoHeaders, @Nullable final String filter,
            @Nullable final List<String> options, @Nullable final JsonFieldSelector fields) {
        super(TYPE, dittoHeaders);
        this.filter = filter;
        if (options != null) {
            this.options = Collections.unmodifiableList(options);
        } else {
            this.options = null;
        }
        this.fields = fields;
    }

    /**
     * Returns a new instance of {@code QueryThings}.
     *
     * @param filter the optional query filter string
     * @param options the optional query options
     * @param fields the optional fields
     * @param dittoHeaders the headers of the command.
     * @return a new command for searching Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static QueryThings of(@Nullable final String filter, @Nullable final List<String> options,
            @Nullable final JsonFieldSelector fields, final DittoHeaders dittoHeaders) {
        return new QueryThings(dittoHeaders, filter, options, fields);
    }

    /**
     * Returns a new instance of {@code QueryThings}.
     *
     * @param filter the optional query filter string
     * @param options the query options
     * @param dittoHeaders the headers of the command.
     * @return a new command for searching Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static QueryThings of(final String filter, final List<String> options, final DittoHeaders dittoHeaders) {
        return new QueryThings(dittoHeaders, filter, options, null);
    }

    /**
     * Returns a new instance of {@code QueryThings}.
     *
     * @param filter the optional query filter string
     * @param fields the optional fields
     * @param dittoHeaders the headers of the command.
     * @return a new command for searching Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static QueryThings of(final String filter, final JsonFieldSelector fields,
            final DittoHeaders dittoHeaders) {
        return new QueryThings(dittoHeaders, filter, null, fields);
    }

    /**
     * Returns a new instance of {@code QueryThings}.
     *
     * @param filter the optional query filter string
     * @param dittoHeaders the headers of the command.
     * @return a new command for searching Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static QueryThings of(final String filter, final DittoHeaders dittoHeaders) {
        return new QueryThings(dittoHeaders, filter, null, null);
    }

    /**
     * Returns a new instance of {@code QueryThings}.
     *
     * @param options the query options
     * @param fields the optional fields
     * @param dittoHeaders the headers of the command.
     * @return a new command for searching Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static QueryThings of(final List<String> options, final JsonFieldSelector fields,
            final DittoHeaders dittoHeaders) {
        return new QueryThings(dittoHeaders, null, options, fields);
    }

    /**
     * Returns a new instance of {@code QueryThings}.
     *
     * @param fields the optional fields
     * @param dittoHeaders the headers of the command.
     * @return a new command for searching Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static QueryThings of(final JsonFieldSelector fields, final DittoHeaders dittoHeaders) {
        return new QueryThings(dittoHeaders, null, null, fields);
    }

    /**
     * Returns a new instance of {@code QueryThings}.
     *
     * @param options the query options
     * @param dittoHeaders the headers of the command.
     * @return a new command for searching Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static QueryThings of(final List<String> options, final DittoHeaders dittoHeaders) {
        return new QueryThings(dittoHeaders, null, options, null);
    }

    /**
     * Returns a new instance of {@code QueryThings}.
     *
     * @param dittoHeaders the headers of the command.
     * @return a new command for searching Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static QueryThings of(final DittoHeaders dittoHeaders) {
        return new QueryThings(dittoHeaders, null, null, null);
    }

    /**
     * Creates a new {@code QueryThings} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static QueryThings fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code QueryThings} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static QueryThings fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<QueryThings>(TYPE, jsonObject).deserialize(jsonObjectReader -> {
            final String extractedFilter = jsonObjectReader.<String>getAsOptional(JSON_FILTER)
                    .orElse(null);

            final List<String> extractedOptions = jsonObjectReader.<JsonArray>getAsOptional(JSON_OPTIONS)
                    .map(jsonArray -> jsonArray.stream()
                            .filter(JsonValue::isString)
                            .map(JsonValue::asString)
                            .collect(Collectors.toList()))
                    .orElse(null);

            final JsonFieldSelector extractedFieldSelector = jsonObjectReader.<String>getAsOptional(JSON_FIELDS)
                    .map(fields -> JsonFactory.newFieldSelector(fields,
                            JsonFactory.newParseOptionsBuilder().build()))
                    .orElse(null);

            return new QueryThings(dittoHeaders, extractedFilter, extractedOptions, extractedFieldSelector);
        });
    }

    /**
     * Get the optional filter string.
     *
     * @return the optional filter string.
     */
    public Optional<String> getFilter() {
        return Optional.ofNullable(filter);
    }

    /**
     * Get the optional options.
     *
     * @return the optional options.
     */
    public Optional<List<String>> getOptions() {
        return Optional.ofNullable(options);
    }

    /**
     * Get the optional field selector.
     *
     * @return the optional field selector.
     */
    public Optional<JsonFieldSelector> getFields() {
        return Optional.ofNullable(fields);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        getFilter().ifPresent(presentFilter -> jsonObjectBuilder.set(JSON_FILTER, presentFilter, predicate));
        getOptions().ifPresent(presentOptions -> jsonObjectBuilder.set(JSON_OPTIONS, presentOptions.stream()
                        .map(JsonValue::newInstance)
                        .collect(JsonCollectors.valuesToArray()),
                predicate));
        getFields().ifPresent(presentFields -> jsonObjectBuilder.set(JSON_FIELDS, presentFields.toString(), predicate));
    }

    @Override
    public QueryThings setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(filter, options, fields, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof QueryThings))
            return false;
        if (!super.equals(o))
            return false;
        final QueryThings that = (QueryThings) o;
        return Objects.equals(filter, that.filter) &&
                Objects.equals(options, that.options) &&
                Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filter, options, fields);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + "filter='" + filter + "', options=" + options + ", fields=" + fields
                + ']';
    }
}
