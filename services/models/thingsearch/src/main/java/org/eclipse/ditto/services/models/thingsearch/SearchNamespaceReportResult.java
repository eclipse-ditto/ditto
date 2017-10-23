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
package org.eclipse.ditto.services.models.thingsearch;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Create a List with all namespaces and their numbers of things.
 */
@Immutable
public final class SearchNamespaceReportResult implements Jsonifiable.WithPredicate<JsonObject, JsonField> {

    private static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
            JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, JsonSchemaVersion.V_1);
    private static final JsonFieldDefinition<JsonArray> NAMESPACES =
            JsonFactory.newJsonArrayFieldDefinition("namespaces", FieldType.REGULAR, JsonSchemaVersion.V_1);

    private final Map<String, SearchNamespaceResultEntry> searchNamespaceResultEntries;

    /**
     * Creates the Namespace Report.
     */
    public SearchNamespaceReportResult(final Iterable<SearchNamespaceResultEntry> entries) {
        checkNotNull(entries, "entries");
        searchNamespaceResultEntries = new HashMap<>();
        entries.forEach(entry -> searchNamespaceResultEntries.put(entry.getNamespace(), entry));
    }

    /**
     * Creates a new {@code SearchNamespaceReportResult} from a JSON string.
     *
     * @param jsonString the JSON string of which a new SearchNamespaceReportResult is to be created.
     * @return the SearchNamespaceReportResult which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} does not contain a JSON object or if it is not
     * valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonString} was not in the expected
     * 'SearchNamespaceReportResult' format.
     */
    public static SearchNamespaceReportResult fromJson(final String jsonString) {
        final JsonObject jsonObject = JsonFactory.newObject(jsonString);
        return fromJson(jsonObject);
    }

    /**
     * Creates a new {@code SearchNamespaceReportResult} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new SearchNamespaceReportResult is to be created.
     * @return the SearchNamespaceReportResult which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} does not contain a JSON object or if it is not
     * valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected
     * 'SearchNamespaceReportResult' format.
     */
    public static SearchNamespaceReportResult fromJson(final JsonObject jsonObject) {
        final JsonArray namespacesJsonArray = jsonObject.getValueOrThrow(NAMESPACES);

        final Collection<SearchNamespaceResultEntry> resultEntries = namespacesJsonArray.stream()
                .map(JsonValue::asObject)
                .map(SearchNamespaceResultEntry::fromJson)
                .collect(Collectors.toList());

        return new SearchNamespaceReportResult(resultEntries);
    }

    /**
     * Get the SearchNamespaceResultEntry to the given Namespace.
     *
     * @param namespace the Namespace.
     */
    public SearchNamespaceResultEntry getNamespaceEntry(final String namespace) {
        return searchNamespaceResultEntries.get(namespace);
    }

    @Override
    public JsonObject toJson(final Predicate<JsonField> predicate) {
        final JsonSchemaVersion jsonSchemaVersion = JsonSchemaVersion.V_1;

        final JsonArrayBuilder jsonArrayBuilder = JsonFactory.newArrayBuilder();
        searchNamespaceResultEntries.forEach((id, entry) -> jsonArrayBuilder.add(entry.toJson()));
        final JsonArray jsonArray = jsonArrayBuilder.build();

        return JsonFactory.newObjectBuilder()
                .set(SCHEMA_VERSION, jsonSchemaVersion.toInt(), predicate)
                .set(NAMESPACES, jsonArray, predicate)
                .build();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final JsonArrayBuilder jsonArrayBuilder = JsonFactory.newArrayBuilder();
        searchNamespaceResultEntries.forEach((id, entry) -> jsonArrayBuilder.add(entry.toJson()));
        final JsonArray jsonArray = jsonArrayBuilder.build();

        return JsonFactory.newObjectBuilder()
                .set(SCHEMA_VERSION, schemaVersion.toInt(), predicate)
                .set(NAMESPACES, jsonArray, predicate)
                .build();
    }

    @Override
    public JsonObject toJson() {
        return toJson(FieldType.REGULAR);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        final SearchNamespaceReportResult that = (SearchNamespaceReportResult) o;
        return Objects.equals(searchNamespaceResultEntries, that.searchNamespaceResultEntries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchNamespaceResultEntries);
    }

    @Override
    public String toString() {
        return "SearchNamespaceReportResult{" +
                "searchNamespaceResultEntries=" + searchNamespaceResultEntries +
                '}';
    }

}
