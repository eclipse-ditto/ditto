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

package org.eclipse.ditto.policies.api.commands.sudo;

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
 * Response for a {@link CheckPolicyPermissions} command in the Ditto framework.
 * <p>
 * This class encapsulates the results of permission checks performed on various resources within a policy.
 * The response contains a map of resource identifiers and corresponding boolean values indicating whether
 * each permission was granted or denied.
 * <p>
 * This response is immutable and provides methods to build, parse from JSON, and convert permission results
 * to and from a map format.
 *
 * @since 3.7.0
 */
@Immutable
@JsonParsableCommandResponse(type = CheckPolicyPermissionsResponse.TYPE)
public final class CheckPolicyPermissionsResponse extends AbstractCommandResponse<CheckPolicyPermissionsResponse>
        implements PolicySudoQueryCommandResponse<CheckPolicyPermissionsResponse> {

    /**
     * The type of this response.
     */
    public static final String TYPE = "checkPolicyPermissionsResponse";

    /**
     * The key for the permission results field in the JSON response.
     */
    private static final String PERMISSIONS_RESULTS = "permissionsResults";

    private static final JsonFieldDefinition<JsonObject> PERMISSIONS_RESULTS_FIELD =
            JsonFactory.newJsonObjectFieldDefinition("permissionsResults");

    private static final JsonFieldDefinition<String> POLICY_ID_FIELD =
            JsonFactory.newStringFieldDefinition("policyId");

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private final PolicyId policyId;
    private final JsonObject permissionsResults;

    private CheckPolicyPermissionsResponse(final PolicyId policyId, final JsonObject permissionResults,
            final HttpStatus statusCode, final DittoHeaders dittoHeaders) {
        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.permissionsResults = checkNotNull(permissionResults, "permissionResults");
    }

    /**
     * Creates a response for a {@link CheckPolicyPermissions} command.
     *
     * @param policyId the ID of the policy being checked.
     * @param permissionResults the results of the permission checks.
     * @param dittoHeaders the headers associated with the command.
     * @return a new {@link CheckPolicyPermissionsResponse}.
     */
    public static CheckPolicyPermissionsResponse of(final PolicyId policyId,
            final Map<String, Boolean> permissionResults,
            final DittoHeaders dittoHeaders) {

        return new CheckPolicyPermissionsResponse(policyId, fromMap(permissionResults), HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response from a JSON object.
     *
     * @param jsonObject the JSON object to parse the response from.
     * @param dittoHeaders the headers associated with the command.
     * @return a new {@link CheckPolicyPermissionsResponse}.
     */
    public static CheckPolicyPermissionsResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final String extractedPolicyId = jsonObject.getValueOrThrow(POLICY_ID_FIELD);
        final PolicyId policyId = PolicyId.of(extractedPolicyId);
        return new CheckPolicyPermissionsResponse(policyId,
                jsonObject.getValueOrThrow(PERMISSIONS_RESULTS_FIELD), HTTP_STATUS,
                dittoHeaders);
    }

    /**
     * Returns the results of the permission checks.
     *
     * @return the permission results as a {@link JsonObject}.
     */
    public JsonObject getPermissionsResults() {
        return permissionsResults;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(POLICY_ID_FIELD, policyId.toString());
        jsonObjectBuilder.set(PERMISSIONS_RESULTS_FIELD, permissionsResults);
    }

    @Override
    public boolean equals(@Nullable final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CheckPolicyPermissionsResponse)) {
            return false;
        }
        final CheckPolicyPermissionsResponse that = (CheckPolicyPermissionsResponse) other;
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
        return "PolicyCheckPermissionsResponse[" +
                "policyId=" + policyId +
                ", permissionResults=" + permissionsResults +
                ']';
    }

    @Override
    public CheckPolicyPermissionsResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, toMap(entity.asObject()), getDittoHeaders());
    }

    @Override
    public CheckPolicyPermissionsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, toMap(permissionsResults), dittoHeaders);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return permissionsResults;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/permissionResults");
    }

    /**
     * Converts a {@link JsonObject} to a map of permission results.
     *
     * @param jsonObject the JSON object to convert.
     * @return a map of permission results.
     */
    public static Map<String, Boolean> toMap(final JsonObject jsonObject) {
        final JsonObject permissionsResultsJsonObject =
                jsonObject.getValueOrThrow(JsonFieldDefinition.ofJsonObject(PERMISSIONS_RESULTS));

        return permissionsResultsJsonObject.getKeys()
                .stream()
                .collect(Collectors.toMap(
                        key -> key.toString(),
                        key -> permissionsResultsJsonObject.getValue(key).get().asBoolean()
                ));
    }

    /**
     * Converts a map of permission results to a {@link JsonObject}.
     *
     * @param map the map of permission results.
     * @return a {@link JsonObject} representing the permission results.
     */
    public static JsonObject fromMap(final Map<String, Boolean> map) {
        final JsonObjectBuilder resultsJson = JsonFactory.newObjectBuilder();

        map.forEach((key, result) -> resultsJson.set(key, JsonFactory.newValue(result)));

        final JsonObjectBuilder mapJson = JsonFactory.newObjectBuilder();

        return mapJson.set(PERMISSIONS_RESULTS, resultsJson.build()).build();
    }

}
