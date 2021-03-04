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
package org.eclipse.ditto.signals.commands.policies.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
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
import org.eclipse.ditto.model.policies.PolicyImports;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link RetrievePolicyImports} command.
 */
@Immutable
public final class RetrievePolicyImportsResponse extends AbstractCommandResponse<RetrievePolicyImportsResponse>
        implements PolicyQueryCommandResponse<RetrievePolicyImportsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrievePolicyImports.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_POLICY_IMPORTS =
            JsonFactory.newJsonObjectFieldDefinition("policyImports", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final JsonObject policyImports;

    private RetrievePolicyImportsResponse(final PolicyId policyId,
            final HttpStatus statusCode,
            final JsonObject policyImports,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "Policy ID");
        this.policyImports = checkNotNull(policyImports, "Policy imports");
    }

    /**
     * Creates a response to a {@code RetrievePolicyImports} command.
     *
     * @param policyId the Policy ID of the retrieved policy imports.
     * @param policyImports the retrieved Policy imports.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyImportsResponse of(final PolicyId policyId, final PolicyImports policyImports,
            final DittoHeaders dittoHeaders) {

        return new RetrievePolicyImportsResponse(policyId, HttpStatus.OK,
                policyImports.toJson(dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST)), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyImports} command.
     *
     * @param policyId the Policy ID of the retrieved policy imports.
     * @param policyImports the retrieved Policy imports.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyImportsResponse of(final PolicyId policyId, final JsonObject policyImports,
            final DittoHeaders dittoHeaders) {

        return new RetrievePolicyImportsResponse(policyId, HttpStatus.OK, policyImports, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyImports} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected format.
     */
    public static RetrievePolicyImportsResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyImports} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static RetrievePolicyImportsResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandResponseJsonDeserializer<RetrievePolicyImportsResponse>(TYPE, jsonObject)
                .deserialize(statusCode -> {
                    final PolicyId policyId = PolicyId.of(
                            jsonObject.getValueOrThrow(PolicyQueryCommandResponse.JsonFields.JSON_POLICY_ID));
                    final JsonObject extractedPolicyImports = jsonObject.getValueOrThrow(JSON_POLICY_IMPORTS);

                    return of(policyId, extractedPolicyImports, dittoHeaders);
                });
    }

    /**
     * Returns the retrieved Policy imports.
     *
     * @return the retrieved Policy imports.
     */
    public PolicyImports getPolicyImports() {
        return PoliciesModelFactory.newPolicyImports(policyImports);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return policyImports;
    }

    @Override
    public RetrievePolicyImportsResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrievePolicyImportsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, policyImports, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/imports");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyQueryCommandResponse.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_POLICY_IMPORTS, policyImports, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrievePolicyImportsResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrievePolicyImportsResponse that = (RetrievePolicyImportsResponse) o;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) &&
                Objects.equals(policyImports, that.policyImports) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, policyImports);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", policyImports=" +
                policyImports + "]";
    }

}
