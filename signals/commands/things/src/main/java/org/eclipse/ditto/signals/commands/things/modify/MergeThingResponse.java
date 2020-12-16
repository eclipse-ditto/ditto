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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

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
    public static final String TYPE = TYPE_PREFIX + MergeThing.NAME;

    private final ThingId thingId;
    private final JsonPointer path;
    private final JsonValue value;

    private MergeThingResponse(final ThingId thingId, final JsonPointer path, final JsonValue value,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.path = checkNotNull(path, "path");
        this.value = checkNotNull(value, "value");
    }

    /**
     * Creates a command response for merged thing.
     *
     * @param thingId the thing id.
     * @param path the path where the changes were applied.
     * @param value the value describing the changes that were merged into the existing thing.
     * @param dittoHeaders the ditto headers.
     * @return the created {@code MergeThingResponse}.
     */
    public static MergeThingResponse of(final ThingId thingId, final JsonPointer path, final JsonValue value,
            final DittoHeaders dittoHeaders) {
        return new MergeThingResponse(thingId, path, value, dittoHeaders);
    }

    @Override
    public ThingId getThingEntityId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return path;
    }

    @Override
    public MergeThingResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, path, value, dittoHeaders);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(value);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicateParam) {
        final Predicate<JsonField> predicate = schemaVersion.and(predicateParam);
        jsonObjectBuilder.set(ThingModifyCommand.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(MergeThingResponse.JsonFields.JSON_PATH, path.toString(), predicate);
        jsonObjectBuilder.set(MergeThingResponse.JsonFields.JSON_VALUE, value, predicate);
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
     * {@link ThingModifyCommand.JsonFields#JSON_THING_ID}, {@link JsonFields#JSON_PATH} or
     * {@link JsonFields#JSON_VALUE}.
     */
    public static MergeThingResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<MergeThingResponse>(TYPE, jsonObject)
                .deserialize(statusCode -> {
                    final String extractedThingId =
                            jsonObject.getValueOrThrow(ThingModifyCommand.JsonFields.JSON_THING_ID);
                    final String path = jsonObject.getValueOrThrow(JsonFields.JSON_PATH);
                    final JsonValue value = jsonObject.getValueOrThrow(JsonFields.JSON_VALUE);

                    return new MergeThingResponse(ThingId.of(extractedThingId), JsonPointer.of(path), value,
                            dittoHeaders);
                });
    }

    /**
     * An enumeration of the JSON fields of a {@code MergeThingResponse} command.
     */
    private static class JsonFields {
        static final JsonFieldDefinition<String> JSON_PATH =
                JsonFactory.newStringFieldDefinition("path", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonValue> JSON_VALUE =
                JsonFactory.newJsonValueFieldDefinition("value", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final MergeThingResponse that = (MergeThingResponse) o;
        return that.canEqual(this) && thingId.equals(that.thingId) &&
                path.equals(that.path) &&
                value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, path, value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", thingId=" + thingId +
                ", path=" + path +
                ", value=" + value +
                "]";
    }
}
