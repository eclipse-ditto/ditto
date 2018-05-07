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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.ThingIdValidator;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command modifies all {@code Thing}'s attributes at once.
 */
@Immutable
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
            JsonFactory.newJsonObjectFieldDefinition("attributes", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final Attributes attributes;

    private ModifyAttributes(final Attributes attributes, final String thingId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        ThingIdValidator.getInstance().accept(thingId, dittoHeaders);
        this.thingId = thingId;
        this.attributes = checkNotNull(attributes, "Attributes");
    }

    /**
     * Returns a command for modifying an attributes object which is passed as argument.
     *
     * @param thingId the ID of the thing on which to modify the attributes.
     * @param newAttributesObject the value of the attributes to modify.
     * @param dittoHeaders the headers of the command.
     * @return a command for modifying the provided new attributes.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static ModifyAttributes of(final String thingId, final Attributes newAttributesObject,
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
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
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
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static ModifyAttributes fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyAttributes>(TYPE, jsonObject).deserialize(() -> {
            final String thingId = jsonObject.getValueOrThrow(ThingModifyCommand.JsonFields.JSON_THING_ID);
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
    public String getThingId() {
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
        jsonObjectBuilder.set(ThingModifyCommand.JsonFields.JSON_THING_ID, thingId, predicate);
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
