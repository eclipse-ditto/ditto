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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.AllowedAddition;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;

/**
 * This command modifies {@link AllowedAddition}s of a
 * {@link org.eclipse.ditto.policies.model.PolicyEntry}.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = ModifyPolicyEntryAllowedAdditions.NAME)
public final class ModifyPolicyEntryAllowedAdditions
        extends AbstractCommand<ModifyPolicyEntryAllowedAdditions>
        implements PolicyModifyCommand<ModifyPolicyEntryAllowedAdditions> {

    /**
     * Name of this command.
     */
    public static final String NAME = "modifyPolicyEntryAllowedAdditions";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_ALLOWED_ADDITIONS =
            JsonFactory.newJsonArrayFieldDefinition("allowedAdditions", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final Label label;
    private final Set<AllowedAddition> allowedAdditions;

    private ModifyPolicyEntryAllowedAdditions(final PolicyId policyId,
            final Label label,
            final Set<AllowedAddition> allowedAdditions,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.policyId = policyId;
        this.label = label;
        this.allowedAdditions = Collections.unmodifiableSet(new LinkedHashSet<>(allowedAdditions));
    }

    /**
     * Creates a command for modifying {@code AllowedAdditions} of a {@code Policy}'s {@code PolicyEntry}.
     *
     * @param policyId the identifier of the Policy.
     * @param label the Label of the PolicyEntry.
     * @param allowedAdditions the AllowedAdditions to modify.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyEntryAllowedAdditions of(final PolicyId policyId,
            final Label label,
            final Set<AllowedAddition> allowedAdditions,
            final DittoHeaders dittoHeaders) {

        Objects.requireNonNull(policyId, "The Policy identifier must not be null!");
        Objects.requireNonNull(label, "The Label must not be null!");
        Objects.requireNonNull(allowedAdditions, "The AllowedAdditions must not be null!");
        return new ModifyPolicyEntryAllowedAdditions(policyId, label, allowedAdditions, dittoHeaders);
    }

    /**
     * Creates a command for modifying {@code AllowedAdditions} of a {@code Policy}'s {@code PolicyEntry} from a
     * JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyPolicyEntryAllowedAdditions fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for modifying {@code AllowedAdditions} of a {@code Policy}'s {@code PolicyEntry} from a
     * JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyPolicyEntryAllowedAdditions fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandJsonDeserializer<ModifyPolicyEntryAllowedAdditions>(TYPE, jsonObject)
                .deserialize(() -> {
                    final String extractedPolicyId =
                            jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label label = PoliciesModelFactory.newLabel(jsonObject.getValueOrThrow(JSON_LABEL));
                    final JsonArray allowedAdditionsArray =
                            jsonObject.getValueOrThrow(JSON_ALLOWED_ADDITIONS);
                    final Set<AllowedAddition> allowedAdditions =
                            PoliciesModelFactory.parseAllowedAdditions(allowedAdditionsArray);

                    return of(policyId, label, allowedAdditions, dittoHeaders);
                });
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose {@code AllowedAdditions} to modify.
     *
     * @return the Label of the PolicyEntry whose AllowedAdditions to modify.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the {@code AllowedAdditions} to modify.
     *
     * @return the AllowedAdditions to modify.
     */
    public Set<AllowedAddition> getAllowedAdditions() {
        return allowedAdditions;
    }

    /**
     * Returns the identifier of the {@code Policy} whose {@code PolicyEntry} to modify.
     *
     * @return the identifier of the Policy whose PolicyEntry to modify.
     */
    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(allowedAdditions.stream()
                .map(a -> JsonValue.of(a.getName()))
                .collect(JsonCollectors.valuesToArray()));
    }

    @Override
    public ModifyPolicyEntryAllowedAdditions setEntity(final JsonValue entity) {
        final Set<AllowedAddition> additions = PoliciesModelFactory.parseAllowedAdditions(entity.asArray());
        return of(policyId, label, additions, getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/allowedAdditions";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_ALLOWED_ADDITIONS, allowedAdditions.stream()
                .map(a -> JsonValue.of(a.getName()))
                .collect(JsonCollectors.valuesToArray()), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyPolicyEntryAllowedAdditions setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, allowedAdditions, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyEntryAllowedAdditions;
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
        final ModifyPolicyEntryAllowedAdditions that = (ModifyPolicyEntryAllowedAdditions) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) && Objects.equals(label, that.label)
                && Objects.equals(allowedAdditions, that.allowedAdditions) && super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, allowedAdditions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label
                + ", allowedAdditions=" + allowedAdditions + "]";
    }

}
