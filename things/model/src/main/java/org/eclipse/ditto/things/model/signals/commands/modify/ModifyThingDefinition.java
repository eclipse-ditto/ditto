/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;

/**
 * This command modifies the Definition of a Thing.
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingCommand.TYPE_PREFIX, name = ModifyThingDefinition.NAME)
public final class ModifyThingDefinition extends AbstractCommand<ModifyThingDefinition>
        implements ThingModifyCommand<ModifyThingDefinition> {

    /**
     * Name of the "Modify Thing definition" command.
     */
    public static final String NAME = "modifyDefinition";

    /**
     * Type of this command.
     */
    public static final String TYPE = ThingCommand.TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonValue> JSON_DEFINITION =
            JsonFactory.newJsonValueFieldDefinition("definition", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final ThingDefinition definition;

    private ModifyThingDefinition(final ThingId thingId, final ThingDefinition definition,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.thingId = checkNotNull(thingId, "Thing ID");
        this.definition = checkNotNull(definition, "definition is null");
    }


    /**
     * Returns a command for modifying a definition which is passed as argument.
     *
     * @param thingId the ID of the thing on which to modify the Definition.
     * @param definition the Definition to set.
     * @param dittoHeaders the headers of the command.
     * @return a command for modifying the provided new Definition.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ModifyThingDefinition of(final ThingId thingId, final ThingDefinition definition,
            final DittoHeaders dittoHeaders) {
        return new ModifyThingDefinition(thingId, definition, dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyThingDefinition} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.base.model.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static ModifyThingDefinition fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyThingDefinition} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.base.model.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static ModifyThingDefinition fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyThingDefinition>(TYPE, jsonObject).deserialize(() -> {
            final String extractedThingId = jsonObject.getValueOrThrow(ThingCommand.JsonFields.JSON_THING_ID);
            final ThingId thingId = ThingId.of(extractedThingId);
            final JsonValue readDefinition = jsonObject.getValueOrThrow(JSON_DEFINITION);
            final ThingDefinition definition;
            if (readDefinition.isNull()) {
                definition = ThingsModelFactory.nullDefinition();
            } else {
                definition = ThingsModelFactory.newDefinition(readDefinition.asString());
            }

            return of(thingId, definition, dittoHeaders);
        });
    }

    /**
     * ModifyThingDefinition is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }


    /**
     * Returns the new Definition.
     *
     * @return the new Definition.
     */
    public ThingDefinition getDefinition() {
        return definition;
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(JsonValue.of(definition));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = Thing.JsonFields.DEFINITION.getPointer().toString();
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommand.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_DEFINITION, definition.toJson(), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyThingDefinition setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, definition, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, definition);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final ModifyThingDefinition that = (ModifyThingDefinition) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(definition, that.definition)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyThingDefinition);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", definition="
                + definition + "]";
    }

}
