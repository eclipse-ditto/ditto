
/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Response to an {@link PolicyCheckPermissionsCommand}.
 * <p>
 * This class holds the results of the permission checks for each resource. The result is
 * a {@link Map} with the resource identifier as the key and {@code true}/{@code false} as
 * the value, indicating whether the requested permission was granted or denied.
 */
@Immutable
@JsonParsableCommandResponse(type = PolicyCheckPermissionsResponse.TYPE)
public final class PolicyCheckPermissionsResponse extends AbstractCommandResponse<PolicyCheckPermissionsResponse>
        implements PolicyQueryCommandResponse<PolicyCheckPermissionsResponse> {

    public static final String TYPE = "policyCheckPermissionsResponse";

    public static final String PERMISSIONS_RESULTS = "permissionsResults";

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private final PolicyId policyId;
    private final JsonObject permissionsResults;

    private PolicyCheckPermissionsResponse(final PolicyId policyId, final JsonObject permissionResults,
            final HttpStatus statusCode, final DittoHeaders dittoHeaders) {
        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.permissionsResults = checkNotNull(permissionResults, "permissionResults");
    }

    /**
     * Creates a response for {@link PolicyCheckPermissionsCommand}.
     *
     * @param policyId the ID of the policy being enforced.
     * @param permissionResults the results of permission checks.
     * @param dittoHeaders the headers of the preceding command.
     * @return a new {@code EnforcePolicyResponse}.
     */
    public static PolicyCheckPermissionsResponse of(final PolicyId policyId,
            final Map<String, Boolean> permissionResults,
            final DittoHeaders dittoHeaders) {

        return new PolicyCheckPermissionsResponse(policyId, fromMap(permissionResults), HTTP_STATUS, dittoHeaders);
    }

    public static PolicyCheckPermissionsResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final String extractedPolicyId = jsonObject.getValueOrThrow(JsonFieldDefinition.ofString("policyId"));
        final PolicyId policyId = PolicyId.of(extractedPolicyId);
        return new PolicyCheckPermissionsResponse(policyId,
                jsonObject.getValueOrThrow(JsonFieldDefinition.ofJsonObject(PERMISSIONS_RESULTS)), HTTP_STATUS,
                dittoHeaders);
    }

    public JsonObject getPermissionsResults() {
        return permissionsResults;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        jsonObjectBuilder.set("policyId", policyId.toString());
        jsonObjectBuilder.set(PERMISSIONS_RESULTS, JsonFactory.newObject(permissionsResults.toString()));
    }

    @Override
    public boolean equals(@Nullable final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PolicyCheckPermissionsResponse)) {
            return false;
        }
        final PolicyCheckPermissionsResponse that = (PolicyCheckPermissionsResponse) other;
        return Objects.equals(policyId, that.policyId) &&
                Objects.equals(permissionsResults, that.permissionsResults) &&
                super.equals(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, permissionsResults);
    }

    @Override
    public String toString() {
        return "PolicyCheckPermissionsResponse{" +
                "policyId=" + policyId +
                ", permissionResults=" + permissionsResults +
                '}';
    }

    @Override
    public PolicyCheckPermissionsResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, toMap(entity.asObject()), getDittoHeaders());
    }

    @Override
    public PolicyCheckPermissionsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, toMap(permissionsResults), dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return permissionsResults;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/permissionResults");
    }

    public static Map<String, Boolean> toMap(JsonObject jsonObject) {
        final JsonObject permissionsResultsJsonObject =
                jsonObject.getValueOrThrow(JsonFieldDefinition.ofJsonObject(PERMISSIONS_RESULTS));

        return permissionsResultsJsonObject.getKeys()
                .stream()
                .collect(Collectors.toMap(
                        key -> key.toString(),
                        key -> permissionsResultsJsonObject.getValue(key).get().asBoolean()
                ));
    }


    public static JsonObject fromMap(Map<String, Boolean> map) {
        final JsonObjectBuilder resultsJson = JsonFactory.newObjectBuilder();

        map.forEach((key, result) -> resultsJson.set(key, JsonFactory.newValue(result)));

        final JsonObjectBuilder mapJson = JsonFactory.newObjectBuilder();

        return mapJson.set(PERMISSIONS_RESULTS, resultsJson.build()).build();
    }

}
