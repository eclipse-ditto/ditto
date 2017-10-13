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
package org.eclipse.ditto.signals.commands.things.query;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingIdValidator;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command which retrieves the {@link org.eclipse.ditto.model.things.Features} of a Thing.
 */
@Immutable
public final class RetrieveFeatures extends AbstractCommand<RetrieveFeatures>
        implements ThingQueryCommand<RetrieveFeatures> {

    /**
     * Name of the "Retrieve Features" command.
     */
    public static final String NAME = "retrieveFeatures";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_SELECTED_FIELDS =
            JsonFactory.newStringFieldDefinition("selectedFields", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    @Nullable private final JsonFieldSelector selectedFields;

    private RetrieveFeatures(final String thingId, @Nullable final JsonFieldSelector theSelectedFields,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        ThingIdValidator.getInstance().accept(thingId, dittoHeaders);
        this.thingId = thingId;
        selectedFields = theSelectedFields;
    }

    /**
     * Returns a Command for retrieving all Features of a Thing.
     *
     * @param thingId the ID of a Thing whose Features to be retrieved by this command.
     * @param dittoHeaders the headers of the command.
     * @return a Command for retrieving the Features.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static RetrieveFeatures of(final String thingId, final DittoHeaders dittoHeaders) {
        return of(thingId, null, dittoHeaders);
    }

    /**
     * Returns a Command for retrieving all Features of a Thing.
     *
     * @param thingId the ID of a Thing whose Features to be retrieved by this command.
     * @param selectedFields defines the fields of the JSON representation of the Features to retrieve.
     * @param dittoHeaders the headers of the command.
     * @return a Command for retrieving the Features.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static RetrieveFeatures of(final String thingId, @Nullable final JsonFieldSelector selectedFields,
            final DittoHeaders dittoHeaders) {

        return new RetrieveFeatures(thingId, selectedFields, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveFeatures} from a JSON string.
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
    public static RetrieveFeatures fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveFeatures} from a JSON object.
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
    public static RetrieveFeatures fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrieveFeatures>(TYPE, jsonObject).deserialize(() -> {
            final String thingId = jsonObject.getValueOrThrow(ThingQueryCommand.JsonFields.JSON_THING_ID);
            final JsonFieldSelector extractedFieldSelector = jsonObject.getValue(JSON_SELECTED_FIELDS)
                    .map(str -> JsonFactory.newFieldSelector(str, JsonFactory.newParseOptionsBuilder()
                            .withoutUrlDecoding()
                            .build()))
                    .orElse(null);

            return of(thingId, extractedFieldSelector, dittoHeaders);
        });
    }

    @Override
    public Optional<JsonFieldSelector> getSelectedFields() {
        return Optional.ofNullable(selectedFields);
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/features");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingQueryCommand.JsonFields.JSON_THING_ID, thingId, predicate);
        if (null != selectedFields) {
            jsonObjectBuilder.set(JSON_SELECTED_FIELDS, selectedFields.toString(), predicate);
        }
    }

    @Override
    public RetrieveFeatures setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, selectedFields, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, selectedFields);
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
        final RetrieveFeatures that = (RetrieveFeatures) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(selectedFields, that.selectedFields) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveFeatures;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", selectedFields="
                + selectedFields + "]";
    }

}
