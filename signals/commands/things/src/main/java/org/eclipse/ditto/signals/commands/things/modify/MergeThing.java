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
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * /**
 * This command merges an existing Thing with the supplied modification. The command contains a {@code path} and a
 * {@code value} describing the change that should be applied. The {@code value} at the given {@code path} is merged
 * with the existing thing according to <a href="https://tools.ietf.org/html/rfc7396">RFC7396 - JSON Merge Patch</a>.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = MergeThing.TYPE_PREFIX, name = MergeThing.NAME)
public final class MergeThing extends AbstractCommand<MergeThing> implements ThingModifyCommand<MergeThing> {

    /**
     * Name of the "Merge Thing" command.
     */
    public static final String NAME = "mergeThing";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final ThingId thingId;
    private final JsonPointer path;
    private final JsonValue value;

    private MergeThing(final ThingId thingId, final JsonPointer path, final JsonValue value,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.path = checkNotNull(path, "path");
        this.value = checkNotNull(value, "value");
    }

    /**
     * Creates a command for merging the thing identified by {@code thingId} with the changes described by {@code
     * path}* and {@code value}.
     *
     * @param thingId the thing id
     * @param path the path where the changes are applied
     * @param value the value describing the changes that are merged into the existing thing
     * @param dittoHeaders the ditto headers
     * @return the created {@link org.eclipse.ditto.signals.commands.things.modify.MergeThing} command
     */
    public static MergeThing of(final ThingId thingId, final JsonPointer path, final JsonValue value,
            final DittoHeaders dittoHeaders) {
        return new MergeThing(thingId, path, value, dittoHeaders);
    }

    /**
     * @return the path where the changes are applied
     */
    public JsonPointer getPath() {
        return path;
    }

    /**
     * @return the value describing the changes that are applied to the existing thing
     */
    public JsonValue getValue() {
        return value;
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
    public boolean changesAuthorization() {
        // TODO verify! but this should be the only path that changes authorization
        return Thing.JsonFields.POLICY_ID.getPointer().equals(path);
    }

    @Override
    public Category getCategory() {
        // TODO what about null values? they actually _delete_ properties. check what are the impacts!
        return Category.MODIFY;
    }

    @Override
    public MergeThing setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, path, value, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicateParameter) {
        final Predicate<JsonField> predicate = schemaVersion.and(predicateParameter);
        jsonObjectBuilder.set(ThingModifyCommand.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_PATH, path.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_VALUE, value, predicate);
    }

    /**
     * Creates a new {@code MergeThing} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the {@code MergeThing} command created from JSON
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain a field for
     * {@link ThingModifyCommand.JsonFields#JSON_THING_ID}, {@link JsonFields#JSON_PATH} or {@link JsonFields#JSON_VALUE}.
     */
    public static MergeThing fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<MergeThing>(TYPE, jsonObject).deserialize(() -> {
            final String thingId = jsonObject.getValueOrThrow(ThingModifyCommand.JsonFields.JSON_THING_ID);
            final String path = jsonObject.getValueOrThrow(JsonFields.JSON_PATH);
            final JsonValue jsonValue = jsonObject.getValueOrThrow(JsonFields.JSON_VALUE);

            return of(ThingId.of(thingId), JsonPointer.of(path), jsonValue, dittoHeaders);
        });
    }

    /**
     * An enumeration of the JSON fields of a {@code MergeThing} command.
     */
    @Immutable
    public static class JsonFields {

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
        final MergeThing that = (MergeThing) o;
        return that.canEqual(this) && thingId.equals(that.thingId) && path.equals(that.path) &&
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
