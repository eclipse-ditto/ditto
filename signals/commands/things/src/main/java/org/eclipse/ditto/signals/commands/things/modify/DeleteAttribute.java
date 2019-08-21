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
package org.eclipse.ditto.signals.commands.things.modify;

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
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command deletes a thing's attribute. Contains the key of the attribute to delete and the ID of the Thing.
 */
@Immutable
@JsonParsableCommand(typePrefix = DeleteAttribute.TYPE_PREFIX, name = DeleteAttribute.NAME)
public final class DeleteAttribute extends AbstractCommand<DeleteAttribute>
        implements ThingModifyCommand<DeleteAttribute> {

    /**
     * Name of the "Delete Thing Attribute" command.
     */
    public static final String NAME = "deleteAttribute";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_ATTRIBUTE =
            JsonFactory.newStringFieldDefinition("attribute", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final JsonPointer attributePointer;

    private DeleteAttribute(final ThingId thingId, final JsonPointer attributePointer,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.thingId = checkNotNull(thingId, "Thing ID");
        this.attributePointer = checkNotNull(attributePointer, "key of the attribute to be deleted");
    }

    /**
     * Returns a command for deleting a Thing's attribute which can be accessed with the given authorization context.
     * The attribute's key and the Thing's ID are passed as arguments.
     *
     * @param thingId the thing's key.
     * @param attributeJsonPointer the JSON pointer of the attribute key to delete.
     * @param dittoHeaders the headers of the command.
     * @return a command for deleting a Thing's attribute.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     * @deprecated Thing ID is now typed. Use
     * {@link #of(org.eclipse.ditto.model.things.ThingId, org.eclipse.ditto.json.JsonPointer, org.eclipse.ditto.model.base.headers.DittoHeaders)}
     * instead.
     */
    @Deprecated
    public static DeleteAttribute of(final String thingId, final JsonPointer attributeJsonPointer,
            final DittoHeaders dittoHeaders) {

        return of(ThingId.of(thingId), attributeJsonPointer, dittoHeaders);
    }

    /**
     * Returns a command for deleting a Thing's attribute which can be accessed with the given authorization context.
     * The attribute's key and the Thing's ID are passed as arguments.
     *
     * @param thingId the thing's key.
     * @param attributeJsonPointer the JSON pointer of the attribute key to delete.
     * @param dittoHeaders the headers of the command.
     * @return a command for deleting a Thing's attribute.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     */
    public static DeleteAttribute of(final ThingId thingId, final JsonPointer attributeJsonPointer,
            final DittoHeaders dittoHeaders) {

        return new DeleteAttribute(thingId, attributeJsonPointer, dittoHeaders);
    }

    /**
     * Creates a new {@code DeleteAttribute} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if {@code thingId} did not comply to {@link
     * org.eclipse.ditto.model.base.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static DeleteAttribute fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code DeleteAttribute} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if {@code thingId} did not comply to {@link
     * org.eclipse.ditto.model.base.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static DeleteAttribute fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<DeleteAttribute>(TYPE, jsonObject).deserialize(() -> {
            final String extractedThingId = jsonObject.getValueOrThrow(ThingModifyCommand.JsonFields.JSON_THING_ID);
            final ThingId thingId = ThingId.of(extractedThingId);
            final String extractedPointerString = jsonObject.getValueOrThrow(JSON_ATTRIBUTE);
            final JsonPointer extractedPointer = JsonFactory.newPointer(extractedPointerString);

            return of(thingId, extractedPointer, dittoHeaders);
        });
    }

    /**
     * Returns the JSON pointer of the attribute to delete.
     *
     * @return the JSON pointer of the attribute to delete.
     */
    public JsonPointer getAttributePointer() {
        return attributePointer;
    }

    @Override
    public ThingId getThingEntityId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/attributes" + attributePointer;
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommand.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_ATTRIBUTE, attributePointer.toString(), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public DeleteAttribute setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, attributePointer, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, attributePointer);
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
        final DeleteAttribute that = (DeleteAttribute) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(attributePointer, that.attributePointer) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DeleteAttribute;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", attributePointer="
                + attributePointer + "]";
    }

}
