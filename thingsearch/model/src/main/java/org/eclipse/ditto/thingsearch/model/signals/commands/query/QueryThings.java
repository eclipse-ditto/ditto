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


import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;

/**
 * Command for searching things.
 * @since 1.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingSearchCommand.TYPE_PREFIX, name = QueryThings.NAME)
public final class QueryThings extends AbstractCommand<QueryThings> implements ThingSearchQueryCommand<QueryThings> {

    /**
     * Name of the command.
     */
    public static final String NAME = "queryThings";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_FILTER =
            JsonFactory.newStringFieldDefinition("filter", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_OPTIONS =
            JsonFactory.newJsonArrayFieldDefinition("options", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_FIELDS =
            JsonFactory.newStringFieldDefinition("fields", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_NAMESPACES =
            JsonFactory.newJsonArrayFieldDefinition("namespaces", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    @Nullable private final String filter;
    @Nullable private final List<String> options;
    @Nullable private final JsonFieldSelector fields;
    @Nullable private final Set<String> namespaces;

    private QueryThings(final DittoHeaders dittoHeaders,
            @Nullable final String filter,
            @Nullable final List<String> options,
            @Nullable final JsonFieldSelector fields,
            @Nullable final Collection<String> namespaces) {
        super(TYPE, dittoHeaders);
        this.filter = filter;
        if (options != null) {
            this.options = Collections.unmodifiableList(options);
        } else {
            this.options = null;
        }
        this.fields = fields;
        if (namespaces != null) {
            this.namespaces = Collections.unmodifiableSet(new HashSet<>(namespaces));
        } else {
            this.namespaces = null;
        }
    }

    /**
     * Returns a new instance of {@code QueryThings}.
     *
     * @param filter the optional query filter string
     * @param options the optional query options
     * @param fields the optional fields
     * @param dittoHeaders the headers of the command.
     * @return a new command for searching Things.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static QueryThings of(@Nullable final String filter, @Nullable final List<String> options,
            @Nullable final JsonFieldSelector fields, @Nullable final Set<String> namespaces,
            final DittoHeaders dittoHeaders) {
        return new QueryThings(dittoHeaders, filter, options, fields, namespaces);
    }

    /**
     * Returns a new instance of {@code QueryThings}.
     *
     * @param dittoHeaders the headers of the command.
     * @return a new command for searching Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static QueryThings of(final DittoHeaders dittoHeaders) {
        return new QueryThings(dittoHeaders, null, null, null, null);
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
        return new CommandJsonDeserializer<QueryThings>(TYPE, jsonObject).deserialize(() -> {
            final String extractedFilter = jsonObject.getValue(JSON_FILTER).orElse(null);

            final List<String> extractedOptions = jsonObject.getValue(JSON_OPTIONS)
                    .map(jsonArray -> jsonArray.stream()
                            .filter(JsonValue::isString)
                            .map(JsonValue::asString)
                            .collect(Collectors.toList()))
                    .orElse(null);

            final JsonFieldSelector extractedFieldSelector = jsonObject.getValue(JSON_FIELDS)
                    .map(fields -> JsonFactory.newFieldSelector(fields, JsonFactory.newParseOptionsBuilder().build()))
                    .orElse(null);

            final Set<String> extractedNamespaces = jsonObject.getValue(JSON_NAMESPACES)
                    .map(jsonValues -> jsonValues.stream()
                            .filter(JsonValue::isString)
                            .map(JsonValue::asString)
                            .collect(Collectors.toSet()))
                    .orElse(null);

            return new QueryThings(dittoHeaders, extractedFilter, extractedOptions, extractedFieldSelector,
                    extractedNamespaces);
        });
    }

    @Override
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
    public Optional<Set<String>> getNamespaces() {
        return Optional.ofNullable(namespaces);
    }

    @Override
    public QueryThings setNamespaces(@Nullable final Collection<String> namespaces) {
        return new QueryThings(getDittoHeaders(), filter, options, fields, namespaces);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        getFilter().ifPresent(presentFilter -> jsonObjectBuilder.set(JSON_FILTER, presentFilter, predicate));
        getOptions().ifPresent(presentOptions -> jsonObjectBuilder.set(JSON_OPTIONS, presentOptions.stream()
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray()), predicate));
        getFields().ifPresent(presentFields -> jsonObjectBuilder.set(JSON_FIELDS, presentFields.toString(), predicate));
        getNamespaces().ifPresent(presentOptions -> jsonObjectBuilder.set(JSON_NAMESPACES, presentOptions.stream()
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray()), predicate));
    }

    @Override
    public QueryThings setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new QueryThings(dittoHeaders, filter, options, fields, namespaces);
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
                Objects.equals(fields, that.fields) &&
                Objects.equals(namespaces, that.namespaces);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filter, options, fields, namespaces);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                super.toString() +
                ", filter=" + filter +
                ", options=" + options +
                ", fields=" + fields +
                ", namespaces=" + namespaces +
                ']';
    }
}
