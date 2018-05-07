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
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.ThingIdValidator;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command modifies all existing Features of a Thing.
 */
public final class ModifyFeatures extends AbstractCommand<ModifyFeatures>
        implements ThingModifyCommand<ModifyFeatures> {

    /**
     * Name of the "Modify Features" command.
     */
    public static final String NAME = "modifyFeatures";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_FEATURES =
            JsonFactory.newJsonObjectFieldDefinition("features", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final Features features;

    private ModifyFeatures(final String thingId, final Features features, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        ThingIdValidator.getInstance().accept(thingId, dittoHeaders);
        this.thingId = thingId;
        this.features = checkNotNull(features, "Features");
    }

    /**
     * Returns a Command for modifying the Features of a Thing.
     *
     * @param thingId the ID of the {@code Thing} on which the {@code Features} to modify.
     * @param features the {@code Features} to modify.
     * @param dittoHeaders the headers of the command.
     * @return a Command for modifying the provided Features.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static ModifyFeatures of(final String thingId, final Features features,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeatures(thingId, features, dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyFeatures} from a JSON string.
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
    public static ModifyFeatures fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyFeatures} from a JSON object.
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
    public static ModifyFeatures fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyFeatures>(TYPE, jsonObject).deserialize(() -> {
            final String thingId = jsonObject.getValueOrThrow(ThingModifyCommand.JsonFields.JSON_THING_ID);
            final JsonObject featuresJsonObject = jsonObject.getValueOrThrow(JSON_FEATURES);
            final Features extractedFeatures = ThingsModelFactory.newFeatures(featuresJsonObject);

            return of(thingId, extractedFeatures, dittoHeaders);
        });
    }

    /**
     * Returns the new {@code Features} to modify.
     *
     * @return the Features to modify.
     */
    public Features getFeatures() {
        return features;
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(features.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/features");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommand.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_FEATURES, features.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyFeatures setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, features, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, features);
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
        final ModifyFeatures that = (ModifyFeatures) obj;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId)
                && Objects.equals(features, that.features)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyFeatures);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", features=" + features
                + "]";
    }

}
