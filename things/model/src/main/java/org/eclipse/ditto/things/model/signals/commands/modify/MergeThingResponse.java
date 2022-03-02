/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.base.model.signals.UnsupportedSchemaVersionException;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link MergeThing} command.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableCommandResponse(type = MergeThingResponse.TYPE)
public final class MergeThingResponse extends AbstractCommandResponse<MergeThingResponse>
        implements ThingModifyCommandResponse<MergeThingResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = ThingCommandResponse.TYPE_PREFIX + MergeThing.NAME;

    private static final HttpStatus HTTP_STATUS = HttpStatus.NO_CONTENT;

    private static final CommandResponseJsonDeserializer<MergeThingResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                JsonPointer.of(jsonObject.getValueOrThrow(JsonFields.JSON_PATH)),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    private final JsonPointer path;

    private MergeThingResponse(final ThingId thingId,
            final JsonPointer path,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, FeatureToggle.checkMergeFeatureEnabled(TYPE, dittoHeaders));
        this.thingId = checkNotNull(thingId, "thingId");
        this.path = checkNotNull(path, "path");
        checkSchemaVersion();
    }

    private void checkSchemaVersion() {
        final JsonSchemaVersion implementedSchemaVersion = getImplementedSchemaVersion();
        if (!implementsSchemaVersion(implementedSchemaVersion)) {
            throw UnsupportedSchemaVersionException.newBuilder(implementedSchemaVersion).build();
        }
    }

    /**
     * Creates a command response for merged thing.
     *
     * @param thingId the thing id.
     * @param path the path where the changes were applied.
     * @param dittoHeaders the ditto headers.
     * @return the created {@code MergeThingResponse}.
     */
    public static MergeThingResponse of(final ThingId thingId,
            final JsonPointer path,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, path, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code MergeThingResponse} for the specified arguments.
     *
     * @param thingId the ID of the merged thing.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code MergeThingResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code MergeThingResponse}.
     * @since 2.3.0
     */
    public static MergeThingResponse newInstance(final ThingId thingId,
            final JsonPointer path,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new MergeThingResponse(thingId,
                path,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        MergeThingResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a new {@code MergeThingResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command response is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the {@code MergeThingResponse} command created from JSON.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain a field for
     * {@link org.eclipse.ditto.things.model.signals.commands.ThingCommand.JsonFields#JSON_THING_ID} or
     * {@link MergeThingResponse.JsonFields#JSON_PATH}.
     */
    public static MergeThingResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return path;
    }

    @Override
    public MergeThingResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, path, getHttpStatus(), dittoHeaders);
    }

    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicateParam) {

        final Predicate<JsonField> predicate = schemaVersion.and(predicateParam);
        jsonObjectBuilder.set(ThingCommand.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_PATH, path.toString(), predicate);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final MergeThingResponse that = (MergeThingResponse) o;
        return that.canEqual(this) && thingId.equals(that.thingId) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, path);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof MergeThingResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", thingId=" + thingId +
                ", path=" + path +
                "]";
    }

    /**
     * An enumeration of the JSON fields of a {@code MergeThingResponse} command.
     */
    private static final class JsonFields {

        static final JsonFieldDefinition<String> JSON_PATH =
                JsonFieldDefinition.ofString("path", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
