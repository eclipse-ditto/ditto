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
package org.eclipse.ditto.things.api.commands.sudo;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command which retrieves several {@link org.eclipse.ditto.things.model.Thing}s based on the passed in List of
 * Thing IDs without authorization. This command is sent only internally by the Ditto services, e.g. eventing or search,
 * in order to synchronize their Things cache.
 */
@Immutable
@AllValuesAreNonnullByDefault
@JsonParsableCommand(typePrefix = ThingSudoCommand.TYPE_PREFIX, name = SudoRetrieveThings.NAME)
public final class SudoRetrieveThings extends AbstractCommand<SudoRetrieveThings>
        implements ThingSudoCommand<SudoRetrieveThings> {

    /**
     * Name of the "Sudo Retrieve Things" command.
     */
    public static final String NAME = "sudoRetrieveThings";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonArray> JSON_THING_IDS =
            JsonFactory.newJsonArrayFieldDefinition("payload/thingIds", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final List<ThingId> thingIds;
    @Nullable private final JsonFieldSelector selectedFields;

    private SudoRetrieveThings(final List<ThingId> thingIds, @Nullable final JsonFieldSelector selectedFields,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);

        requireNonNull(thingIds, "The Thing IDs must not be null!");
        this.thingIds = Collections.unmodifiableList(new ArrayList<>(thingIds));
        this.selectedFields = selectedFields;
    }

    /**
     * Creates a new {@code SudoRetrieveThings}.
     *
     * @param thingIds one or more Thing IDs to be retrieved.
     * @param dittoHeaders the command headers of the request.
     * @return a command for retrieving Things without authorization.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrieveThings of(final List<ThingId> thingIds, final DittoHeaders dittoHeaders) {
        return of(thingIds, null, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveThings}.
     *
     * @param thingIds one or more Thing IDs to be retrieved.
     * @param selectedFields the Fields which should be included in the Thing's JSON representation.
     * @param dittoHeaders the command headers of the request.
     * @return a command for retrieving Things without authorization.
     * @throws NullPointerException if any argument but {@code selectedFields} is {@code null}.
     */
    public static SudoRetrieveThings of(final List<ThingId> thingIds, @Nullable final JsonFieldSelector selectedFields,
            final DittoHeaders dittoHeaders) {

        return new SudoRetrieveThings(thingIds, selectedFields, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveThings} from a JSON string.
     *
     * @param jsonString the JSON string of which a new SudoRetrieveThings is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the SudoRetrieveThings which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} does not contain a JSON
     * object or if it is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonString} was not in the
     * expected format.
     */
    public static SudoRetrieveThings fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveThings} from a JSON object.
     *
     * @param jsonObject the JSON string of which a new SudoRetrieveThings is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the SudoRetrieveThings which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected format.
     */
    public static SudoRetrieveThings fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {

        final List<ThingId> extractedThingIds = jsonObject.getValueOrThrow(JSON_THING_IDS)
                .stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(ThingId::of)
                .toList();

        final JsonFieldSelector extractedFieldSelector = jsonObject.getValue(ThingSudoCommand.JsonFields.SELECTED_FIELDS)
                .map(str -> JsonFactory.newFieldSelector(str, JsonFactory.newParseOptionsBuilder()
                        .withoutUrlDecoding()
                        .build()))
                .orElse(null);

        return SudoRetrieveThings.of(extractedThingIds, extractedFieldSelector, dittoHeaders);
    }

    /**
     * Returns the IDs of the Things to be retrieved by this command.
     *
     * @return a sorted unmodifiable list containing the IDs of Things to be retrieved by this command (in order how
     * they were requested).
     */
    public List<ThingId> getThingIds() {
        return thingIds;
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

        final JsonArray thingIdsJsonArray = thingIds.stream()
                .map(String::valueOf)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());

        jsonObjectBuilder.set(JSON_THING_IDS, thingIdsJsonArray, predicate);

        if (null != selectedFields) {
            jsonObjectBuilder.set(ThingSudoCommand.JsonFields.SELECTED_FIELDS, selectedFields.toString(), predicate);
        }
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public SudoRetrieveThings setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingIds, selectedFields, dittoHeaders);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(thingIds, selectedFields, super.hashCode());
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "pmd:SimplifyConditional"})
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final SudoRetrieveThings that = (SudoRetrieveThings) obj;
        return that.canEqual(this) && Objects.equals(thingIds, that.thingIds)
                && Objects.equals(selectedFields, that.selectedFields) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoRetrieveThings;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingIds=" + thingIds + ", selectedFields="
                + selectedFields + "]";
    }

}
