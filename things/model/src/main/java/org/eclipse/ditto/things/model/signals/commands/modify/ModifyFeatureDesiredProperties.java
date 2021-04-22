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
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.base.model.signals.WithFeatureId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;

/**
 * This command modifies a {@link org.eclipse.ditto.things.model.Feature}'s properties.
 *
 * @since 1.5.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingCommand.TYPE_PREFIX, name = ModifyFeatureDesiredProperties.NAME)
public final class ModifyFeatureDesiredProperties extends AbstractCommand<ModifyFeatureDesiredProperties> implements
        ThingModifyCommand<ModifyFeatureDesiredProperties>, WithFeatureId {

    /**
     * Name of the "Modify Feature Desired Properties" command.
     */
    public static final String NAME = "modifyFeatureDesiredProperties";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_DESIRED_PROPERTIES =
            JsonFactory.newJsonObjectFieldDefinition("desiredProperties", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final String featureId;
    private final FeatureProperties desiredProperties;

    private ModifyFeatureDesiredProperties(final ThingId thingId,
            final CharSequence featureId,
            final FeatureProperties desiredProperties,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = checkNotNull(featureId == null || featureId.toString().isEmpty() ? null : featureId.toString(),
                "featureId");
        this.desiredProperties = checkNotNull(desiredProperties, "desiredProperties");

        final JsonObject propertiesJsonObject = desiredProperties.toJson();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                propertiesJsonObject::getUpperBoundForStringSize,
                () -> propertiesJsonObject.toString().length(),
                () -> dittoHeaders);
    }

    /**
     * Returns a Command for modifying a Feature's desired properties on a Thing.
     *
     * @param thingId the {@code Thing}'s ID whose {@code Feature}'s desired properties to modify.
     * @param featureId the {@code Feature}'s ID whose desired properties to modify.
     * @param desiredProperties the desired properties to modify.
     * @param dittoHeaders the headers of the command.
     * @return a Command for modifying the provided desired properties.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     */
    public static ModifyFeatureDesiredProperties of(final ThingId thingId,
            final CharSequence featureId,
            final FeatureProperties desiredProperties,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeatureDesiredProperties(thingId, featureId, desiredProperties, dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyFeatureDesiredProperties} from a JSON string.
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
    public static ModifyFeatureDesiredProperties fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyFeatureDesiredProperties} from a JSON object.
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
    public static ModifyFeatureDesiredProperties fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandJsonDeserializer<ModifyFeatureDesiredProperties>(TYPE, jsonObject).deserialize(() -> {
            final String extractedThingId = jsonObject.getValueOrThrow(ThingCommand.JsonFields.JSON_THING_ID);
            final ThingId thingId = ThingId.of(extractedThingId);
            final String extractedFeatureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
            final JsonObject propertiesJsonObject = jsonObject.getValueOrThrow(JSON_DESIRED_PROPERTIES);

            final FeatureProperties extractedProperties = ThingsModelFactory.newFeatureProperties(propertiesJsonObject);

            return of(thingId, extractedFeatureId, extractedProperties, dittoHeaders);
        });
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the desired properties to modify.
     *
     * @return the desired properties to modify.
     */
    public FeatureProperties getDesiredProperties() {
        return desiredProperties;
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(desiredProperties.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    /**
     * ModifyFeatureDesiredProperties is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId + "/desiredProperties";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommand.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_DESIRED_PROPERTIES, desiredProperties, predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyFeatureDesiredProperties setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, featureId, desiredProperties, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) return false;
        final ModifyFeatureDesiredProperties that = (ModifyFeatureDesiredProperties) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(desiredProperties, that.desiredProperties) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyFeatureDesiredProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, desiredProperties);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", thingId=" + thingId +
                ", featureId=" + featureId +
                ", desiredProperties=" + desiredProperties +
                "]";
    }

}
