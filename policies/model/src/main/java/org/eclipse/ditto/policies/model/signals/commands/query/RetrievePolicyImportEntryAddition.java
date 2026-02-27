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
package org.eclipse.ditto.policies.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
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
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;

/**
 * Command which retrieves the {@link org.eclipse.ditto.policies.model.EntryAddition} based on the passed in Policy ID,
 * imported Policy ID and Label.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = RetrievePolicyImportEntryAddition.NAME)
public final class RetrievePolicyImportEntryAddition extends AbstractCommand<RetrievePolicyImportEntryAddition>
        implements PolicyQueryCommand<RetrievePolicyImportEntryAddition> {

    /**
     * Name of the "Retrieve Policy Import Entry Addition" command.
     */
    public static final String NAME = "retrievePolicyImportEntryAddition";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final PolicyId importedPolicyId;
    private final Label label;

    private RetrievePolicyImportEntryAddition(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final Label label,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.importedPolicyId = checkNotNull(importedPolicyId, "importedPolicyId");
        this.label = checkNotNull(label, "label");
    }

    /**
     * Returns a command for retrieving the EntryAddition with the given Policy ID, imported Policy ID and Label.
     *
     * @param policyId the ID of the Policy for which to retrieve the EntryAddition for.
     * @param importedPolicyId the ID of the imported Policy.
     * @param label the Label of the EntryAddition to retrieve.
     * @param dittoHeaders the optional command headers of the request.
     * @return a Command for retrieving the EntryAddition.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyImportEntryAddition of(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final Label label,
            final DittoHeaders dittoHeaders) {

        return new RetrievePolicyImportEntryAddition(policyId, importedPolicyId, label, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrievePolicyImportEntryAddition} from a JSON string.
     *
     * @param jsonString the JSON string of which a new RetrievePolicyImportEntryAddition instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrievePolicyImportEntryAddition} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrievePolicyImportEntryAddition fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        final JsonObject jsonObject = JsonFactory.newObject(jsonString);
        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrievePolicyImportEntryAddition} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new RetrievePolicyImportEntryAddition instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrievePolicyImportEntryAddition} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrievePolicyImportEntryAddition fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandJsonDeserializer<RetrievePolicyImportEntryAddition>(TYPE, jsonObject).deserialize(() -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final PolicyId importedPolicyId = PolicyId.of(jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID));
            final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));

            return of(policyId, importedPolicyId, label, dittoHeaders);
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
     * Returns the {@code Label} of the {@code EntryAddition} to retrieve.
     *
     * @return the Label of the EntryAddition to retrieve.
     */
    public Label getLabel() {
        return label;
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/imports/" + importedPolicyId + "/entriesAdditions/" + label;
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, importedPolicyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
    }

    @Override
    public RetrievePolicyImportEntryAddition setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, importedPolicyId, label, dittoHeaders);
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
        final RetrievePolicyImportEntryAddition that = (RetrievePolicyImportEntryAddition) obj;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(importedPolicyId, that.importedPolicyId) &&
                Objects.equals(label, that.label) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrievePolicyImportEntryAddition;
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, importedPolicyId, label);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId +
                ", importedPolicyId=" + importedPolicyId + ", label=" + label + "]";
    }

}
