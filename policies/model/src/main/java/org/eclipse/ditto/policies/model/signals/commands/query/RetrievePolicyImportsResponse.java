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

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link RetrievePolicyImports} command.
 *
 * @since 3.1.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrievePolicyImportsResponse.TYPE)
public final class RetrievePolicyImportsResponse extends AbstractCommandResponse<RetrievePolicyImportsResponse>
        implements PolicyQueryCommandResponse<RetrievePolicyImportsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrievePolicyImports.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_POLICY_IMPORTS =
            JsonFactory.newJsonObjectFieldDefinition("policyImports", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrievePolicyImportsResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new RetrievePolicyImportsResponse(
                                PolicyId.of(jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                jsonObject.getValueOrThrow(JSON_POLICY_IMPORTS),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final JsonObject policyImports;

    private RetrievePolicyImportsResponse(final PolicyId policyId,
            final JsonObject policyImports,
            final HttpStatus statusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.policyImports = checkNotNull(policyImports, "policyImports");
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

        final JsonObject importsObject = policyImports.toJson(dittoHeaders.getSchemaVersion()
                .orElse(JsonSchemaVersion.LATEST));
        return new RetrievePolicyImportsResponse(policyId,importsObject, HTTP_STATUS, dittoHeaders);
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

        return new RetrievePolicyImportsResponse(policyId, policyImports, HTTP_STATUS, dittoHeaders);
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
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
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
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
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
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(policyImports, that.policyImports) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, policyImports);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", policyImports=" + policyImports +
                "]";
    }

}
