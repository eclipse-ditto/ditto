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
package org.eclipse.ditto.signals.commands.namespaces;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to {@link org.eclipse.ditto.signals.commands.namespaces.QueryNamespaceEmptiness} for indicating whether a
 * namespace is empty.
 */
@Immutable
public final class QueryNamespaceEmptinessResponse
        extends AbstractNamespaceCommandResponse<QueryNamespaceEmptinessResponse> {

    /**
     * Type of the command response.
     */
    public static final String TYPE = TYPE_PREFIX + QueryNamespaceEmptiness.NAME;

    private final boolean empty;

    private QueryNamespaceEmptinessResponse(final CharSequence namespace,
            final CharSequence resourceType,
            final boolean empty,
            final DittoHeaders dittoHeaders) {

        super(namespace, resourceType, TYPE, HttpStatusCode.OK, dittoHeaders);
        this.empty = empty;
    }

    /**
     * Returns an instance of {@code QueryNamespaceEmptinessResponse} which indicates an empty namespace.
     *
     * @param namespace the namespace the returned response relates to.
     * @param resourceType type of the {@code Resource} represented by the returned response.
     * @param dittoHeaders the headers of the command which caused the returned response.
     * @return a response for an empty namespace.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} or {@code resourceType} is empty.
     */
    public static QueryNamespaceEmptinessResponse empty(final CharSequence namespace, final CharSequence resourceType,
            final DittoHeaders dittoHeaders) {

        return new QueryNamespaceEmptinessResponse(namespace, resourceType, true, dittoHeaders);
    }

    /**
     * Returns an instance of {@code QueryNamespaceEmptinessResponse} which indicates a non-empty namespace.
     *
     * @param namespace the namespace the returned response relates to.
     * @param resourceType type of the {@code Resource} represented by the returned response.
     * @param dittoHeaders the headers of the command which caused the returned response.
     * @return a response for a non-empty namespace.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} or {@code resourceType} is empty.
     */
    public static QueryNamespaceEmptinessResponse notEmpty(final CharSequence namespace, final CharSequence resourceType,
            final DittoHeaders dittoHeaders) {

        return new QueryNamespaceEmptinessResponse(namespace, resourceType, false, dittoHeaders);
    }

    /**
     * Creates a new {@code QueryNamespaceEmptinessResponse} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the QueryNamespaceEmptinessResponse is to be created.
     * @param headers the headers.
     * @return the deserialized response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} was not in the expected format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * <ul>
     *     <li>{@link org.eclipse.ditto.signals.commands.namespaces.NamespaceCommandResponse.JsonFields#NAMESPACE},</li>
     *     <li>{@link org.eclipse.ditto.signals.commands.namespaces.NamespaceCommandResponse.JsonFields#RESOURCE_TYPE} or</li>
     *     <li>{@link JsonFields#EMPTY}.</li>
     * </ul>
     */
    public static QueryNamespaceEmptinessResponse fromJson(final JsonObject jsonObject, final DittoHeaders headers) {
        return new CommandResponseJsonDeserializer<QueryNamespaceEmptinessResponse>(TYPE, jsonObject).deserialize(
                statusCode -> {
                    final String namespace = jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.NAMESPACE);
                    final String resourceType =
                            jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.RESOURCE_TYPE);
                    final Boolean isEmpty = jsonObject.getValueOrThrow(JsonFields.EMPTY);

                    return new QueryNamespaceEmptinessResponse(namespace, resourceType, isEmpty, headers);
                });
    }

    /**
     * Indicates whether the namespace is empty.
     *
     * @return {@code true} if the namespace is empty, {@code false} if the namespace has data.
     */
    public boolean isEmpty() {
        return empty;
    }

    @Override
    public QueryNamespaceEmptinessResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        if (Objects.equals(getDittoHeaders(), dittoHeaders)) {
            return this;
        }
        return new QueryNamespaceEmptinessResponse(getNamespace(), getResourceType(), empty, dittoHeaders);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final QueryNamespaceEmptinessResponse that = (QueryNamespaceEmptinessResponse) o;
        return that.canEqual(this) && empty == that.empty && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof QueryNamespaceEmptinessResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), empty);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, predicate);
        jsonObjectBuilder.set(JsonFields.EMPTY, empty, schemaVersion.and(predicate));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", empty=" + empty + "]";
    }

    /**
     * This class contains definitions for all specific fields of a {@code QueryNamespaceEmptinessResponse}'s JSON
     * representation.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * This JSON field indicates whether the namespace is empty.
         */
        public static final JsonFieldDefinition<Boolean> EMPTY = JsonFactory.newBooleanFieldDefinition("empty",
                FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_1);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
