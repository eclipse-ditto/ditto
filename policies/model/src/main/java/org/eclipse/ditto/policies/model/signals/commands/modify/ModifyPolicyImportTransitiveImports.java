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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;
import org.eclipse.ditto.policies.model.signals.commands.PolicyImportsValidator;

/**
 * This command modifies the resolve transitively configuration of a Policy import.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = ModifyPolicyImportTransitiveImports.NAME)
public final class ModifyPolicyImportTransitiveImports
        extends AbstractCommand<ModifyPolicyImportTransitiveImports>
        implements PolicyModifyCommand<ModifyPolicyImportTransitiveImports> {

    /**
     * NAME of this command.
     */
    public static final String NAME = "modifyPolicyImportTransitiveImports";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_TRANSITIVE_IMPORTS =
            JsonFactory.newJsonArrayFieldDefinition("transitiveImports", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final PolicyId importedPolicyId;
    private final List<PolicyId> transitiveImports;

    private ModifyPolicyImportTransitiveImports(final PolicyId policyId, final PolicyId importedPolicyId,
            final List<PolicyId> transitiveImports, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.importedPolicyId = checkNotNull(importedPolicyId, "importedPolicyId");
        this.transitiveImports = Collections.unmodifiableList(
                checkNotNull(transitiveImports, "transitiveImports"));

        PolicyImportsValidator.validateTransitiveImports(policyId, importedPolicyId, transitiveImports);

        final JsonArray jsonArray = this.transitiveImports.stream()
                .map(PolicyId::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
        PolicyCommandSizeValidator.getInstance()
                .ensureValidSize(() -> jsonArray.toString().length(), () -> dittoHeaders);
    }

    /**
     * Creates a command for modifying the resolve transitively configuration of a Policy import.
     *
     * @param policyId the identifier of the Policy.
     * @param importedPolicyId the identifier of the imported Policy.
     * @param transitiveImports the list of Policy IDs to resolve transitively.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyImportTransitiveImports of(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final List<PolicyId> transitiveImports,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyImportTransitiveImports(policyId, importedPolicyId, transitiveImports,
                dittoHeaders);
    }

    /**
     * Creates a command for modifying the resolve transitively configuration of a Policy import from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyPolicyImportTransitiveImports fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for modifying the resolve transitively configuration of a Policy import from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyPolicyImportTransitiveImports fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyPolicyImportTransitiveImports>(TYPE, jsonObject)
                .deserialize(() -> {
                    final PolicyId policyId = PolicyId.of(
                            jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID));
                    final PolicyId importedPolicyId =
                            PolicyId.of(jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID));
                    final JsonArray transitiveImportsArray =
                            jsonObject.getValueOrThrow(JSON_TRANSITIVE_IMPORTS);
                    final List<PolicyId> transitiveImports = transitiveImportsArray.stream()
                            .map(JsonValue::asString)
                            .map(PolicyId::of)
                            .collect(Collectors.toList());

                    return of(policyId, importedPolicyId, transitiveImports, dittoHeaders);
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
     * Returns the list of Policy IDs to resolve transitively.
     *
     * @return the list of Policy IDs to resolve transitively.
     */
    public List<PolicyId> getTransitiveImports() {
        return transitiveImports;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        final JsonArray jsonArray = transitiveImports.stream()
                .map(PolicyId::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
        return Optional.of(jsonArray);
    }

    @Override
    public ModifyPolicyImportTransitiveImports setEntity(final JsonValue entity) {
        final List<PolicyId> policyIds = entity.asArray().stream()
                .map(JsonValue::asString)
                .map(PolicyId::of)
                .collect(Collectors.toList());
        return of(policyId, importedPolicyId, policyIds, getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/imports/" + importedPolicyId + "/transitiveImports";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, importedPolicyId.toString(), predicate);
        final JsonArray jsonArray = transitiveImports.stream()
                .map(PolicyId::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
        jsonObjectBuilder.set(JSON_TRANSITIVE_IMPORTS, jsonArray, predicate);
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
    public ModifyPolicyImportTransitiveImports setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, importedPolicyId, transitiveImports, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyImportTransitiveImports;
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
        final ModifyPolicyImportTransitiveImports that = (ModifyPolicyImportTransitiveImports) obj;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(importedPolicyId, that.importedPolicyId) &&
                Objects.equals(transitiveImports, that.transitiveImports) &&
                super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, importedPolicyId, transitiveImports);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", importedPolicyId=" + importedPolicyId +
                ", transitiveImports=" + transitiveImports + "]";
    }

}
