/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.api.commands.sudo;


import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.ThingSearchQueryCommand;

/**
 * Ditto-internal command to start or resume a search request for a stream of thing IDs.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingSearchSudoCommand.TYPE_PREFIX, name = StreamThings.NAME)
public final class StreamThings
        extends AbstractCommand<StreamThings> implements ThingSearchQueryCommand<StreamThings> {

    /**
     * Name of the command.
     */
    public static final String NAME = "streamThings";

    /**
     * Type of this command.
     */
    public static final String TYPE = ThingSearchSudoCommand.TYPE_PREFIX + NAME;

    @Nullable private final String filter;
    @Nullable private final JsonArray namespaces;
    @Nullable private final String sort;
    @Nullable private final JsonArray sortValues;

    private StreamThings(@Nullable final String filter,
            @Nullable final JsonArray namespaces,
            @Nullable final String sort,
            @Nullable final JsonArray sortValues,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.filter = filter;
        this.namespaces = namespaces;
        this.sort = sort;
        this.sortValues = sortValues;
    }

    /**
     * Returns a new instance of {@code StreamThings}.
     *
     * @param filter the optional query filter string.
     * @param namespaces namespaces to search, or null to search all namespaces.
     * @param sort the sort option.
     * @param sortValues the values of the sort fields to resume from.
     * @param dittoHeaders the headers of the command.
     * @return a new command for streaming search results.
     */
    public static StreamThings of(@Nullable final String filter,
            @Nullable final JsonArray namespaces,
            @Nullable final String sort,
            @Nullable final JsonArray sortValues,
            final DittoHeaders dittoHeaders) {
        return new StreamThings(filter, namespaces, sort, sortValues, dittoHeaders);
    }

    /**
     * Creates a new {@code StreamThings} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static StreamThings fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<StreamThings>(TYPE, jsonObject).deserialize(() -> {
            final String filter = jsonObject.getValue(JsonFields.FILTER).orElse(null);
            final JsonArray namespaces = jsonObject.getValue(JsonFields.NAMESPACES).orElse(null);
            final String sort = jsonObject.getValue(JsonFields.SORT).orElse(null);
            final JsonArray sortValues = jsonObject.getValue(JsonFields.SORT_VALUES).orElse(null);
            return new StreamThings(filter, namespaces, sort, sortValues, dittoHeaders);
        });
    }

    /**
     * Get the optional filter string.
     *
     * @return the optional filter string.
     */
    @Override
    public Optional<String> getFilter() {
        return Optional.ofNullable(filter);
    }

    /**
     * Get the optional set of namespaces.
     *
     * @return the optional set of namespaces.
     */
    @Override
    public Optional<Set<String>> getNamespaces() {
        return Optional.ofNullable(namespaces)
                .map(array -> array.stream().map(JsonValue::asString).collect(Collectors.toSet()));
    }

    /**
     * Get the namespaces to search.
     *
     * @return the namespaces to search, or an empty optional to search all namespaces.
     */
    public Optional<JsonArray> getNamespacesAsArray() {
        return Optional.ofNullable(namespaces);
    }

    /**
     * Get the sort option if any exists.
     *
     * @return the optional sort option.
     */
    public Optional<String> getSort() {
        return Optional.ofNullable(sort);
    }

    /**
     * Get the sort values to resume a search from.
     *
     * @return sort values.
     */
    public Optional<JsonArray> getSortValues() {
        return Optional.ofNullable(sortValues);
    }

    /**
     * Sets the given namespaces on a copy of this command and returns it.
     *
     * @param namespaces the namespaces.
     * @return the created command.
     */
    @Override
    public StreamThings setNamespaces(@Nullable final Collection<String> namespaces) {
        if (namespaces == null) {
            return new StreamThings(filter, JsonArray.empty(), sort, sortValues, getDittoHeaders());
        } else {
            final JsonArray namespacesJson = namespaces.stream()
                    .map(JsonValue::of)
                    .collect(JsonCollectors.valuesToArray());
            return new StreamThings(filter, namespacesJson, sort, sortValues, getDittoHeaders());
        }
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        if (filter != null) {
            jsonObjectBuilder.set(JsonFields.FILTER, filter);
        }
        if (namespaces != null) {
            jsonObjectBuilder.set(JsonFields.NAMESPACES, namespaces);
        }
        if (sort != null) {
            jsonObjectBuilder.set(JsonFields.SORT, sort);
        }
        if (sortValues != null) {
            jsonObjectBuilder.set(JsonFields.SORT_VALUES, sortValues);
        }
    }

    /**
     * Create a copy of this command with new sort values for cursor computation.
     *
     * @param sortValues the new sort values.
     * @return the new command.
     */
    public StreamThings setSortValues(final JsonArray sortValues) {
        return new StreamThings(filter, namespaces, sort, sortValues, getDittoHeaders());
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public StreamThings setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new StreamThings(filter, namespaces, sort, sortValues, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof StreamThings))
            return false;
        if (!super.equals(o))
            return false;
        final StreamThings that = (StreamThings) o;
        return Objects.equals(filter, that.filter) &&
                Objects.equals(namespaces, that.namespaces) &&
                Objects.equals(sort, that.sort) &&
                Objects.equals(sortValues, that.sortValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filter, namespaces, sort, sortValues);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", filter=" + filter +
                ", namespaces=" + namespaces +
                ", sort=" + sort +
                ", sortValues=" + sortValues +
                ']';
    }

    private static final class JsonFields {

        private static final JsonFieldDefinition<String> FILTER =
                JsonFactory.newStringFieldDefinition("filter");

        private static final JsonFieldDefinition<JsonArray> NAMESPACES =
                JsonFactory.newJsonArrayFieldDefinition("namespaces");

        private static final JsonFieldDefinition<String> SORT =
                JsonFactory.newStringFieldDefinition("sort");

        private static final JsonFieldDefinition<JsonArray> SORT_VALUES =
                JsonFactory.newJsonArrayFieldDefinition("sortValues");
    }

}
