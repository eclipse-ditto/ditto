/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.policies.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.PolicyImport;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link ModifyPolicyImport} command.
 */
@Immutable
public final class ModifyPolicyImportResponse extends AbstractCommandResponse<ModifyPolicyImportResponse>
        implements PolicyModifyCommandResponse<ModifyPolicyImportResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyPolicyImport.NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_POLICY_IMPORT =
            JsonFactory.newJsonValueFieldDefinition("policyImport", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    @Nullable private final PolicyImport policyImportCreated;

    private ModifyPolicyImportResponse(final PolicyId policyId,
            final HttpStatus statusCode,
            @Nullable final PolicyImport policyImportCreated,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "Policy ID");
        this.policyImportCreated = policyImportCreated;
    }

    /**
     * Creates a response to a {@code ModifyPolicyImport} command.
     *
     * @param policyId the Policy ID of the created policy entry.
     * @param policyImportCreated (optional) the PolicyImport created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code statusCode} or {@code dittoHeaders} is {@code null}.
     */
    public static ModifyPolicyImportResponse created(final PolicyId policyId, final PolicyImport policyImportCreated,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyImportResponse(policyId, HttpStatus.CREATED, policyImportCreated, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyImport} command.
     *
     * @param policyId the Policy ID of the modified policy entry.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyImportResponse modified(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        return new ModifyPolicyImportResponse(policyId, HttpStatus.NO_CONTENT, null, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyImport} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected format.
     */
    public static ModifyPolicyImportResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyImport} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static ModifyPolicyImportResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ModifyPolicyImportResponse>(TYPE, jsonObject)
                .deserialize(statusCode -> {
                    final PolicyId policyId = PolicyId.of(
                            jsonObject.getValueOrThrow(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID));
                    final Optional<String> readImportedPolicyId = jsonObject.getValue(JSON_IMPORTED_POLICY_ID);

                    final PolicyImport extractedPolicyImportCreated = jsonObject.getValue(JSON_POLICY_IMPORT)
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .flatMap(obj -> readImportedPolicyId.map(s ->
                                    PoliciesModelFactory.newPolicyImport(PolicyId.of(s), obj)))
                            .orElse(null);

                    return new ModifyPolicyImportResponse(policyId, statusCode, extractedPolicyImportCreated,
                            dittoHeaders);
                });
    }

    /**
     * Returns the created PolicyImport.
     *
     * @return the created PolicyImport.
     */
    public Optional<PolicyImport> getPolicyImportCreated() {
        return Optional.ofNullable(policyImportCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(policyImportCreated).map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        if (policyImportCreated == null) {
            return JsonPointer.empty();
        }

        final String path = "/imports/" + policyImportCreated.getImportedPolicyId();
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        if (null != policyImportCreated) {
            jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, policyImportCreated.getImportedPolicyId().toString(), predicate);
            jsonObjectBuilder.set(JSON_POLICY_IMPORT, policyImportCreated.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public ModifyPolicyImportResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return (policyImportCreated != null) ? created(policyId, policyImportCreated, dittoHeaders) :
                modified(policyId, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyImportResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyPolicyImportResponse that = (ModifyPolicyImportResponse) o;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId)
                && Objects.equals(policyImportCreated, that.policyImportCreated) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, policyImportCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId +
                ", policyImportCreated=" + policyImportCreated + "]";
    }

}
