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
package org.eclipse.ditto.thingsearch.api.commands.sudo;

import static org.eclipse.ditto.base.model.json.JsonSchemaVersion.V_2;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.WithThingId;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command to trigger search index update for a thing.
 * Currently a Ditto-internal message, but could become public API at some point.
 */
@Immutable
@AllValuesAreNonnullByDefault
@JsonParsableCommand(typePrefix = ThingSearchSudoCommand.TYPE_PREFIX, name = SudoUpdateThing.NAME)
public final class SudoUpdateThing extends AbstractCommand<SudoUpdateThing>
        implements ThingSearchSudoCommand<SudoUpdateThing>, SignalWithEntityId<SudoUpdateThing>, WithThingId {

    /**
     * Name of this command.
     */
    public static final String NAME = "sudoUpdateThing";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final JsonFieldDefinition<String> JSON_THING_ID = Thing.JsonFields.ID;
    private static final JsonFieldDefinition<Boolean> JSON_INVALIDATE_THING =
            JsonFactory.newBooleanFieldDefinition("invalidateThing", FieldType.REGULAR, V_2);
    private static final JsonFieldDefinition<Boolean> JSON_INVALIDATE_POLICY =
            JsonFactory.newBooleanFieldDefinition("invalidatePolicy", FieldType.REGULAR, V_2);
    private static final JsonFieldDefinition<String> JSON_UPDATE_REASON =
            JsonFactory.newStringFieldDefinition("updateReason", FieldType.REGULAR, V_2);


    private final ThingId thingId;
    private final boolean invalidateThing;
    private final boolean invalidatePolicy;
    private final UpdateReason updateReason;

    private SudoUpdateThing(final ThingId thingId,
            final boolean invalidateThing,
            final boolean invalidatePolicy,
            final UpdateReason updateReason,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.thingId = thingId;
        this.invalidateThing = invalidateThing;
        this.invalidatePolicy = invalidatePolicy;
        this.updateReason = updateReason;
    }

    /**
     * Create an SudoUpdateThing command.
     *
     * @param thingId the ID of the thing whose search index should be updated.
     * @param dittoHeaders Ditto headers of the command.
     * @return the command.
     */
    public static SudoUpdateThing of(final ThingId thingId,
            final UpdateReason updateReason,
            final DittoHeaders dittoHeaders) {
        return new SudoUpdateThing(thingId, true, true, updateReason, dittoHeaders);
    }

    /**
     * Create an SudoUpdateThing command.
     *
     * @param thingId the ID of the thing whose search index should be updated.
     * @param invalidateThing whether the cached thing should be invalidated.
     * @param invalidatePolicy whether the cached policy should be invalidated.
     * @param dittoHeaders Ditto headers of the command.
     * @return the command.
     */
    public static SudoUpdateThing of(final ThingId thingId,
            final boolean invalidateThing,
            final boolean invalidatePolicy,
            final UpdateReason updateReason,
            final DittoHeaders dittoHeaders) {
        return new SudoUpdateThing(thingId, invalidateThing, invalidatePolicy, updateReason, dittoHeaders);
    }

    /**
     * Creates a new {@code SudoUpdateThing} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain a value for
     * "thingId".
     */
    public static SudoUpdateThing fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final UpdateReason updateReason = jsonObject.getValue(JSON_UPDATE_REASON)
                .map(SudoUpdateThing::updateReasonFromString)
                .orElse(UpdateReason.UNKNOWN);
        final boolean invalidateThing = jsonObject.getValue(JSON_INVALIDATE_THING).orElse(false);
        final boolean invalidatePolicy = jsonObject.getValue(JSON_INVALIDATE_POLICY).orElse(false);
        return of(ThingId.of(jsonObject.getValueOrThrow(JSON_THING_ID)), invalidateThing, invalidatePolicy,
                updateReason, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(JSON_THING_ID, thingId.toString(), predicate)
                .set(JSON_INVALIDATE_THING, invalidateThing, predicate)
                .set(JSON_INVALIDATE_POLICY, invalidatePolicy, predicate)
                .set(JSON_UPDATE_REASON, updateReason.toString(), predicate);
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
    public SudoUpdateThing setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SudoUpdateThing(thingId, invalidateThing, invalidatePolicy, updateReason, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Return whether to invalidate the cached thing.
     *
     * @return whether to invalidate the cached thing.
     */
    public boolean shouldInvalidateThing() {
        return invalidateThing;
    }

    /**
     * Return whether to invalidate the cached policy.
     *
     * @return whether to invalidate the cached policy.
     */
    public boolean shouldInvalidatePolicy() {
        return invalidatePolicy;
    }

    /**
     * Return the update reason.
     *
     * @return the update reason.
     */
    public UpdateReason getUpdateReason() {
        return updateReason;
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
        if (!(o instanceof SudoUpdateThing)) {
            return false;
        } else {
            final SudoUpdateThing that = (SudoUpdateThing) o;
            return Objects.equals(thingId, that.thingId) &&
                    invalidateThing == that.invalidateThing &&
                    invalidatePolicy == that.invalidatePolicy &&
                    updateReason == that.updateReason &&
                    super.equals(that);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, invalidateThing, invalidatePolicy, updateReason);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", thingId=" + thingId +
                ", invalidateThing=" + invalidateThing +
                ", invalidatePolicy=" + invalidatePolicy +
                ", updateReason=" + updateReason +
                "]";
    }

    private static UpdateReason updateReasonFromString(final String name) {
        try {
            return UpdateReason.valueOf(name);
        } catch (final IllegalArgumentException e) {
            return UpdateReason.UNKNOWN;
        }
    }

}
