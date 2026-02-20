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
import org.eclipse.ditto.policies.model.EntryAddition;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;

/**
 * This command modifies an {@link org.eclipse.ditto.policies.model.EntryAddition} of a
 * {@link org.eclipse.ditto.policies.model.PolicyImport}.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = ModifyPolicyImportEntryAddition.NAME)
public final class ModifyPolicyImportEntryAddition extends AbstractCommand<ModifyPolicyImportEntryAddition>
        implements PolicyModifyCommand<ModifyPolicyImportEntryAddition> {

    /**
     * Name of this command.
     */
    public static final String NAME = "modifyPolicyImportEntryAddition";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_ENTRY_ADDITION =
            JsonFactory.newJsonObjectFieldDefinition("entryAddition", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final PolicyId importedPolicyId;
    private final EntryAddition entryAddition;

    private ModifyPolicyImportEntryAddition(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final EntryAddition entryAddition,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.policyId = Objects.requireNonNull(policyId, "The Policy identifier must not be null!");
        this.importedPolicyId = Objects.requireNonNull(importedPolicyId,
                "The imported Policy identifier must not be null!");
        this.entryAddition = Objects.requireNonNull(entryAddition, "The EntryAddition must not be null!");
    }

    /**
     * Creates a command for modifying an {@code EntryAddition} of a {@code PolicyImport}.
     *
     * @param policyId the identifier of the Policy.
     * @param importedPolicyId the ID of the imported Policy.
     * @param entryAddition the EntryAddition to modify.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyImportEntryAddition of(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final EntryAddition entryAddition,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyImportEntryAddition(policyId, importedPolicyId, entryAddition, dittoHeaders);
    }

    /**
     * Creates a command for modifying an {@code EntryAddition} of a {@code PolicyImport} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyPolicyImportEntryAddition fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for modifying an {@code EntryAddition} of a {@code PolicyImport} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyPolicyImportEntryAddition fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandJsonDeserializer<ModifyPolicyImportEntryAddition>(TYPE, jsonObject).deserialize(() -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final PolicyId importedPolicyId = PolicyId.of(jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID));
            final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
            final JsonObject entryAdditionJsonObject = jsonObject.getValueOrThrow(JSON_ENTRY_ADDITION);
            final EntryAddition entryAddition = PoliciesModelFactory.newEntryAddition(label, entryAdditionJsonObject);

            return of(policyId, importedPolicyId, entryAddition, dittoHeaders);
        });
    }

    /**
     * Returns the ID of the imported {@code Policy}.
     *
     * @return the ID of the imported Policy.
     */
    public PolicyId getImportedPolicyId() {
        return importedPolicyId;
    }

    /**
     * Returns the {@code Label} of the {@code EntryAddition} to modify.
     *
     * @return the Label of the EntryAddition to modify.
     */
    public Label getLabel() {
        return entryAddition.getLabel();
    }

    /**
     * Returns the {@code EntryAddition} to modify.
     *
     * @return the EntryAddition to modify.
     */
    public EntryAddition getEntryAddition() {
        return entryAddition;
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(entryAddition.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public ModifyPolicyImportEntryAddition setEntity(final JsonValue entity) {
        return of(policyId, importedPolicyId,
                PoliciesModelFactory.newEntryAddition(entryAddition.getLabel(), entity.asObject()),
                getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/imports/" + importedPolicyId + "/entriesAdditions/" + entryAddition.getLabel();
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, importedPolicyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_LABEL, entryAddition.getLabel().toString(), predicate);
        jsonObjectBuilder.set(JSON_ENTRY_ADDITION, entryAddition.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyPolicyImportEntryAddition setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, importedPolicyId, entryAddition, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyImportEntryAddition;
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
        final ModifyPolicyImportEntryAddition that = (ModifyPolicyImportEntryAddition) obj;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(importedPolicyId, that.importedPolicyId) &&
                Objects.equals(entryAddition, that.entryAddition) &&
                super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, importedPolicyId, entryAddition);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId +
                ", importedPolicyId=" + importedPolicyId + ", entryAddition=" + entryAddition + "]";
    }

}
