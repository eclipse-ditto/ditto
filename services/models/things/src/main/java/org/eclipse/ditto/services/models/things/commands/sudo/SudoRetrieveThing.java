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
package org.eclipse.ditto.services.models.things.commands.sudo;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command which retrieves a {@link org.eclipse.ditto.model.things.Thing} without authorization. This command is sent
 * only internally by the Ditto services, e.g. eventing or search, in order to synchronize their Things cache.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class SudoRetrieveThing extends AbstractCommand<SudoRetrieveThing> implements
        SudoCommand<SudoRetrieveThing> {

    /**
     * Name of the "Sudo Retrieve Thing" command.
     */
    public static final String NAME = "sudoRetrieveThing";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<Boolean> JSON_USE_ORIGINAL_SCHEMA_VERSION =
            JsonFactory.newBooleanFieldDefinition("payload/useOriginalSchemaVersion", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final String thingId;
    @Nullable private final JsonFieldSelector selectedFields;
    private final boolean useOriginalSchemaVersion;

    private SudoRetrieveThing(final String thingId,
            @Nullable final JsonFieldSelector selectedFields,
            final DittoHeaders dittoHeaders,
            final boolean useOriginalSchemaVersion) {

        super(TYPE, dittoHeaders);
        this.thingId = checkNotNull(thingId, "Thing ID");
        this.selectedFields = selectedFields;
        this.useOriginalSchemaVersion = useOriginalSchemaVersion;
    }

    /**
     * Creates a new {@code SudoRetrieveThing}.
     *
     * @param thingId the ID of the Thing to be retrieved.
     * @param dittoHeaders the command headers of the request.
     * @return a command for retrieving a Thing without authorization.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrieveThing of(final String thingId, final DittoHeaders dittoHeaders) {
        return of(thingId, null, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveThing}.
     *
     * @param thingId the ID of the Thing to be retrieved.
     * @param selectedFields the Fields which should be included in the Thing's JSON representation.
     * @param dittoHeaders the command headers of the request.
     * @return a command for retrieving a Thing without authorization.
     * @throws NullPointerException if any argument is {@code null} except the {@code selectedFields}
     */
    public static SudoRetrieveThing of(final String thingId, @Nullable final JsonFieldSelector selectedFields,
            final DittoHeaders dittoHeaders) {

        return new SudoRetrieveThing(thingId, selectedFields, dittoHeaders, false);
    }

    /**
     * Creates a new {@code SudoRetrieveThing}. The returned thing is serialized using its original schema version.
     *
     * @param thingId the ID of the Thing to be retrieved.
     * @param selectedFields the Fields which should be included in the Thing's JSON representation.
     * @param dittoHeaders the command headers of the request.
     * @return a command for retrieving a Thing without authorization.
     * @throws NullPointerException if any argument is {@code null} except the {@code selectedFields}.
     */
    public static SudoRetrieveThing withOriginalSchemaVersion(final String thingId,
            @Nullable final JsonFieldSelector selectedFields, final DittoHeaders dittoHeaders) {

        return new SudoRetrieveThing(thingId, selectedFields, dittoHeaders, true);
    }

    /**
     * Creates a new {@code SudoRetrieveThing}. The returned thing is serialized using its original schema version.
     *
     * @param thingId the ID of the Thing to be retrieved.
     * @param dittoHeaders the command headers of the request.
     * @return a command for retrieving a Thing without authorization.
     * @throws NullPointerException if any argument is {@code null} except the {@code selectedFields}.
     */
    public static SudoRetrieveThing withOriginalSchemaVersion(final String thingId,
            final DittoHeaders dittoHeaders) {

        return new SudoRetrieveThing(thingId, null, dittoHeaders, true);
    }

    /**
     * Creates a new {@code SudoRetrieveThing} from a JSON string.
     *
     * @param jsonString the JSON string of which a new SudoRetrieveThing is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the SudoRetrieveThing which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} does not contain a JSON
     * object or if it is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonString} was not in the
     * expected format.
     */
    public static SudoRetrieveThing fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveThing} from a JSON object.
     *
     * @param jsonObject the JSON string of which a new SudoRetrieveThing is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the SudoRetrieveThing which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected format.
     */
    public static SudoRetrieveThing fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String readThingId = jsonObject.getValueOrThrow(SudoCommand.JsonFields.JSON_THING_ID);
        final JsonFieldSelector readFieldSelector = jsonObject.getValue(SudoCommand.JsonFields.SELECTED_FIELDS)
                .map(str -> JsonFactory.newFieldSelector(str, JsonFactory.newParseOptionsBuilder()
                        .withoutUrlDecoding()
                        .build())
                )
                .orElse(null);
        final boolean isUseOriginalSchemaVersion = jsonObject.getValue(JSON_USE_ORIGINAL_SCHEMA_VERSION).orElse(false);

        return new SudoRetrieveThing(readThingId, readFieldSelector, dittoHeaders, isUseOriginalSchemaVersion);
    }

    @Override
    public String getId() {
        return thingId;
    }

    /**
     * Returns whether the resulting thing should be serialized using its original schema version.
     *
     * @return a boolean holding the info whether the resulting thing should be serialized by its original schema
     * version
     */
    public boolean useOriginalSchemaVersion() {
        return useOriginalSchemaVersion;
    }

    /**
     * Returns the JSON field selector which is to be included in the JSON of each retrieved Thing.
     *
     * @return the JSON field selector if specified.
     */
    public Optional<JsonFieldSelector> getSelectedFields() {
        return Optional.ofNullable(selectedFields);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        jsonObjectBuilder.set(SudoCommand.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_USE_ORIGINAL_SCHEMA_VERSION, useOriginalSchemaVersion, predicate);

        if (null != selectedFields) {
            jsonObjectBuilder.set(SudoCommand.JsonFields.SELECTED_FIELDS, selectedFields.toString(), predicate);
        }
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public SudoRetrieveThing setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SudoRetrieveThing(thingId, selectedFields, dittoHeaders, useOriginalSchemaVersion);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final SudoRetrieveThing that = (SudoRetrieveThing) o;
        return that.canEqual(this) &&
                useOriginalSchemaVersion == that.useOriginalSchemaVersion &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(selectedFields, that.selectedFields) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoRetrieveThing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, selectedFields, useOriginalSchemaVersion);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "thingId=" + thingId +
                ", selectedFields=" + selectedFields +
                ", useOriginalSchemaVersion=" + useOriginalSchemaVersion +
                "]";
    }

}
