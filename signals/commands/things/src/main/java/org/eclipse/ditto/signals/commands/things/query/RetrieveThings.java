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
package org.eclipse.ditto.signals.commands.things.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.WithNamespace;

/**
 * Command which retrieves several {@link org.eclipse.ditto.model.things.Thing}s based on the the passed in List of
 * Thing IDs.
 */
@Immutable
public final class RetrieveThings extends AbstractCommand<RetrieveThings>
        implements ThingQueryCommand<RetrieveThings>, WithNamespace {

    /**
     * Name of the "Retrieve Things" command.
     */
    public static final String NAME = "retrieveThings";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    public static final JsonFieldDefinition<JsonArray> JSON_THING_IDS =
            JsonFactory.newJsonArrayFieldDefinition("thingIds", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_SELECTED_FIELDS =
            JsonFactory.newStringFieldDefinition("selectedFields", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_NAMESPACE =
            JsonFactory.newStringFieldDefinition("namespace", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final List<String> thingIds;
    @Nullable private final JsonFieldSelector selectedFields;
    @Nullable private final String namespace;

    private RetrieveThings(final Builder builder) {
        this(builder.thingIds, builder.selectedFields, builder.namespace, builder.dittoHeaders);
    }

    private RetrieveThings(final List<String> thingIds,
            @Nullable final JsonFieldSelector selectedFields,
            @Nullable final String namespace,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);

        this.thingIds = Collections.unmodifiableList(new ArrayList<>(thingIds));
        this.selectedFields = selectedFields;
        this.namespace = checkForDistinctNamespace(namespace, thingIds);
    }

    @Nullable
    private static String checkForDistinctNamespace(@Nullable final String providedNamespace,
            final Collection<String> thingIds) {

        if (providedNamespace != null) {
            final List<String> distinctNamespaces = thingIds.stream()
                    .map(id -> id.split(":"))
                    .filter(parts -> parts.length > 1)
                    .map(parts -> parts[0])
                    .distinct()
                    .collect(Collectors.toList());

            if (distinctNamespaces.size() != 1) {
                throw new IllegalArgumentException(
                        "Retrieving multiple things is only supported if all things are in the same, non empty namespace");
            }

            // if a specific namespace is provided it must match the namespace of the things to retrieve
            if (!distinctNamespaces.get(0).equals(providedNamespace)) {
                throw new IllegalArgumentException(
                        "The provided namespace must match the namespace of all things to retrieve.");
            }
        }
        return providedNamespace;
    }

    /**
     * Returns a builder for a command for retrieving the Things.
     *
     * @param thingIds one or more Thing IDs to be retrieved.
     * @return a builder for a Thing retrieving command.
     * @throws NullPointerException if {@code authorizationContext} is {@code null}.
     */
    public static Builder getBuilder(final String... thingIds) {
        return new Builder(Arrays.asList(thingIds));
    }

    /**
     * Returns a builder for a command for retrieving the Things.
     *
     * @param thingIds the Thing IDs to be retrieved.
     * @return a builder for a Thing retrieving command.
     * @throws NullPointerException if {@code authorizationContext} is {@code null}.
     */
    public static Builder getBuilder(final List<String> thingIds) {
        return new Builder(thingIds);
    }

    /**
     * Returns a builder for a command for retrieving Things. The builder gets initialised with the data from the
     * specified RetrieveThings.
     *
     * @param retrieveThings a {@code RetrieveThings} object which acts as template for the new builder.
     * @return a builder for a Thing retrieving command.
     * @throws NullPointerException if {@code authorizationContext} is {@code null}.
     */
    public static Builder getBuilder(final RetrieveThings retrieveThings) {
        return new Builder(retrieveThings);
    }

    /**
     * Creates a new {@code RetrieveThings} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveThings fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveThings} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveThings fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {

        final String namespace = jsonObject.getValue(JSON_NAMESPACE).orElse(null);

        final List<String> extractedThingIds = jsonObject.getValueOrThrow(JSON_THING_IDS)
                .stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .collect(Collectors.toList());

        final JsonFieldSelector extractedFieldSelector = jsonObject.getValue(JSON_SELECTED_FIELDS)
                .map(str -> JsonFactory.newFieldSelector(str, JsonFactory.newParseOptionsBuilder()
                        .withoutUrlDecoding()
                        .build()))
                .orElse(null);

        return new RetrieveThings(extractedThingIds, extractedFieldSelector, namespace, dittoHeaders);
    }

    /**
     * Returns an unmodifiable unsorted List of the identifiers of the {@code Thing}s to be retrieved.
     *
     * @return the identifiers of the Things.
     */
    public List<String> getThingIds() {
        return thingIds;
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.ofNullable(namespace);
    }

    @Override
    public Optional<JsonFieldSelector> getSelectedFields() {
        return Optional.ofNullable(selectedFields);
    }

    @Override
    public String getThingId() {
        return ":_";
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty(); // no path for retrieve of multiple things
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonArray thingIdsArray = thingIds.stream()
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());
        jsonObjectBuilder.set(JSON_THING_IDS, thingIdsArray, predicate);
        if (namespace != null) {
            jsonObjectBuilder.set(JSON_NAMESPACE, namespace, predicate);
        }
        if (null != selectedFields) {
            jsonObjectBuilder.set(JSON_SELECTED_FIELDS, selectedFields.toString(), predicate);
        }
    }

    @Override
    public RetrieveThings setDittoHeaders(final DittoHeaders dittoHeaders) {
        return getBuilder(this).dittoHeaders(dittoHeaders).build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingIds, selectedFields, namespace);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "pmd:SimplifyConditional"})
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final RetrieveThings that = (RetrieveThings) obj;
        return that.canEqual(this) && Objects.equals(thingIds, that.thingIds)
                && Objects.equals(namespace, that.namespace)
                && Objects.equals(selectedFields, that.selectedFields) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveThings;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingIds=" + thingIds + ", selectedFields="
                + selectedFields + ", namespace=" + namespace + "]";
    }

    /**
     * Builder to facilitate creation of {@code RetrieveThings} instances. Multiple calls to one of this class' methods
     * will overwrite before set values.
     */
    @NotThreadSafe
    public static final class Builder {

        private final List<String> thingIds;

        private DittoHeaders dittoHeaders;
        @Nullable private JsonFieldSelector selectedFields;
        @Nullable private String namespace;

        private Builder(final List<String> thingIds) {
            this.thingIds = new ArrayList<>(thingIds);
            dittoHeaders = DittoHeaders.empty();
            selectedFields = null;
            namespace = null;
        }

        private Builder(final RetrieveThings retrieveThings) {
            this.thingIds = retrieveThings.getThingIds();
            dittoHeaders = retrieveThings.getDittoHeaders();
            selectedFields = retrieveThings.getSelectedFields().orElse(null);
            namespace = retrieveThings.getNamespace().orElse(null);
        }

        /**
         * Sets the optional command headers.
         *
         * @param dittoHeaders the command headers.
         * @return this builder to allow method chaining.
         */
        public Builder dittoHeaders(final DittoHeaders dittoHeaders) {
            this.dittoHeaders = dittoHeaders;
            return this;
        }

        /**
         * Sets the JSON fields which should be shown in the JSON document which is returned due to a HTTP request.
         *
         * @param selectedFields the selected JSON fields to be shown in the resulting JSON document.
         * @return this builder to allow method chaining.
         */
        public Builder selectedFields(@Nullable final JsonFieldSelector selectedFields) {
            if (selectedFields == null || selectedFields.isEmpty()) {
                this.selectedFields = null;
            } else {
                this.selectedFields = selectedFields;
            }
            return this;
        }

        /**
         * Sets the JSON fields which should be shown in the JSON document which is returned due to a HTTP request.
         *
         * @param selectedFields the selected JSON fields to be shown in the resulting JSON document.
         * @return this builder to allow method chaining.
         */
        public Builder selectedFields(final Optional<JsonFieldSelector> selectedFields) {
            if (selectedFields.isPresent()) {
                return selectedFields(selectedFields.get());
            } else {
                this.selectedFields = null;
                return this;
            }
        }

        /**
         * Sets namespace for this command.
         *
         * @param namespace the namespace used for this command
         * @return this builder to allow method chaining.
         */
        public Builder namespace(@Nullable final String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Builds an instance of {@code RetrieveThings} based on the provided values.
         *
         * @return a new instance of {@code RetrieveThings}.
         */
        public RetrieveThings build() {
            return new RetrieveThings(this);
        }

    }

}
