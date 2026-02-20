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
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.ImportedLabels;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;

/**
 * This command modifies the entries (imported labels) of a Policy import.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = ModifyPolicyImportEntries.NAME)
public final class ModifyPolicyImportEntries
        extends AbstractCommand<ModifyPolicyImportEntries>
        implements PolicyModifyCommand<ModifyPolicyImportEntries> {

    /**
     * NAME of this command.
     */
    public static final String NAME = "modifyPolicyImportEntries";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_ENTRIES =
            JsonFactory.newJsonArrayFieldDefinition("entries", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final PolicyId importedPolicyId;
    private final ImportedLabels importedLabels;

    private ModifyPolicyImportEntries(final PolicyId policyId, final PolicyId importedPolicyId,
            final ImportedLabels importedLabels, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.importedPolicyId = checkNotNull(importedPolicyId, "importedPolicyId");
        this.importedLabels = checkNotNull(importedLabels, "importedLabels");

        PolicyCommandSizeValidator.getInstance()
                .ensureValidSize(() -> importedLabels.toJson().toString().length(), () -> dittoHeaders);
    }

    /**
     * Creates a command for modifying the entries (imported labels) of a Policy import.
     *
     * @param policyId the identifier of the Policy.
     * @param importedPolicyId the identifier of the imported Policy.
     * @param importedLabels the ImportedLabels to modify.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyImportEntries of(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final ImportedLabels importedLabels,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyImportEntries(policyId, importedPolicyId, importedLabels, dittoHeaders);
    }

    /**
     * Creates a command for modifying the entries (imported labels) of a Policy import from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyPolicyImportEntries fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for modifying the entries (imported labels) of a Policy import from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyPolicyImportEntries fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyPolicyImportEntries>(TYPE, jsonObject)
                .deserialize(() -> {
                    final PolicyId policyId = PolicyId.of(
                            jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID));
                    final PolicyId importedPolicyId =
                            PolicyId.of(jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID));
                    final JsonArray entriesJsonArray = jsonObject.getValueOrThrow(JSON_ENTRIES);
                    final ImportedLabels importedLabels =
                            PoliciesModelFactory.newImportedEntries(entriesJsonArray);

                    return of(policyId, importedPolicyId, importedLabels, dittoHeaders);
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
     * Returns the {@code ImportedLabels} to modify.
     *
     * @return the ImportedLabels to modify.
     */
    public ImportedLabels getImportedLabels() {
        return importedLabels;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(importedLabels.toJson());
    }

    @Override
    public ModifyPolicyImportEntries setEntity(final JsonValue entity) {
        return of(policyId, importedPolicyId,
                PoliciesModelFactory.newImportedEntries(entity.asArray()), getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/imports/" + importedPolicyId + "/entries";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, importedPolicyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_ENTRIES, importedLabels.toJson(), predicate);
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
    public ModifyPolicyImportEntries setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, importedPolicyId, importedLabels, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyImportEntries;
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
        final ModifyPolicyImportEntries that = (ModifyPolicyImportEntries) obj;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(importedPolicyId, that.importedPolicyId) &&
                Objects.equals(importedLabels, that.importedLabels) &&
                super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, importedPolicyId, importedLabels);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", importedPolicyId=" + importedPolicyId +
                ", importedLabels=" + importedLabels + "]";
    }

}
