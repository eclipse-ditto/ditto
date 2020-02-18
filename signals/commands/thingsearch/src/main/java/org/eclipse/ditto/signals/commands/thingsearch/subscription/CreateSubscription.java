/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.thingsearch.subscription;


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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;
import org.eclipse.ditto.signals.commands.thingsearch.query.ThingSearchQueryCommand;

/**
 * Command for subscribing for things in a search result.
 * Corresponds to the reactive-streams signal {@code Publisher#subscribe(Subscriber)}.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableCommand(typePrefix = CreateSubscription.TYPE_PREFIX, name = CreateSubscription.NAME)
public final class CreateSubscription extends AbstractCommand<CreateSubscription>
        implements ThingSearchQueryCommand<CreateSubscription> {

    /**
     * Name of the command.
     */
    public static final String NAME = "subscribe";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    @Nullable private final String filter;
    @Nullable private final List<String> options;
    @Nullable private final JsonFieldSelector fields;
    @Nullable private final Set<String> namespaces;

    private CreateSubscription(final DittoHeaders dittoHeaders,
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
     * Returns a new instance of the command.
     *
     * @param filter the optional query filter string
     * @param options the optional query options
     * @param fields the optional fields
     * @param dittoHeaders the headers of the command.
     * @return a new command to subscribe for search results.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static CreateSubscription of(@Nullable final String filter, @Nullable final List<String> options,
            @Nullable final JsonFieldSelector fields, @Nullable final Set<String> namespaces,
            final DittoHeaders dittoHeaders) {
        return new CreateSubscription(dittoHeaders, filter, options, fields, namespaces);
    }

    /**
     * Returns a new instance of {@code QueryThings}.
     *
     * @param dittoHeaders the headers of the command.
     * @return a new command for searching Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CreateSubscription of(final DittoHeaders dittoHeaders) {
        return new CreateSubscription(dittoHeaders, null, null, null, null);
    }

    /**
     * Creates a new {@code CreateSubscription} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CreateSubscription fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<CreateSubscription>(TYPE, jsonObject).deserialize(() -> {
            final String extractedFilter = jsonObject.getValue(JsonFields.FILTER).orElse(null);

            final List<String> extractedOptions = jsonObject.getValue(JsonFields.OPTIONS)
                    .map(jsonArray -> jsonArray.stream()
                            .filter(JsonValue::isString)
                            .map(JsonValue::asString)
                            .collect(Collectors.toList()))
                    .orElse(null);

            final JsonFieldSelector extractedFieldSelector = jsonObject.getValue(JsonFields.FIELDS)
                    .map(fields -> JsonFactory.newFieldSelector(fields, JsonFactory.newParseOptionsBuilder().build()))
                    .orElse(null);

            final Set<String> extractedNamespaces = jsonObject.getValue(JsonFields.NAMESPACES)
                    .map(jsonValues -> jsonValues.stream()
                            .filter(JsonValue::isString)
                            .map(JsonValue::asString)
                            .collect(Collectors.toSet()))
                    .orElse(null);

            return new CreateSubscription(dittoHeaders, extractedFilter, extractedOptions, extractedFieldSelector,
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
    public CreateSubscription setNamespaces(@Nullable final Collection<String> namespaces) {
        return new CreateSubscription(getDittoHeaders(), filter, options, fields, namespaces);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        getFilter().ifPresent(presentFilter -> jsonObjectBuilder.set(JsonFields.FILTER, presentFilter, predicate));
        getOptions().ifPresent(presentOptions -> jsonObjectBuilder.set(JsonFields.OPTIONS, presentOptions.stream()
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray()), predicate));
        getFields().ifPresent(
                presentFields -> jsonObjectBuilder.set(JsonFields.FIELDS, presentFields.toString(), predicate));
        getNamespaces().ifPresent(presentOptions -> jsonObjectBuilder.set(JsonFields.NAMESPACES, presentOptions.stream()
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray()), predicate));
    }

    @Override
    public CreateSubscription setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new CreateSubscription(dittoHeaders, filter, options, fields, namespaces);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CreateSubscription))
            return false;
        if (!super.equals(o))
            return false;
        final CreateSubscription that = (CreateSubscription) o;
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

    static final class JsonFields {

        static final JsonFieldDefinition<String> FILTER =
                JsonFactory.newStringFieldDefinition("filter", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonArray> OPTIONS =
                JsonFactory.newJsonArrayFieldDefinition("options", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> FIELDS =
                JsonFactory.newStringFieldDefinition("fields", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonArray> NAMESPACES =
                JsonFactory.newJsonArrayFieldDefinition("namespaces", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
    }
}
