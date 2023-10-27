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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.WithFeatureId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;

/**
 * This command deletes a single desired property of a {@link org.eclipse.ditto.things.model.Feature}'s desired properties.
 *
 * @since 1.5.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingCommand.TYPE_PREFIX, name = DeleteFeatureDesiredProperty.NAME)
public final class DeleteFeatureDesiredProperty extends AbstractCommand<DeleteFeatureDesiredProperty> implements
        ThingModifyCommand<DeleteFeatureDesiredProperty>, WithFeatureId {

    /**
     * Name of the "Delete Feature Desired Property" command.
     */
    public static final String NAME = "deleteFeatureDesiredProperty";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_DESIRED_PROPERTY =
            JsonFactory.newStringFieldDefinition("desiredProperty", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final String featureId;
    private final JsonPointer desiredPropertyPointer;

    private DeleteFeatureDesiredProperty(final ThingId thingId, final CharSequence featureId,
            final JsonPointer desiredPropertyPointer,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = checkNotNull(featureId == null || featureId.toString().isEmpty() ? null : featureId.toString(),
                "featureId");
        this.desiredPropertyPointer = checkDesiredPropertyPointer(desiredPropertyPointer);
    }

    private JsonPointer checkDesiredPropertyPointer(final JsonPointer desiredPropertyPointer) {
        checkNotNull(desiredPropertyPointer, "Desired property JsonPointer");
        return ThingsModelFactory.validateFeaturePropertyPointer(desiredPropertyPointer);
    }

    /**
     * Returns a Command for deleting a Feature's desired property on a Thing.
     *
     * @param thingId the {@code Thing}'s ID whose {@code Feature}'s desired property to delete.
     * @param featureId the {@code Feature}'s ID whose desired property to delete.
     * @param desiredPropertyPointer the JSON pointer of the desired property key to delete.
     * @param dittoHeaders the headers of the command.
     * @return a Command for deleting the provided desired property.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of {@code desiredPropertyPointer} are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static DeleteFeatureDesiredProperty of(final ThingId thingId, final CharSequence featureId,
            final JsonPointer desiredPropertyPointer, final DittoHeaders dittoHeaders) {

        return new DeleteFeatureDesiredProperty(thingId, featureId, desiredPropertyPointer, dittoHeaders);
    }

    /**
     * Creates a new {@code DeleteFeatureDesiredProperty} from a JSON string.
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
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of desired property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static DeleteFeatureDesiredProperty fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code DeleteFeatureDesiredProperty} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.base.model.entity.id.RegexPatterns#ID_REGEX}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of desired property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static DeleteFeatureDesiredProperty fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<DeleteFeatureDesiredProperty>(TYPE, jsonObject).deserialize(() -> {
            final String extractedThingId = jsonObject.getValueOrThrow(ThingCommand.JsonFields.JSON_THING_ID);
            final ThingId thingId = ThingId.of(extractedThingId);
            final String extractedFeatureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
            final String extractedPointerString = jsonObject.getValueOrThrow(JSON_DESIRED_PROPERTY);
            final JsonPointer extractedPointer = JsonFactory.newPointer(extractedPointerString);

            return of(thingId, extractedFeatureId, extractedPointer, dittoHeaders);
        });
    }

    /**
     * Returns the JSON pointer of the desired property to delete.
     *
     * @return the JSON pointer of the desired property to delete.
     */
    public JsonPointer getDesiredPropertyPointer() {
        return desiredPropertyPointer;
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    /**
     * DeleteFeatureDesiredProperty is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId + "/desiredProperties" + desiredPropertyPointer;
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommand.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_DESIRED_PROPERTY, desiredPropertyPointer.toString(), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public DeleteFeatureDesiredProperty setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, featureId, desiredPropertyPointer, dittoHeaders);
    }

    @Override
    public DeleteFeatureDesiredProperty setEntity(final JsonValue entity) {
        return this;
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, desiredPropertyPointer);
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
        final DeleteFeatureDesiredProperty that = (DeleteFeatureDesiredProperty) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) && Objects.equals(featureId, that.featureId)
                && Objects.equals(desiredPropertyPointer, that.desiredPropertyPointer) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof DeleteFeatureDesiredProperty);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", featureId=" +
                featureId
                + ", desiredPropertyPointer=" + desiredPropertyPointer + "]";
    }

}
