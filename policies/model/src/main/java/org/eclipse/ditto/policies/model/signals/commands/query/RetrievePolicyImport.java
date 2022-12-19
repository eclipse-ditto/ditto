/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;

/**
 * Command which retrieves the Policy import of a {@code Policy} based on the passed in Policy ID and imported Policy
 * ID.
 *
 * @since 3.1.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = RetrievePolicyImport.NAME)
public final class RetrievePolicyImport extends AbstractCommand<RetrievePolicyImport> implements
        PolicyQueryCommand<RetrievePolicyImport> {

    /**
     * Name of the retrieve "Retrieve Policy Entry" command.
     */
    public static final String NAME = "retrievePolicyImport";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final PolicyId importedPolicyId;

    private RetrievePolicyImport(final PolicyId importedPolicyId, final PolicyId policyId,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "Policy identifier");
        this.importedPolicyId = checkNotNull(importedPolicyId, "Imported Policy ID");
    }

    /**
     * Returns a command for retrieving a specific Policy import with the given ID and imported Policy ID.
     *
     * @param policyId the ID of a single Policy whose Policy import will be retrieved by this command.
     * @param importedPolicyId the specified importedPolicyId for which to retrieve the Policy import for.
     * @param dittoHeaders the optional command headers of the request.
     * @return a Command for retrieving one Policy import with the {@code policyId} and {@code importedPolicyId} which is
     * readable from the passed authorization context.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyImport of(final PolicyId policyId, final PolicyId importedPolicyId,
            final DittoHeaders dittoHeaders) {
        return new RetrievePolicyImport(importedPolicyId, policyId, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrievePolicyImport} from a JSON string.
     *
     * @param jsonString the JSON string of which a new RetrievePolicyImport instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrievePolicyImport} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrievePolicyImport fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject = JsonFactory.newObject(jsonString);

        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrievePolicyImport} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new RetrievePolicyImport instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrievePolicyImport} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrievePolicyImport fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrievePolicyImport>(TYPE, jsonObject).deserialize(() -> {
            final PolicyId policyId = PolicyId.of(
                    jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID));
            final PolicyId importedPolicyId = PolicyId.of(jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID));

            return of(policyId, importedPolicyId, dittoHeaders);
        });
    }

    /**
     * Returns the identifier of the imported Policy of the {@code PolicyImport} to retrieve.
     *
     * @return the identifier of the imported Policy of the PolicyImport to retrieve.
     */
    public PolicyId getImportedPolicyId() {
        return importedPolicyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/imports/" + importedPolicyId;
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, importedPolicyId.toString(), predicate);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public RetrievePolicyImport setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, importedPolicyId, dittoHeaders);
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
        final RetrievePolicyImport that = (RetrievePolicyImport) obj;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(importedPolicyId, that.importedPolicyId) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrievePolicyImport;
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, importedPolicyId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", importedPolicyId=" + importedPolicyId +
                "]";
    }

}
