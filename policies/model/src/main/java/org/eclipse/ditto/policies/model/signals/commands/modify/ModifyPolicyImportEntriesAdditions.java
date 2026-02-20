/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.EntriesAdditions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;

/**
 * This command modifies the {@link EntriesAdditions} of a Policy import.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = ModifyPolicyImportEntriesAdditions.NAME)
public final class ModifyPolicyImportEntriesAdditions
        extends AbstractCommand<ModifyPolicyImportEntriesAdditions>
        implements PolicyModifyCommand<ModifyPolicyImportEntriesAdditions> {

    /**
     * NAME of this command.
     */
    public static final String NAME = "modifyPolicyImportEntriesAdditions";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_ENTRIES_ADDITIONS =
            JsonFactory.newJsonObjectFieldDefinition("entriesAdditions", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final PolicyId importedPolicyId;
    private final EntriesAdditions entriesAdditions;

    private ModifyPolicyImportEntriesAdditions(final PolicyId policyId, final PolicyId importedPolicyId,
            final EntriesAdditions entriesAdditions, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.importedPolicyId = checkNotNull(importedPolicyId, "importedPolicyId");
        this.entriesAdditions = checkNotNull(entriesAdditions, "entriesAdditions");

        PolicyCommandSizeValidator.getInstance()
                .ensureValidSize(() -> entriesAdditions.toJson().toString().length(), () -> dittoHeaders);
    }

    /**
     * Creates a command for modifying the {@code EntriesAdditions} of a Policy import.
     *
     * @param policyId the identifier of the Policy.
     * @param importedPolicyId the identifier of the imported Policy.
     * @param entriesAdditions the EntriesAdditions to modify.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyImportEntriesAdditions of(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final EntriesAdditions entriesAdditions,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyImportEntriesAdditions(policyId, importedPolicyId, entriesAdditions, dittoHeaders);
    }

    /**
     * Creates a command for modifying the {@code EntriesAdditions} of a Policy import from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyPolicyImportEntriesAdditions fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for modifying the {@code EntriesAdditions} of a Policy import from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyPolicyImportEntriesAdditions fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyPolicyImportEntriesAdditions>(TYPE, jsonObject)
                .deserialize(() -> {
                    final PolicyId policyId = PolicyId.of(
                            jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID));
                    final PolicyId importedPolicyId =
                            PolicyId.of(jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID));
                    final JsonObject additionsJsonObject = jsonObject.getValueOrThrow(JSON_ENTRIES_ADDITIONS);
                    final EntriesAdditions entriesAdditions =
                            PoliciesModelFactory.newEntriesAdditions(additionsJsonObject);

                    return of(policyId, importedPolicyId, entriesAdditions, dittoHeaders);
                });
    }

    /**
     * Returns the identifier of the imported Policy.
     *
     * @return the identifier of the imported Policy.
     */
    public PolicyId getImportedPolicyId() {
        return importedPolicyId;
    }

    /**
     * Returns the {@code EntriesAdditions} to modify.
     *
     * @return the EntriesAdditions to modify.
     */
    public EntriesAdditions getEntriesAdditions() {
        return entriesAdditions;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(entriesAdditions.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public ModifyPolicyImportEntriesAdditions setEntity(final JsonValue entity) {
        return of(policyId, importedPolicyId,
                PoliciesModelFactory.newEntriesAdditions(entity.asObject()), getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/imports/" + importedPolicyId + "/entriesAdditions";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, importedPolicyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_ENTRIES_ADDITIONS,
                entriesAdditions.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public ModifyPolicyImportEntriesAdditions setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, importedPolicyId, entriesAdditions, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyImportEntriesAdditions;
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
        final ModifyPolicyImportEntriesAdditions that = (ModifyPolicyImportEntriesAdditions) obj;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(importedPolicyId, that.importedPolicyId) &&
                Objects.equals(entriesAdditions, that.entriesAdditions) &&
                super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, importedPolicyId, entriesAdditions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", importedPolicyId=" + importedPolicyId +
                ", entriesAdditions=" + entriesAdditions + "]";
    }

}
