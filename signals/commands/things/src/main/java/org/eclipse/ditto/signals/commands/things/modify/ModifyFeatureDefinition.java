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

import org.eclipse.ditto.json.JsonArray;
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
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.ThingIdValidator;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command modifies a {@link org.eclipse.ditto.model.things.Feature}'s Definition.
 */
@Immutable
public final class ModifyFeatureDefinition extends AbstractCommand<ModifyFeatureDefinition>
        implements ThingModifyCommand<ModifyFeatureDefinition>, WithFeatureId {

    /**
     * Name of the "Modify Feature Definition" command.
     */
    public static final String NAME = "modifyFeatureDefinition";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_DEFINITION =
            JsonFactory.newJsonArrayFieldDefinition("definition", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final String featureId;
    private final FeatureDefinition definition;

    private ModifyFeatureDefinition(final String thingId,
            final String featureId,
            final FeatureDefinition definition,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        ThingIdValidator.getInstance().accept(thingId, dittoHeaders);
        this.thingId = thingId;
        this.featureId = checkNotNull(featureId, "Feature ID");
        this.definition = checkNotNull(definition, "Feature Definition");
    }

    /**
     * Returns a Command for modifying a Feature's Definition on a Thing.
     *
     * @param thingId the {@code Thing}'s ID whose {@code Feature}'s Definition to modify.
     * @param featureId the {@code Feature}'s ID whose Definition to modify.
     * @param definition the Definition to modify.
     * @param dittoHeaders the headers of the command.
     * @return a Command for modifying the provided Definition.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the {@code thingId} not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static ModifyFeatureDefinition of(final String thingId,
            final String featureId,
            final FeatureDefinition definition,
            final DittoHeaders dittoHeaders) {

        return new ModifyFeatureDefinition(thingId, featureId, definition, dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyFeatureDefinition} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the parsed {@code jsonString} did not contain any of
     * the fields <ul> <li> {@link org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand.JsonFields#JSON_THING_ID},
     * </li> <li>{@link #JSON_FEATURE_ID} or</li> <li>{@link #JSON_DEFINITION}.</li> </ul>
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static ModifyFeatureDefinition fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyFeatureDefinition} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain any of the fields
     * <ul> <li> {@link org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand.JsonFields#JSON_THING_ID},
     * </li> <li>{@link #JSON_FEATURE_ID} or</li> <li>{@link #JSON_DEFINITION}.</li> </ul>
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static ModifyFeatureDefinition fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyFeatureDefinition>(TYPE, jsonObject).deserialize(() -> {
            final String extractedThingId = jsonObject.getValueOrThrow(ThingModifyCommand.JsonFields.JSON_THING_ID);
            final String extractedFeatureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
            final JsonArray definitionJsonArray = jsonObject.getValueOrThrow(JSON_DEFINITION);
            final FeatureDefinition extractedDefinition = ThingsModelFactory.newFeatureDefinition(definitionJsonArray);

            return of(extractedThingId, extractedFeatureId, extractedDefinition, dittoHeaders);
        });
    }

    /**
     * Returns the identifier of the {@code Thing} which is subject to this command's action.
     *
     * @return the identifier of the Thing.
     */
    @Override
    public String getThingId() {
        return thingId;
    }

    /**
     * Returns the {@link FeatureDefinition} to modify.
     *
     * @return the Definition to modify.
     */
    public FeatureDefinition getDefinition() {
        return definition;
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public String getId() {
        return getThingId();
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(definition.toJson());
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId + "/definition";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommand.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
        jsonObjectBuilder.set(JSON_DEFINITION, definition.toJson(), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyFeatureDefinition setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, featureId, definition, dittoHeaders);
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
        final ModifyFeatureDefinition that = (ModifyFeatureDefinition) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(definition, that.definition) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyFeatureDefinition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, definition);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", thingId=" + thingId +
                ", featureId=" + featureId +
                ", definition=" + definition +
                "]";
    }

}
