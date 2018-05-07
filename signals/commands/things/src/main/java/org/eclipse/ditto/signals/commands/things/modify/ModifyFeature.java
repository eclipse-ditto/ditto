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
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.ThingIdValidator;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command modifies an existing Feature.
 */
@Immutable
public final class ModifyFeature extends AbstractCommand<ModifyFeature> implements ThingModifyCommand<ModifyFeature>,
        WithFeatureId {

    /**
     * Name of the "Modify Feature" command.
     */
    public static final String NAME = "modifyFeature";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_FEATURE =
            JsonFactory.newJsonObjectFieldDefinition("feature", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final Feature feature;

    private ModifyFeature(final String thingId, final Feature feature, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        ThingIdValidator.getInstance().accept(thingId, dittoHeaders);
        this.thingId = thingId;
        this.feature = checkNotNull(feature, "Feature");
    }

    /**
     * Returns a Command for modifying a Feature on a Thing.
     *
     * @param thingId the ID of the {@code Thing} on which the {@code Feature} to modify.
     * @param feature the {@code Feature} to modify.
     * @param dittoHeaders the headers of the command.
     * @return a Command for modifying the provided Feature.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static ModifyFeature of(final String thingId, final Feature feature, final DittoHeaders dittoHeaders) {
        return new ModifyFeature(thingId, feature, dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyFeature} from a JSON string.
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
    public static ModifyFeature fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyFeature} from a JSON object.
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
    public static ModifyFeature fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyFeature>(TYPE, jsonObject).deserialize(() -> {
            final String thingId = jsonObject.getValueOrThrow(ThingModifyCommand.JsonFields.JSON_THING_ID);
            final String extractedFeatureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
            final JsonObject featureJsonObject = jsonObject.getValueOrThrow(JSON_FEATURE);

            final Feature extractedFeature;
            if (featureJsonObject.isNull()) {
                extractedFeature = ThingsModelFactory.nullFeature(extractedFeatureId);
            } else {
                extractedFeature = ThingsModelFactory.newFeatureBuilder(featureJsonObject)
                        .useId(extractedFeatureId)
                        .build();
            }

            return of(thingId, extractedFeature, dittoHeaders);
        });
    }

    /**
     * Returns the new {@code Feature} to modify.
     *
     * @return the Feature to modify.
     */
    public Feature getFeature() {
        return feature;
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public String getFeatureId() {
        return feature.getId();
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(feature.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + getFeatureId();
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommand.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, getFeatureId(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE, feature.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyFeature setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, feature, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, feature);
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
        final ModifyFeature that = (ModifyFeature) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) && Objects.equals(feature, that.feature)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyFeature);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", feature=" + feature
                + "]";
    }

}
