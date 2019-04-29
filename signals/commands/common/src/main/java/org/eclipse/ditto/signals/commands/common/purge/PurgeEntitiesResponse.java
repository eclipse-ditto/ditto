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
package org.eclipse.ditto.signals.commands.common.purge;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.signals.commands.common.CommonCommandResponse;

/**
 * Response to {@link PurgeEntities}.
 */
@Immutable
public final class PurgeEntitiesResponse extends CommonCommandResponse<PurgeEntitiesResponse> {

    /**
     * The type of the response.
     */
    public static final String TYPE = TYPE_PREFIX + PurgeEntities.NAME;

    private final String entityType;
    private final boolean successful;

    private PurgeEntitiesResponse(final CharSequence entityType, final boolean successful,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.entityType = checkNotNull(entityType).toString();
        this.successful = successful;
    }

    /**
     * Returns an instance which indicates a successful purge.
     *
     * @param entityType type of the entities represented by the returned response.
     * @param dittoHeaders the headers of the command which caused the returned response.
     * @return a response for a successful purge.
     */
    public static PurgeEntitiesResponse successful(final CharSequence entityType, final DittoHeaders dittoHeaders) {
        return new PurgeEntitiesResponse(entityType, true, dittoHeaders);
    }

    /**
     * Returns an instance which indicates that a failed purge.
     *
     * @param entityType type of the entities represented by the returned response.
     * @param dittoHeaders the headers of the command which caused the returned response.
     * @return a response for a failed purge.
     */
    public static PurgeEntitiesResponse failed(final CharSequence entityType, final DittoHeaders dittoHeaders) {
        return new PurgeEntitiesResponse(entityType, false, dittoHeaders);
    }

    /**
     * Creates a new instance from the given JSON object.
     *
     * @param jsonObject the JSON object.
     * @param headers the headers.
     * @return the deserialized response.
     */
    public static PurgeEntitiesResponse fromJson(final JsonObject jsonObject, final DittoHeaders headers) {
        return new CommandResponseJsonDeserializer<PurgeEntitiesResponse>(TYPE, jsonObject).deserialize(statusCode -> {
            final String parsedEntityType = jsonObject.getValueOrThrow(JsonFields.ENTITY_TYPE);
            final boolean parsedSuccessful = jsonObject.getValueOrThrow(JsonFields.SUCCESSFUL);

            return new PurgeEntitiesResponse(parsedEntityType, parsedSuccessful, headers);
        });
    }

    /**
     * The type of the entities.
     *
     * @return the type of the entities.
     */
    public String getEntityType() {
        return entityType;
    }

    /**
     * Indicates whether the entities were purged successfully.
     *
     * @return {@code true}, if successful.
     */
    public boolean isSuccessful() {
        return successful;
    }

    @Override
    public PurgeEntitiesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new PurgeEntitiesResponse(entityType, successful, dittoHeaders);
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
        final PurgeEntitiesResponse that = (PurgeEntitiesResponse) o;
        return Objects.equals(entityType, that.entityType) && successful == that.successful;
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PurgeEntitiesResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entityType, successful);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> aPredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(aPredicate);

        jsonObjectBuilder.set(JsonFields.ENTITY_TYPE, entityType, predicate);
        jsonObjectBuilder.set(JsonFields.SUCCESSFUL, successful, predicate);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", entityType=" + entityType +
                ", successful=" + successful +
                "]";
    }

    /**
     * Fields of this response.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * The type of the entities affected by this response.
         */
        public static final JsonFieldDefinition<String> ENTITY_TYPE =
                JsonFactory.newStringFieldDefinition("entityType", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
        /**
         * This JSON field indicates whether the entities were purged successfully.
         */
        public static final JsonFieldDefinition<Boolean> SUCCESSFUL =
                JsonFactory.newBooleanFieldDefinition("successful", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
