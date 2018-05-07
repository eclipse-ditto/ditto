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

import static java.util.Objects.requireNonNull;

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
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingIdValidator;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command modifies the Policy ID of a Thing.
 */
@Immutable
public final class ModifyPolicyId extends AbstractCommand<ModifyPolicyId>
        implements ThingModifyCommand<ModifyPolicyId> {

    /**
     * Name of the "Modify Policy ID" command.
     */
    public static final String NAME = "modifyPolicyId";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_POLICY_ID =
            JsonFactory.newStringFieldDefinition("policyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String thingId;
    private final String policyId;

    private ModifyPolicyId(final String thingId, final String policyId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        ThingIdValidator.getInstance().accept(thingId, dittoHeaders);
        this.thingId = thingId;
        this.policyId = requireNonNull(policyId, "The policy ID must not be null!");
    }

    /**
     * ModifyPolicyId is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns a command for modifying an attribute which is passed as argument.
     *
     * @param thingId the ID of the thing on which to modify the Policy ID.
     * @param policyId the Policy ID to set.
     * @param dittoHeaders the headers of the command.
     * @return a command for modifying the provided new attribute.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static ModifyPolicyId of(final String thingId, final String policyId, final DittoHeaders dittoHeaders) {
        return new ModifyPolicyId(thingId, policyId, dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyPolicyId} from a JSON string.
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
    public static ModifyPolicyId fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyPolicyId} from a JSON object.
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
    public static ModifyPolicyId fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyPolicyId>(TYPE, jsonObject).deserialize(() -> {
            final String thingId = jsonObject.getValueOrThrow(ThingModifyCommand.JsonFields.JSON_THING_ID);
            final String policyId = jsonObject.getValueOrThrow(JSON_POLICY_ID);

            return of(thingId, policyId, dittoHeaders);
        });
    }

    /**
     * Returns the new Policy ID.
     *
     * @return the new Policy ID.
     */
    public String getPolicyId() {
        return policyId;
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(JsonValue.of(policyId));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = Thing.JsonFields.POLICY_ID.getPointer().toString();
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommand.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_POLICY_ID, policyId, predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyPolicyId setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, policyId, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, policyId);
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
        final ModifyPolicyId that = (ModifyPolicyId) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(policyId, that.policyId)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyPolicyId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", policyId="
                + policyId + "]";
    }

}
