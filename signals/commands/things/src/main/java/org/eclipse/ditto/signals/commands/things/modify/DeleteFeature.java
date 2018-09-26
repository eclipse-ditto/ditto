/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
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
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingIdValidator;
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command deletes a Feature.
 */
@Immutable
public final class DeleteFeature extends AbstractCommand<DeleteFeature> implements ThingModifyCommand<DeleteFeature>,
        WithFeatureId {

    /**
     * Name of the "Delete Feature" command.
     */
    public static final String NAME = "deleteFeature";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFactory.newStringFieldDefinition("featureId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    private final String featureId;

    private DeleteFeature(final String thingId, final String featureId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        ThingIdValidator.getInstance().accept(thingId, dittoHeaders);
        this.thingId = thingId;
        this.featureId = checkNotNull(featureId, "Feature ID");
    }

    /**
     * Returns a Command for deleting a new Feature on a Thing.
     *
     * @param thingId the ID of the {@code Thing} on which the {@code Feature} to delete.
     * @param featureId the ID of the {@code Feature} to delete.
     * @param dittoHeaders the headers of the command.
     * @return a Command for deleting the provided new Feature.
     * @throws NullPointerException if the any argument but {@code thingId} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static DeleteFeature of(final String thingId, final String featureId, final DittoHeaders dittoHeaders) {
        return new DeleteFeature(thingId, featureId, dittoHeaders);
    }

    /**
     * Creates a new {@code DeleteFeature} from a JSON string.
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
    public static DeleteFeature fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code DeleteFeature} from a JSON object.
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
    public static DeleteFeature fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<DeleteFeature>(TYPE, jsonObject).deserialize(() -> {
            final String thingId = jsonObject.getValueOrThrow(ThingModifyCommand.JsonFields.JSON_THING_ID);
            final String featureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);

            return of(thingId, featureId, dittoHeaders);
        });
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/features/" + featureId;
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder payloadJsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        payloadJsonObjectBuilder.set(ThingModifyCommand.JsonFields.JSON_THING_ID, thingId, predicate);
        payloadJsonObjectBuilder.set(JSON_FEATURE_ID, featureId, predicate);
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public DeleteFeature setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, featureId, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId);
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
        final DeleteFeature that = (DeleteFeature) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) && Objects.equals(featureId, that.featureId)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof DeleteFeature);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", featureId=" +
                featureId
                + "]";
    }

}
