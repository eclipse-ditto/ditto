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
 * Response to {@link PurgeNamespace} for speeding up namespace purge.
 */
@Immutable
public final class PurgeNamespaceResponse extends AbstractNamespaceCommandResponse<PurgeNamespaceResponse> {

    /**
     * The type of the {@code PurgeNamespaceResponse}.
     */
    public static final String TYPE = TYPE_PREFIX + PurgeNamespace.NAME;

    private final boolean successful;

    private PurgeNamespaceResponse(final CharSequence namespace,
            final CharSequence resourceType,
            final boolean successful,
            final DittoHeaders dittoHeaders) {

        super(namespace, resourceType, TYPE, HttpStatusCode.OK, dittoHeaders);
        this.successful = successful;
    }

    /**
     * Returns an instance of {@code PurgeNamespaceResponse} which indicates a successful namespace purge.
     *
     * @param namespace the namespace the returned response relates to.
     * @param resourceType type of the {@code Resource} represented by the returned response.
     * @param dittoHeaders the headers of the command which caused the returned response.
     * @return a response for a successful namespace purge.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} or {@code resourceType} is empty.
     */
    public static PurgeNamespaceResponse successful(final CharSequence namespace, final CharSequence resourceType,
            final DittoHeaders dittoHeaders) {

        return new PurgeNamespaceResponse(namespace, resourceType, true, dittoHeaders);
    }

    /**
     * Returns an instance of {@code PurgeNamespaceResponse} which indicates that a namespace purge failed.
     *
     * @param namespace the namespace the returned response relates to.
     * @param resourceType type of the {@code Resource} represented by the returned response.
     * @param dittoHeaders the headers of the command which caused the returned response.
     * @return a response for a failed namespace purge.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} or {@code resourceType} is empty.
     */
    public static PurgeNamespaceResponse failed(final CharSequence namespace, final CharSequence resourceType,
            final DittoHeaders dittoHeaders) {

        return new PurgeNamespaceResponse(namespace, resourceType, false, dittoHeaders);
    }

    /**
     * Creates a new {@code PurgeNamespaceResponse} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the PurgeNamespaceResponse is to be created.
     * @param headers the headers.
     * @return the deserialized response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} was not in the expected format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * <ul>
     *     <li>{@link org.eclipse.ditto.signals.commands.namespaces.NamespaceCommandResponse.JsonFields#NAMESPACE},</li>
     *     <li>{@link org.eclipse.ditto.signals.commands.namespaces.NamespaceCommandResponse.JsonFields#RESOURCE_TYPE} or</li>
     *     <li>{@link JsonFields#SUCCESSFUL}.</li>
     * </ul>
     */
    public static PurgeNamespaceResponse fromJson(final JsonObject jsonObject, final DittoHeaders headers) {
        return new CommandResponseJsonDeserializer<PurgeNamespaceResponse>(TYPE, jsonObject).deserialize(statusCode -> {
            final String namespace = jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.NAMESPACE);
            final String resourceType = jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.RESOURCE_TYPE);
            final Boolean isSuccessful = jsonObject.getValueOrThrow(JsonFields.SUCCESSFUL);

            return new PurgeNamespaceResponse(namespace, resourceType, isSuccessful, headers);
        });
    }

    /**
     * Indicates whether the namespace was purged successfully.
     *
     * @return {@code true} if the namespace was purged, {@code false} else.
     */
    public boolean isSuccessful() {
        return successful;
    }

    @Override
    public PurgeNamespaceResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        if (Objects.equals(getDittoHeaders(), dittoHeaders)) {
            return this;
        }
        return new PurgeNamespaceResponse(getNamespace(), getResourceType(), isSuccessful(), dittoHeaders);
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
        final PurgeNamespaceResponse that = (PurgeNamespaceResponse) o;
        return that.canEqual(this) && successful == that.successful && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PurgeNamespaceResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), successful);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, predicate);
        jsonObjectBuilder.set(JsonFields.SUCCESSFUL, isSuccessful(), schemaVersion.and(predicate));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", successful=" + successful + "]";
    }

    /**
     * This class contains definitions for all specific fields of a {@code PurgeNamespaceResponse}'s JSON
     * representation.
     */
    @Immutable
    public static final class JsonFields extends NamespaceCommandResponse.JsonFields {

        /**
         * This JSON field indicates whether the namespace was purged successfully.
         */
        public static final JsonFieldDefinition<Boolean> SUCCESSFUL =
                JsonFactory.newBooleanFieldDefinition("successful", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
