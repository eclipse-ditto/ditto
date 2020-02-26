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
package org.eclipse.ditto.services.models.thingsearch.commands.sudo;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.base.WithIdButActuallyNot;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command to trigger search index update for many things.
 * Currently a Ditto-internal message, but could become public API at some point.
 */
@Immutable
@AllValuesAreNonnullByDefault
@JsonParsableCommand(typePrefix = UpdateThings.TYPE_PREFIX, name = UpdateThings.NAME)
public final class UpdateThings extends AbstractCommand<UpdateThings> implements WithIdButActuallyNot {

    /**
     * Prefix for the type of this command.
     */
    public static final String TYPE_PREFIX = ThingSearchCommand.TYPE_PREFIX;

    /**
     * Name of this command.
     */
    public static final String NAME = "updateThings";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final JsonFieldDefinition<JsonArray> JSON_THING_IDS =
            JsonFactory.newJsonArrayFieldDefinition("thingIds", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final Collection<ThingId> thingIds;

    private UpdateThings(final Collection<ThingId> thingIds, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.thingIds = thingIds;
    }

    /**
     * Create an UpdateThings command.
     *
     * @param thingIds the IDs of the things whose search index should be updated.
     * @param dittoHeaders Ditto headers of the command.
     * @return the command.
     */
    public static UpdateThings of(final Collection<ThingId> thingIds, final DittoHeaders dittoHeaders) {
        return new UpdateThings(thingIds, dittoHeaders);
    }

    /**
     * Creates a new {@code UpdateThings} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain a value for
     * "thingId".
     */
    public static UpdateThings fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final Collection<ThingId> thingIds =
                jsonObject.getValueOrThrow(JSON_THING_IDS)
                        .stream()
                        .map(JsonValue::asString)
                        .map(ThingId::of)
                        .collect(Collectors.toList());
        return of(thingIds, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        final JsonArray thingIdsJsonArray = thingIds.stream()
                .map(ThingId::toString)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());
        jsonObjectBuilder.set(JSON_THING_IDS, thingIdsJsonArray, predicate);
    }

    /**
     * Retrieve the IDs of things whose search index update this command demands.
     *
     * @return the thing IDs.
     */
    public Collection<ThingId> getThingIds() {
        return thingIds;
    }

    @Override
    public String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public UpdateThings setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new UpdateThings(thingIds, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return ThingSearchCommand.RESOURCE_TYPE;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof UpdateThings)) {
            return false;
        } else {
            final UpdateThings that = (UpdateThings) o;
            return Objects.equals(thingIds, that.thingIds) && super.equals(that);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingIds);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + ",thingIds=" + thingIds + "]";
    }
}
