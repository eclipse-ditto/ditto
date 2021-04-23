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
package org.eclipse.ditto.thingsearch.model.signals.commands.subscription;


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
import org.eclipse.ditto.thingsearch.model.signals.commands.query.ThingSearchQueryCommand;

/**
 * Command for subscribing for things in a search result.
 * Corresponds to the reactive-streams signal {@code Publisher#subscribe(Subscriber)}.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingSearchCommand.TYPE_PREFIX, name = CreateSubscription.NAME)
public final class CreateSubscription extends AbstractCommand<CreateSubscription>
        implements ThingSearchQueryCommand<CreateSubscription> {

    /**
     * Name of the command.
     */
    public static final String NAME = "subscribe";

    /**
     * Type of this command.
     */
    public static final String TYPE = ThingSearchCommand.TYPE_PREFIX + NAME;

    @Nullable private final String filter;
    @Nullable private final String options;
    @Nullable private final JsonFieldSelector fields;
    @Nullable private final Set<String> namespaces;
    @Nullable private final String prefix;

    private CreateSubscription(@Nullable final String filter,
            @Nullable final String options,
            @Nullable final JsonFieldSelector fields,
            @Nullable final Collection<String> namespaces,
            @Nullable final String prefix,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.filter = filter;
        this.prefix = prefix;
        this.options = options;

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
    public static CreateSubscription of(@Nullable final String filter, @Nullable final String options,
            @Nullable final JsonFieldSelector fields, @Nullable final Set<String> namespaces,
            final DittoHeaders dittoHeaders) {
        return new CreateSubscription(filter, options, fields, namespaces, null, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code QueryThings}.
     *
     * @param dittoHeaders the headers of the command.
     * @return a new command for searching Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CreateSubscription of(final DittoHeaders dittoHeaders) {
        return new CreateSubscription(null, null, null, null, null, dittoHeaders);
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

            final String extractedOptions = jsonObject.getValue(JsonFields.OPTIONS).orElse(null);


            final JsonFieldSelector extractedFieldSelector = jsonObject.getValue(JsonFields.FIELDS)
                    .map(fields -> JsonFactory.newFieldSelector(fields, JsonFactory.newParseOptionsBuilder().build()))
                    .orElse(null);

            final Set<String> extractedNamespaces = jsonObject.getValue(JsonFields.NAMESPACES)
                    .map(jsonValues -> jsonValues.stream()
                            .filter(JsonValue::isString)
                            .map(JsonValue::asString)
                            .collect(Collectors.toSet()))
                    .orElse(null);

            final String prefix = jsonObject.getValue(JsonFields.PREFIX).orElse(null);

            return new CreateSubscription(extractedFilter, extractedOptions, extractedFieldSelector,
                    extractedNamespaces, prefix, dittoHeaders
            );
        });
    }

    /**
     * Returns the filter which is to be included in the JSON of the retrieved entity.
     *
     * @return the filter string.
     */
    public Optional<String> getFilter() {
        return Optional.ofNullable(filter);
    }

    /**
     * Get the optional options.
     *
     * @return the optional options.
     */
    public Optional<String> getOptions() {
        return Optional.ofNullable(options);
    }

    /**
     * Get the prefix of subscription IDs. The prefix is used to identify a subscription manager if multiple
     * are deployed in the cluster.
     *
     * @return the subscription ID prefix.
     */
    public Optional<String> getPrefix() {
        return Optional.ofNullable(prefix);
    }

    /**
     * Get the optional field selector.
     *
     * @return the optional field selector.
     */
    @Override
    public Optional<JsonFieldSelector> getSelectedFields() {
        return Optional.ofNullable(fields);
    }

    @Override
    public Optional<Set<String>> getNamespaces() {
        return Optional.ofNullable(namespaces);
    }

    @Override
    public CreateSubscription setNamespaces(@Nullable final Collection<String> namespaces) {
        return new CreateSubscription(filter, options, fields, namespaces, prefix, getDittoHeaders());
    }

    /**
     * Create a copy of this command with prefix set. The prefix is used to identify a subscription manager
     * if multiple are deployed in the cluster.
     *
     * @param prefix the subscription ID prefix.
     * @return the new command.
     */
    public CreateSubscription setPrefix(@Nullable final String prefix) {
        return new CreateSubscription(filter, options, fields, namespaces, prefix, getDittoHeaders());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        getFilter().ifPresent(presentFilter -> jsonObjectBuilder.set(JsonFields.FILTER, presentFilter, predicate));
        getOptions().ifPresent(presentOptions -> jsonObjectBuilder.set(JsonFields.OPTIONS, presentOptions, predicate));
        getSelectedFields().ifPresent(
                presentFields -> jsonObjectBuilder.set(JsonFields.FIELDS, presentFields.toString(), predicate));
        getNamespaces().ifPresent(presentOptions -> jsonObjectBuilder.set(JsonFields.NAMESPACES, presentOptions.stream()
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray()), predicate));
        getPrefix().ifPresent(thePrefix -> jsonObjectBuilder.set(JsonFields.PREFIX, thePrefix));
    }

    @Override
    public CreateSubscription setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new CreateSubscription(filter, options, fields, namespaces, prefix, dittoHeaders);
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
                Objects.equals(namespaces, that.namespaces) &&
                Objects.equals(prefix, that.prefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filter, options, fields, namespaces, prefix);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                super.toString() +
                ", filter=" + filter +
                ", options=" + options +
                ", fields=" + fields +
                ", namespaces=" + namespaces +
                ", prefix=" + prefix +
                ']';
    }

    /**
     * Json fields of this command.
     */
    public static final class JsonFields {

        /**
         * Optional JSON field for the filter.
         */
        public static final JsonFieldDefinition<String> FILTER =
                JsonFactory.newStringFieldDefinition("filter", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * Optional JSON field for the options of this command.
         */
        public static final JsonFieldDefinition<String> OPTIONS =
                JsonFactory.newStringFieldDefinition("options", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * Optional JSON field for fields to select in the search result generated by this command.
         */
        public static final JsonFieldDefinition<String> FIELDS =
                JsonFactory.newStringFieldDefinition("fields", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * Optional JSON field for namespaces in which to perform the search.
         */
        public static final JsonFieldDefinition<JsonArray> NAMESPACES =
                JsonFactory.newJsonArrayFieldDefinition("namespaces", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * Internal JSON field for prefix setting.
         */
        static final JsonFieldDefinition<String> PREFIX =
                JsonFactory.newStringFieldDefinition("prefix", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        JsonFields() {
            throw new AssertionError();
        }
    }
}
