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
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;

/**
 * This command modifies all {@code Thing}'s attributes at once.
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingCommand.TYPE_PREFIX, name = ModifyAttributes.NAME)
public final class ModifyAttributes extends AbstractCommand<ModifyAttributes>
        implements ThingModifyCommand<ModifyAttributes> {

    /**
     * Name of the "Modify Thing Attributes" command.
     */
    public static final String NAME = "modifyAttributes";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_ATTRIBUTES =
            JsonFactory.newJsonObjectFieldDefinition("attributes", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final Attributes attributes;

    private ModifyAttributes(final Attributes attributes, final ThingId thingId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.thingId = thingId;
        this.attributes = checkNotNull(attributes, "Attributes");

        final JsonObject attributesJsonObject = attributes.toJson();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                attributesJsonObject::getUpperBoundForStringSize,
                () -> attributesJsonObject.toString().length(),
                () -> dittoHeaders);
    }

    /**
     * Returns a command for modifying an attributes object which is passed as argument.
     *
     * @param thingId the ID of the thing on which to modify the attributes.
     * @param newAttributesObject the value of the attributes to modify.
     * @param dittoHeaders the headers of the command.
     * @return a command for modifying the provided new attributes.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     */
    public static ModifyAttributes of(final ThingId thingId, final Attributes newAttributesObject,
            final DittoHeaders dittoHeaders) {

        return new ModifyAttributes(newAttributesObject, thingId, dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyAttributes} from a JSON string.
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
    public static ModifyAttributes fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyAttributes} from a JSON object.
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
    public static ModifyAttributes fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyAttributes>(TYPE, jsonObject).deserialize(() -> {
            final String extractedThingId = jsonObject.getValueOrThrow(ThingCommand.JsonFields.JSON_THING_ID);
            final ThingId thingId = ThingId.of(extractedThingId);
            final JsonObject attributesJsonObject = jsonObject.getValueOrThrow(JSON_ATTRIBUTES);
            final Attributes extractedAttributes = ThingsModelFactory.newAttributes(attributesJsonObject);

            return of(thingId, extractedAttributes, dittoHeaders);
        });
    }

    /**
     * Returns the {@code Attributes} to modify.
     *
     * @return the Attributes.
     */
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/attributes");
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(attributes.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommand.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_ATTRIBUTES, attributes, predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyAttributes setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, attributes, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, attributes);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final ModifyAttributes that = (ModifyAttributes) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) &&
                Objects.equals(attributes, that.attributes)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyAttributes);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", attributes="
                + attributes + "]";
    }

}
