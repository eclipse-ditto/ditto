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
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.base.model.signals.WithFeatureId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;

/**
 * This command modifies a single desired property of a {@link org.eclipse.ditto.things.model.Feature}'s desired properties.
 *
 * @since 1.5.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingCommand.TYPE_PREFIX, name = ModifyFeatureDesiredProperty.NAME)
public final class ModifyFeatureDesiredProperty extends AbstractCommand<ModifyFeatureDesiredProperty> implements
        ThingModifyCommand<ModifyFeatureDesiredProperty>, WithFeatureId {

    /**
     * Name of the "Modify Feature Desired Property" command.
     */
    public static final String NAME = "modifyFeatureDesiredProperty";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_DESIRED_PROPERTY =
            JsonFactory.newStringFieldDefinition("desiredProperty", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_DESIRED_PROPERTY_VALUE =
            JsonFactory.newJsonValueFieldDefinition("desiredValue", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final String featureId;
    private final JsonPointer desiredPropertyPointer;
    private final JsonValue desiredPropertyValue;

    private ModifyFeatureDesiredProperty(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer desiredPropertyPointer,
            final JsonValue desiredPropertyValue,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = checkNotNull(featureId == null || featureId.toString().isEmpty() ? null : featureId.toString(),
                "featureId");
        this.desiredPropertyPointer =
                checkDesiredPropertyPointer(checkNotNull(desiredPropertyPointer, "desiredPropertyPointer"));
        this.desiredPropertyValue =
                checkDesiredPropertyValue(checkNotNull(desiredPropertyValue, "desiredPropertyValue"));

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                desiredPropertyValue::getUpperBoundForStringSize,
                () -> desiredPropertyValue.toString().length(),
                () -> dittoHeaders);
    }

    private static JsonPointer checkDesiredPropertyPointer(final JsonPointer propertyPointer) {
        return ThingsModelFactory.validateFeaturePropertyPointer(propertyPointer);
    }

    private static JsonValue checkDesiredPropertyValue(final JsonValue value) {
        if (value.isObject()) {
            ThingsModelFactory.validateJsonKeys(value.asObject());
        }
        return value;
    }

    /**
     * Returns a Command for modifying a Feature's desired property on a Thing.
     *
     * @param thingId the {@code Thing}'s ID whose {@code Feature}'s desired property to modify.
     * @param featureId the {@code Feature}'s ID whose desired property to modify.
     * @param desiredPropertyJsonPointer the JSON pointer of the desired property key to modify.
     * @param desiredPropertyValue the value of the desired property to modify.
     * @param dittoHeaders the headers of the command.
     * @return a Command for modifying the provided desired property.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of {@code desiredPropertyJsonPointer} are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static ModifyFeatureDesiredProperty of(final ThingId thingId,
            final CharSequence featureId,
            final JsonPointer desiredPropertyJsonPointer,
            final JsonValue desiredPropertyValue,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeatureDesiredProperty(thingId, featureId, desiredPropertyJsonPointer, desiredPropertyValue,
                dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyFeatureDesiredProperty} from a JSON string.
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
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of the desired property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static ModifyFeatureDesiredProperty fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyFeatureDesiredProperty} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.base.model.entity.id.RegexPatterns#ID_REGEX}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if keys of the desired property pointer are not valid
     * according to pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static ModifyFeatureDesiredProperty fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {

        return new CommandJsonDeserializer<ModifyFeatureDesiredProperty>(TYPE, jsonObject).deserialize(() -> {
            final String extractedThingId = jsonObject.getValueOrThrow(ThingCommand.JsonFields.JSON_THING_ID);
            final ThingId thingId = ThingId.of(extractedThingId);
            final String extractedFeatureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
            final String pointerString = jsonObject.getValueOrThrow(JSON_DESIRED_PROPERTY);
            final JsonPointer extractedPointer = JsonFactory.newPointer(pointerString);
            final JsonValue extractedValue = jsonObject.getValueOrThrow(JSON_DESIRED_PROPERTY_VALUE);

            return of(thingId, extractedFeatureId, extractedPointer, extractedValue, dittoHeaders);
        });
    }

    /**
     * Returns the JSON pointer of the desired property to modify.
     *
     * @return the JSON pointer.
     */
    public JsonPointer getDesiredPropertyPointer() {
        return desiredPropertyPointer;
    }

    /**
     * Returns the value of the desired property to modify.
     *
     * @return the value.
     */
    public JsonValue getDesiredPropertyValue() {
        return desiredPropertyValue;
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(desiredPropertyValue);
    }

    /**
     * ModifyFeatureDesiredProperty is only available in JsonSchemaVersion V_2.
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
        jsonObjectBuilder.set(JSON_DESIRED_PROPERTY_VALUE, desiredPropertyValue, predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyFeatureDesiredProperty setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, featureId, desiredPropertyPointer, desiredPropertyValue, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, desiredPropertyPointer, desiredPropertyValue);
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
        final ModifyFeatureDesiredProperty that = (ModifyFeatureDesiredProperty) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) && Objects.equals(featureId, that.featureId)
                && Objects.equals(desiredPropertyPointer, that.desiredPropertyPointer) &&
                Objects.equals(desiredPropertyValue, that.desiredPropertyValue)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyFeatureDesiredProperty;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId
                + ", featureId=" + featureId
                + ", desiredPropertyPointer=" + desiredPropertyPointer
                + ", desiredPropertyValue=" + desiredPropertyValue + "]";
    }

}
