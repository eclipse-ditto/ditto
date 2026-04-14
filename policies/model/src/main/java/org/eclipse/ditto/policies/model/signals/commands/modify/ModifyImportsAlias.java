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
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ImportsAlias;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;

/**
 * This command modifies a {@link ImportsAlias} of a Policy.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = ModifyImportsAlias.NAME)
public final class ModifyImportsAlias extends AbstractCommand<ModifyImportsAlias>
        implements PolicyModifyCommand<ModifyImportsAlias> {

    /**
     * Name of this command.
     */
    public static final String NAME = "modifyImportsAlias";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_IMPORTS_ALIAS =
            JsonFactory.newJsonObjectFieldDefinition("importsAlias", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final ImportsAlias importsAlias;

    private ModifyImportsAlias(final PolicyId policyId, final ImportsAlias importsAlias,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.policyId = Objects.requireNonNull(policyId, "The Policy identifier must not be null!");
        this.importsAlias = Objects.requireNonNull(importsAlias, "The ImportsAlias must not be null!");
    }

    /**
     * Creates a command for modifying a {@code ImportsAlias} of a Policy.
     *
     * @param policyId the identifier of the Policy.
     * @param importsAlias the ImportsAlias to modify.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyImportsAlias of(final PolicyId policyId, final ImportsAlias importsAlias,
            final DittoHeaders dittoHeaders) {

        return new ModifyImportsAlias(policyId, importsAlias, dittoHeaders);
    }

    /**
     * Creates a command for modifying a {@code ImportsAlias} of a Policy from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyImportsAlias fromJson(final String jsonString, final DittoHeaders dittoHeaders) {

        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for modifying a {@code ImportsAlias} of a Policy from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyImportsAlias fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {

        return new CommandJsonDeserializer<ModifyImportsAlias>(TYPE, jsonObject).deserialize(() -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
            final JsonObject importsAliasJsonObject = jsonObject.getValueOrThrow(JSON_IMPORTS_ALIAS);
            final ImportsAlias importsAlias = PoliciesModelFactory.newImportsAlias(label, importsAliasJsonObject);

            return of(policyId, importsAlias, dittoHeaders);
        });
    }

    /**
     * Returns the {@code Label} of the {@code ImportsAlias} to modify.
     *
     * @return the Label of the ImportsAlias to modify.
     */
    public Label getLabel() {
        return importsAlias.getLabel();
    }

    /**
     * Returns the {@code ImportsAlias} to modify.
     *
     * @return the ImportsAlias to modify.
     */
    public ImportsAlias getImportsAlias() {
        return importsAlias;
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(importsAlias.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public ModifyImportsAlias setEntity(final JsonValue entity) {
        return of(policyId,
                PoliciesModelFactory.newImportsAlias(importsAlias.getLabel(), entity.asObject()),
                getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/importsAliases/" + importsAlias.getLabel());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, importsAlias.getLabel().toString(), predicate);
        jsonObjectBuilder.set(JSON_IMPORTS_ALIAS, importsAlias.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyImportsAlias setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, importsAlias, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyImportsAlias;
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
        final ModifyImportsAlias that = (ModifyImportsAlias) obj;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(importsAlias, that.importsAlias) &&
                super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, importsAlias);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId +
                ", importsAlias=" + importsAlias + "]";
    }

}
