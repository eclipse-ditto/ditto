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
 * Command for counting things.
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingSearchCommand.TYPE_PREFIX, name = CountThings.NAME)
public final class CountThings extends AbstractCommand<CountThings> implements ThingSearchQueryCommand<CountThings> {

    /**
     * Name of the command.
     */
    public static final String NAME = "countThings";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_FILTER =
            JsonFactory.newStringFieldDefinition("filter", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_NAMESPACES =
            JsonFactory.newJsonArrayFieldDefinition("namespaces", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    @Nullable private final String filter;
    @Nullable private final Set<String> namespaces;

    private CountThings(final DittoHeaders dittoHeaders, @Nullable final String filter,
            @Nullable final Collection<String> namespaces) {
        super(TYPE, dittoHeaders);
        this.filter = filter;
        if (namespaces != null) {
            this.namespaces = Collections.unmodifiableSet(new HashSet<>(namespaces));
        } else {
            this.namespaces = null;
        }
    }

    /**
     * Returns a new instance of {@code CountThings}.
     *
     * @param filter the optional filter string
     * @param dittoHeaders the headers of the command.
     * @return a new command for counting Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CountThings of(@Nullable final String filter, @Nullable final Set<String> namespaces,
            final DittoHeaders dittoHeaders) {

        return new CountThings(dittoHeaders, filter, namespaces);
    }

    /**
     * Returns a new instance of {@code CountThings}.
     *
     * @param dittoHeaders the headers of the command.
     * @return a new command for counting Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CountThings of(final DittoHeaders dittoHeaders) {
        return new CountThings(dittoHeaders, null, null);
    }

    /**
     * Creates a new {@code CountThings} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static CountThings fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code CountThings} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CountThings fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<CountThings>(TYPE, jsonObject).deserialize(() -> {
            final String extractedFilter = jsonObject.getValue(JSON_FILTER).orElse(null);

            final Set<String> extractedNamespaces = jsonObject.getValue(JSON_NAMESPACES)
                    .map(jsonValues -> jsonValues.stream()
                            .filter(JsonValue::isString)
                            .map(JsonValue::asString)
                            .collect(Collectors.toSet()))
                    .orElse(null);

            return new CountThings(dittoHeaders, extractedFilter, extractedNamespaces);
        });
    }

    @Override
    public Optional<String> getFilter() {
        return Optional.ofNullable(filter);
    }

    @Override
    public Optional<Set<String>> getNamespaces() {
        return Optional.ofNullable(namespaces);
    }

    @Override
    public CountThings setNamespaces(@Nullable final Collection<String> namespaces) {
        return new CountThings(getDittoHeaders(), filter, namespaces);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        if (filter != null) {
            jsonObjectBuilder.set(JSON_FILTER, filter, predicate);
        }
        getNamespaces().ifPresent(presentOptions -> jsonObjectBuilder.set(JSON_NAMESPACES, presentOptions.stream()
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray()), predicate));
    }

    @Override
    public CountThings setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(filter, namespaces, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CountThings))
            return false;
        if (!super.equals(o))
            return false;
        final CountThings that = (CountThings) o;
        return Objects.equals(filter, that.filter) && Objects.equals(namespaces, that.namespaces);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filter, namespaces);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + "filter='" + filter + "', namespaces='" + namespaces + "']";
    }
}
