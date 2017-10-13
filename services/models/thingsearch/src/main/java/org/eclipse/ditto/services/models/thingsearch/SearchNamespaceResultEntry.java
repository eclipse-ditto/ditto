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

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;


/**
 * Represents the number of Things for a namespace.
 */
@Immutable
public final class SearchNamespaceResultEntry implements Jsonifiable.WithPredicate<JsonObject, JsonField> {

    private final static JsonFieldDefinition<Integer> SCHEMA_VERSION_JSON_FIELD =
            JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, JsonSchemaVersion.V_1);

    private static final JsonFieldDefinition<String> NAMESPACE_JSON_FIELD =
            JsonFactory.newStringFieldDefinition("namespace", FieldType.REGULAR, JsonSchemaVersion.V_1);

    private static final JsonFieldDefinition<Long> COUNT_JSON_FIELD =
            JsonFactory.newLongFieldDefinition("count", FieldType.REGULAR, JsonSchemaVersion.V_1);

    private final String namespace;
    private final long count;

    /**
     * Creates a Namespace Entry.
     *
     * @param namespace the namespace.
     * @param count the Entry.
     */
    public SearchNamespaceResultEntry(final String namespace, final long count) {
        this.namespace = namespace;
        this.count = count;
    }

    /**
     * Creates a new {@code SearchNamespaceResultEntry} from a JSON string.
     *
     * @param jsonString the JSON string of which a new SearchNamespaceResultEntry is to be created.
     * @return the SearchNamespaceResultEntry which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} does not contain a JSON
     * object or if it is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonString} was not in the
     * expected 'SearchNamespaceResultEntry' format.
     */
    public static SearchNamespaceResultEntry fromJson(final String jsonString) {
        final JsonObject jsonObject = JsonFactory.newObject(jsonString);
        return fromJson(jsonObject);
    }

    /**
     * Creates a new {@code SearchNamespaceResultEntry} from a JSON Object.
     *
     * @param jsonObject the JSON object of which a new SearchNamespaceResultEntry is to be created.
     * @return the SearchNamespaceResultEntry which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} does not contain a JSON
     * object or if it is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected 'SearchNamespaceResultEntry' format.
     */
    public static SearchNamespaceResultEntry fromJson(final JsonObject jsonObject) {
        final String namespace = jsonObject.getValueOrThrow(NAMESPACE_JSON_FIELD);
        final long count = jsonObject.getValueOrThrow(COUNT_JSON_FIELD);

        return new SearchNamespaceResultEntry(namespace, count);
    }

    @Override
    public JsonObject toJson(final Predicate<JsonField> predicate) {
        final JsonSchemaVersion jsonSchemaVersion = JsonSchemaVersion.V_1;

        return JsonFactory.newObjectBuilder()
                .set(SCHEMA_VERSION_JSON_FIELD, jsonSchemaVersion.toInt(), predicate)
                .set(NAMESPACE_JSON_FIELD, namespace, predicate)
                .set(COUNT_JSON_FIELD, count, predicate)
                .build();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        return JsonFactory.newObjectBuilder()
                .set(SCHEMA_VERSION_JSON_FIELD, schemaVersion.toInt(), predicate)
                .set(NAMESPACE_JSON_FIELD, namespace, predicate)
                .set(COUNT_JSON_FIELD, count, predicate)
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
        final SearchNamespaceResultEntry that = (SearchNamespaceResultEntry) o;
        return count == that.count && Objects.equals(namespace, that.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, count);
    }

    /**
     * Get number of Things for this Report Entry.
     *
     * @return the number of Things.
     */
    public long getCount() {
        return count;
    }

    /**
     * Get Namespace for this Report Entry.
     *
     * @return the Namespace.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Get the Json Schema Version of this Report.
     *
     * @return the SCHEMA_VERSION.
     */
    public JsonFieldDefinition getSchemaVersion() {
        return SCHEMA_VERSION_JSON_FIELD;
    }

    @Override
    public String toString() {
        return "SearchNamespaceResultEntry{" +
                "namespace='" + namespace + '\'' +
                ", count=" + count +
                '}';
    }

}
