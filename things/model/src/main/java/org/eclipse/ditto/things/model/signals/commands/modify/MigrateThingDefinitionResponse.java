/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link MigrateThingDefinition} command.
 */
@Immutable
@JsonParsableCommandResponse(type = MigrateThingDefinitionResponse.TYPE)
public final class MigrateThingDefinitionResponse extends AbstractCommandResponse<MigrateThingDefinitionResponse>
        implements ThingModifyCommandResponse<MigrateThingDefinitionResponse> {

    public static final String TYPE = ThingCommandResponse.TYPE_PREFIX + MigrateThingDefinition.NAME;

    private static final CommandResponseJsonDeserializer<MigrateThingDefinitionResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(JsonFields.JSON_THING_ID)),
                                jsonObject.getValueOrThrow(JsonFields.JSON_PATCH),
                                MergeStatus.fromString(jsonObject.getValueOrThrow(JsonFields.JSON_MERGE_STATUS)),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final JsonObject patch;
    private final MergeStatus mergeStatus;

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private MigrateThingDefinitionResponse(final ThingId thingId,
            final JsonObject patch,
            final MergeStatus mergeStatus,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.patch = checkNotNull(patch, "patch");
        this.mergeStatus = checkNotNull(mergeStatus, "mergeStatus");
    }

    /**
     * Helper class for defining JSON field names.
     */
    @Immutable
    public static final class JsonFields {
        public static final JsonFieldDefinition<String> JSON_THING_ID =
                JsonFactory.newStringFieldDefinition("thingId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        public static final JsonFieldDefinition<JsonObject> JSON_PATCH =
                JsonFactory.newJsonObjectFieldDefinition("patch", FieldType.REGULAR, JsonSchemaVersion.V_2);

        public static final JsonFieldDefinition<String> JSON_MERGE_STATUS =
                JsonFactory.newStringFieldDefinition("mergeStatus", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }

    /**
     * Enum for possible migration statuses.
     */
    public enum MergeStatus {
        APPLIED,
        DRY_RUN;

        public static MergeStatus fromString(String status) {
            for (MergeStatus s : values()) {
                if (s.name().equalsIgnoreCase(status)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Unknown MergeStatus: " + status);
        }
    }

    /**
     * Creates a response indicating that the migration was applied successfully.
     *
     * @param thingId      The Thing ID of the migrated entity.
     * @param patch        The JSON patch applied.
     * @param dittoHeaders The headers for the response.
     * @return An instance of {@link MigrateThingDefinitionResponse} indicating a successful migration.
     */
    public static MigrateThingDefinitionResponse applied(final ThingId thingId,
            final JsonObject patch,
            final DittoHeaders dittoHeaders) {
        return newInstance(thingId, patch, MergeStatus.APPLIED, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response for a dry-run execution of the migration.
     *
     * @param thingId      The Thing ID being checked.
     * @param patch        The JSON patch that would have been applied.
     * @param dittoHeaders The headers for the response.
     * @return An instance of {@link MigrateThingDefinitionResponse} indicating a dry-run migration.
     */
    public static MigrateThingDefinitionResponse dryRun(final ThingId thingId,
            final JsonObject patch,
            final DittoHeaders dittoHeaders) {
        return newInstance(thingId, patch, MergeStatus.DRY_RUN, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a new instance of {@link MigrateThingDefinitionResponse}.
     *
     * @param thingId      The Thing ID being modified.
     * @param patch        The JSON patch applied to the Thing.
     * @param mergeStatus  The status of the migration.
     * @param httpStatus   The HTTP status code.
     * @param dittoHeaders The headers for the response.
     * @return A new instance of {@link MigrateThingDefinitionResponse}.
     */
    public static MigrateThingDefinitionResponse newInstance(final ThingId thingId,
            final JsonObject patch,
            final MergeStatus mergeStatus,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new MigrateThingDefinitionResponse(thingId, patch, mergeStatus,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        MigrateThingDefinitionResponse.class),
                dittoHeaders);
    }

    /**
     * Parses a {@code MigrateThingDefinitionResponse} from JSON.
     *
     * @param jsonObject   The JSON object.
     * @param dittoHeaders The headers associated with the command.
     * @return A {@link MigrateThingDefinitionResponse} instance.
     */
    public static MigrateThingDefinitionResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * Retrieves the JSON patch applied during migration.
     *
     * @return The JSON patch.
     */
    public JsonObject getPatch() {
        return patch;
    }

    /**
     * Retrieves the status of the migration (e.g., {@code APPLIED}, {@code DRY_RUN}).
     *
     * @return The merge status.
     */
    public MergeStatus getMergeStatus() {
        return mergeStatus;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_PATCH, patch, predicate);
        jsonObjectBuilder.set(JsonFields.JSON_MERGE_STATUS, mergeStatus.name(), predicate);
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MigrateThingDefinitionResponse that = (MigrateThingDefinitionResponse) obj;
        return thingId.equals(that.thingId) &&
                patch.equals(that.patch) &&
                mergeStatus == that.mergeStatus &&
                super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, patch, mergeStatus);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", thingId=" + thingId +
                ", patch=" + patch +
                ", mergeStatus=" + mergeStatus.name() +
                "]";
    }

    @Override
    public MigrateThingDefinitionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, patch, mergeStatus, getHttpStatus(), dittoHeaders);
    }

    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    @Override
    public MigrateThingDefinitionResponse setEntity(final JsonValue entity) {
        JsonObject jsonObject = entity.asObject();
        return new MigrateThingDefinitionResponse(
                ThingId.of(jsonObject.getValueOrThrow(JsonFields.JSON_THING_ID)),
                jsonObject.getValueOrThrow(JsonFields.JSON_PATCH),
                MergeStatus.fromString(jsonObject.getValueOrThrow(JsonFields.JSON_MERGE_STATUS)),
                getHttpStatus(),
                getDittoHeaders()
        );
    }

    @Override
    public Optional<JsonValue> getEntity(JsonSchemaVersion schemaVersion) {
        return Optional.of(JsonObject.newBuilder()
                .set(JsonFields.JSON_THING_ID, thingId.toString())
                .set(JsonFields.JSON_PATCH, patch)
                .set(JsonFields.JSON_MERGE_STATUS, mergeStatus.name())
                .build());
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }
}


