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
package org.eclipse.ditto.signals.commands.policies.modify;

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
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command modifies a {@link PolicyEntry}.
 */
@Immutable
public final class ModifyPolicyEntry extends AbstractCommand<ModifyPolicyEntry> implements
        PolicyModifyCommand<ModifyPolicyEntry> {

    /**
     * NAME of this command.
     */
    public static final String NAME = "modifyPolicyEntry";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_POLICY_ENTRY =
            JsonFactory.newJsonObjectFieldDefinition("policyEntry", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String policyId;
    private final PolicyEntry policyEntry;

    private ModifyPolicyEntry(final String policyId, final PolicyEntry policyEntry, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = policyId;
        this.policyEntry = policyEntry;
    }

    /**
     * Creates a command for modifying a {@code PolicyEntry}.
     *
     * @param policyId the identifier of the Policy.
     * @param policyEntry the PolicyEntry to modify.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyEntry of(final String policyId, final PolicyEntry policyEntry,
            final DittoHeaders dittoHeaders) {

        Objects.requireNonNull(policyId, "The Policy identifier must not be null!");
        Objects.requireNonNull(policyEntry, "The PolicyEntry must not be null!");
        return new ModifyPolicyEntry(policyId, policyEntry, dittoHeaders);
    }

    /**
     * Creates a command for modifying a {@code PolicyEntry} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyPolicyEntry fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for modifying a {@code PolicyEntry} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyPolicyEntry fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyPolicyEntry>(TYPE, jsonObject).deserialize(() -> {
            final String policyId = jsonObject.getValueOrThrow(PolicyModifyCommand.JsonFields.JSON_POLICY_ID);
            final String policyEntryLabel = jsonObject.getValueOrThrow(JSON_LABEL);
            final JsonObject policyEntryJsonObject = jsonObject.getValueOrThrow(JSON_POLICY_ENTRY);
            final PolicyEntry policyEntry =
                    PoliciesModelFactory.newPolicyEntry(policyEntryLabel, policyEntryJsonObject);

            return of(policyId, policyEntry, dittoHeaders);
        });
    }

    /**
     * Returns the {@code PolicyEntry} to modify.
     *
     * @return the PolicyEntry to modify.
     */
    public PolicyEntry getPolicyEntry() {
        return policyEntry;
    }

    /**
     * Returns the identifier of the {@code Policy} whose {@code PolicyEntry} to modify.
     *
     * @return the identifier of the Policy whose PolicyEntry to modify.
     */
    @Override
    public String getId() {
        return policyId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(policyEntry.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + policyEntry.getLabel();
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyModifyCommand.JsonFields.JSON_POLICY_ID, policyId, predicate);
        jsonObjectBuilder.set(JSON_LABEL, policyEntry.getLabel().toString(), predicate);
        jsonObjectBuilder.set(JSON_POLICY_ENTRY, policyEntry.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyPolicyEntry setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, policyEntry, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyEntry;
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || getClass() != obj.getClass()) {
            return false;
        }
        final ModifyPolicyEntry that = (ModifyPolicyEntry) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId)
                && Objects.equals(policyEntry, that.policyEntry) && super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, policyEntry);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", policyEntry="
                + policyEntry + "]";
    }

}
