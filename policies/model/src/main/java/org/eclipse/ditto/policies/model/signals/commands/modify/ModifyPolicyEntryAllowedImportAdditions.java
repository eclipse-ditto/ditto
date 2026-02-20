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
import org.eclipse.ditto.policies.model.AllowedImportAddition;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;

/**
 * This command modifies {@link AllowedImportAddition}s of a
 * {@link org.eclipse.ditto.policies.model.PolicyEntry}.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = ModifyPolicyEntryAllowedImportAdditions.NAME)
public final class ModifyPolicyEntryAllowedImportAdditions
        extends AbstractCommand<ModifyPolicyEntryAllowedImportAdditions>
        implements PolicyModifyCommand<ModifyPolicyEntryAllowedImportAdditions> {

    /**
     * Name of this command.
     */
    public static final String NAME = "modifyPolicyEntryAllowedImportAdditions";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_ALLOWED_IMPORT_ADDITIONS =
            JsonFactory.newJsonArrayFieldDefinition("allowedImportAdditions", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final Label label;
    private final Set<AllowedImportAddition> allowedImportAdditions;

    private ModifyPolicyEntryAllowedImportAdditions(final PolicyId policyId,
            final Label label,
            final Set<AllowedImportAddition> allowedImportAdditions,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.policyId = policyId;
        this.label = label;
        this.allowedImportAdditions = Collections.unmodifiableSet(new LinkedHashSet<>(allowedImportAdditions));
    }

    /**
     * Creates a command for modifying {@code AllowedImportAdditions} of a {@code Policy}'s {@code PolicyEntry}.
     *
     * @param policyId the identifier of the Policy.
     * @param label the Label of the PolicyEntry.
     * @param allowedImportAdditions the AllowedImportAdditions to modify.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyEntryAllowedImportAdditions of(final PolicyId policyId,
            final Label label,
            final Set<AllowedImportAddition> allowedImportAdditions,
            final DittoHeaders dittoHeaders) {

        Objects.requireNonNull(policyId, "The Policy identifier must not be null!");
        Objects.requireNonNull(label, "The Label must not be null!");
        Objects.requireNonNull(allowedImportAdditions, "The AllowedImportAdditions must not be null!");
        return new ModifyPolicyEntryAllowedImportAdditions(policyId, label, allowedImportAdditions, dittoHeaders);
    }

    /**
     * Creates a command for modifying {@code AllowedImportAdditions} of a {@code Policy}'s {@code PolicyEntry} from a
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
    public static ModifyPolicyEntryAllowedImportAdditions fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a command for modifying {@code AllowedImportAdditions} of a {@code Policy}'s {@code PolicyEntry} from a
     * JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyPolicyEntryAllowedImportAdditions fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandJsonDeserializer<ModifyPolicyEntryAllowedImportAdditions>(TYPE, jsonObject)
                .deserialize(() -> {
                    final String extractedPolicyId =
                            jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label label = PoliciesModelFactory.newLabel(jsonObject.getValueOrThrow(JSON_LABEL));
                    final JsonArray allowedImportAdditionsArray =
                            jsonObject.getValueOrThrow(JSON_ALLOWED_IMPORT_ADDITIONS);
                    final Set<AllowedImportAddition> allowedImportAdditions = allowedImportAdditionsArray.stream()
                            .filter(JsonValue::isString)
                            .map(JsonValue::asString)
                            .map(AllowedImportAddition::forName)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toCollection(LinkedHashSet::new));

                    return of(policyId, label, allowedImportAdditions, dittoHeaders);
                });
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose {@code AllowedImportAdditions} to modify.
     *
     * @return the Label of the PolicyEntry whose AllowedImportAdditions to modify.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the {@code AllowedImportAdditions} to modify.
     *
     * @return the AllowedImportAdditions to modify.
     */
    public Set<AllowedImportAddition> getAllowedImportAdditions() {
        return allowedImportAdditions;
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
        return Optional.of(allowedImportAdditions.stream()
                .map(a -> JsonValue.of(a.getName()))
                .collect(JsonCollectors.valuesToArray()));
    }

    @Override
    public ModifyPolicyEntryAllowedImportAdditions setEntity(final JsonValue entity) {
        final JsonArray jsonArray = entity.asArray();
        final Set<AllowedImportAddition> additions = jsonArray.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(AllowedImportAddition::forName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return of(policyId, label, additions, getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/allowedImportAdditions";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_ALLOWED_IMPORT_ADDITIONS, allowedImportAdditions.stream()
                .map(a -> JsonValue.of(a.getName()))
                .collect(JsonCollectors.valuesToArray()), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyPolicyEntryAllowedImportAdditions setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, allowedImportAdditions, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyEntryAllowedImportAdditions;
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
        final ModifyPolicyEntryAllowedImportAdditions that = (ModifyPolicyEntryAllowedImportAdditions) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) && Objects.equals(label, that.label)
                && Objects.equals(allowedImportAdditions, that.allowedImportAdditions) && super.equals(obj);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, allowedImportAdditions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label
                + ", allowedImportAdditions=" + allowedImportAdditions + "]";
    }

}
