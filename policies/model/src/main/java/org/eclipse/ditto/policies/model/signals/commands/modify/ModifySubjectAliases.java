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
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectAliases;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandSizeValidator;

/**
 * This command modifies the {@link SubjectAliases} of a Policy.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = ModifySubjectAliases.NAME)
public final class ModifySubjectAliases extends AbstractCommand<ModifySubjectAliases>
        implements PolicyModifyCommand<ModifySubjectAliases> {

    /**
     * NAME of this command.
     */
    public static final String NAME = "modifySubjectAliases";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_SUBJECT_ALIASES =
            JsonFactory.newJsonObjectFieldDefinition("subjectAliases", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final SubjectAliases subjectAliases;

    private ModifySubjectAliases(final PolicyId policyId, final SubjectAliases subjectAliases,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.subjectAliases = checkNotNull(subjectAliases, "subjectAliases");

        PolicyCommandSizeValidator.getInstance()
                .ensureValidSize(() -> subjectAliases.toJson().toString().length(), () -> dittoHeaders);
    }

    /**
     * Creates a command for modifying the {@code SubjectAliases} of a Policy.
     *
     * @param policyId the identifier of the Policy.
     * @param subjectAliases the SubjectAliases to modify.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifySubjectAliases of(final PolicyId policyId, final SubjectAliases subjectAliases,
            final DittoHeaders dittoHeaders) {

        return new ModifySubjectAliases(policyId, subjectAliases, dittoHeaders);
    }

    /**
     * Creates a command for modifying the {@code SubjectAliases} of a Policy from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifySubjectAliases fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for modifying the {@code SubjectAliases} of a Policy from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifySubjectAliases fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifySubjectAliases>(TYPE, jsonObject)
                .deserialize(() -> {
                    final PolicyId policyId = PolicyId.of(
                            jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID));
                    final JsonObject aliasesJsonObject = jsonObject.getValueOrThrow(JSON_SUBJECT_ALIASES);
                    final SubjectAliases subjectAliases =
                            PoliciesModelFactory.newSubjectAliases(aliasesJsonObject);

                    return of(policyId, subjectAliases, dittoHeaders);
                });
    }

    /**
     * Returns the {@code SubjectAliases} to modify.
     *
     * @return the SubjectAliases to modify.
     */
    public SubjectAliases getSubjectAliases() {
        return subjectAliases;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(subjectAliases.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public ModifySubjectAliases setEntity(final JsonValue entity) {
        return of(policyId, PoliciesModelFactory.newSubjectAliases(entity.asObject()), getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/subjectAliases");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ALIASES,
                subjectAliases.toJson(schemaVersion, thePredicate), predicate);
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
    public ModifySubjectAliases setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, subjectAliases, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifySubjectAliases;
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
        final ModifySubjectAliases that = (ModifySubjectAliases) obj;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(subjectAliases, that.subjectAliases) &&
                super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, subjectAliases);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", subjectAliases=" + subjectAliases + "]";
    }

}
