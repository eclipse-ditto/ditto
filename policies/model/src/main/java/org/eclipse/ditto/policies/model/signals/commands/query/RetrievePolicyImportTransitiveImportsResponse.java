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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
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
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link RetrievePolicyImportTransitiveImports} command.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrievePolicyImportTransitiveImportsResponse.TYPE)
public final class RetrievePolicyImportTransitiveImportsResponse
        extends AbstractCommandResponse<RetrievePolicyImportTransitiveImportsResponse>
        implements PolicyQueryCommandResponse<RetrievePolicyImportTransitiveImportsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrievePolicyImportTransitiveImports.NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_TRANSITIVE_IMPORTS =
            JsonFactory.newJsonArrayFieldDefinition("transitiveImports", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrievePolicyImportTransitiveImportsResponse>
            JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new RetrievePolicyImportTransitiveImportsResponse(
                                PolicyId.of(jsonObject.getValueOrThrow(
                                        PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                PolicyId.of(jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID)),
                                jsonObject.getValueOrThrow(JSON_TRANSITIVE_IMPORTS),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final PolicyId importedPolicyId;
    private final JsonArray transitiveImports;

    private RetrievePolicyImportTransitiveImportsResponse(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final JsonArray transitiveImports,
            final HttpStatus statusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.importedPolicyId = checkNotNull(importedPolicyId, "importedPolicyId");
        this.transitiveImports = checkNotNull(transitiveImports, "transitiveImports");
    }

    /**
     * Creates a response to a {@code RetrievePolicyImportTransitiveImports} command.
     *
     * @param policyId the Policy ID of the retrieved resolve transitively configuration.
     * @param importedPolicyId the imported Policy ID.
     * @param transitiveImports the retrieved resolve transitively list of Policy IDs.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyImportTransitiveImportsResponse of(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final List<PolicyId> transitiveImports,
            final DittoHeaders dittoHeaders) {

        final JsonArray jsonArray = checkNotNull(transitiveImports, "transitiveImports").stream()
                .map(PolicyId::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());

        return new RetrievePolicyImportTransitiveImportsResponse(policyId, importedPolicyId, jsonArray,
                HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyImportTransitiveImports} command.
     *
     * @param policyId the Policy ID of the retrieved resolve transitively configuration.
     * @param importedPolicyId the imported Policy ID.
     * @param transitiveImports the retrieved resolve transitively as JSON array.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyImportTransitiveImportsResponse of(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final JsonArray transitiveImports,
            final DittoHeaders dittoHeaders) {

        return new RetrievePolicyImportTransitiveImportsResponse(policyId, importedPolicyId, transitiveImports,
                HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyImportTransitiveImports} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrievePolicyImportTransitiveImportsResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyImportTransitiveImports} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrievePolicyImportTransitiveImportsResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * Returns the retrieved resolve transitively list of Policy IDs.
     *
     * @return the retrieved resolve transitively list of Policy IDs.
     */
    public List<PolicyId> getTransitiveImports() {
        return transitiveImports.stream()
                .map(JsonValue::asString)
                .map(PolicyId::of)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return transitiveImports;
    }

    @Override
    public RetrievePolicyImportTransitiveImportsResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, importedPolicyId, entity.asArray(), getDittoHeaders());
    }

    @Override
    public RetrievePolicyImportTransitiveImportsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, importedPolicyId, transitiveImports, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
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
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, importedPolicyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_TRANSITIVE_IMPORTS, transitiveImports, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrievePolicyImportTransitiveImportsResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrievePolicyImportTransitiveImportsResponse that =
                (RetrievePolicyImportTransitiveImportsResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(importedPolicyId, that.importedPolicyId) &&
                Objects.equals(transitiveImports, that.transitiveImports) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, importedPolicyId, transitiveImports);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", importedPolicyId=" + importedPolicyId +
                ", transitiveImports=" + transitiveImports +
                "]";
    }

}
