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

package org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator.validateHttpStatus;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.api.common.CommonCommandResponse;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Response for a {@link CheckPermissions} command in the Eclipse Ditto framework.
 * This class encapsulates the response for permission checks that are carried out
 * when a CheckPermissions command is issued. The response includes the results of
 * the permission checks for various resources.
 * The {@link CheckPermissionsResponse} is immutable and provides methods to
 * construct the response, parse it from JSON, and serialize it back to JSON.
 *
 * @since 3.7.0
 */
@Immutable
@JsonParsableCommandResponse(type = CheckPermissionsResponse.TYPE)
public final class CheckPermissionsResponse extends CommonCommandResponse<CheckPermissionsResponse>
        implements WithEntity<CheckPermissionsResponse> {

    /**
     * The type identifier for the response.
     */
    public static final String TYPE = TYPE_PREFIX + CheckPermissionsResponse.NAME;

    static final String NAME = "checkPermissionsResponse";


    private static final JsonFieldDefinition<JsonObject> JSON_PERMISSION_RESULTS =
            JsonFieldDefinition.ofJsonObject("permissionResults", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private final JsonObject permissionResults;

    /**
     * Constructor to create a new {@code CheckPermissionsResponse}.
     *
     * @param permissionResults the permission results to include in the response.
     * @param httpStatus the HTTP status associated with the response.
     * @param dittoHeaders headers of the response.
     */
    private CheckPermissionsResponse(final JsonObject permissionResults,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {
        super(TYPE,
                validateHttpStatus(httpStatus, Collections.singleton(HTTP_STATUS), CheckPermissionsResponse.class),
                dittoHeaders);
        this.permissionResults = checkNotNull(permissionResults, "permissionResults");
    }

    /**
     * Build a new {@code CheckPermissionsResponse}.
     *
     * @param permissionResults the permission results map to respond with.
     * @param dittoHeaders the headers for the response.
     * @return a new instance of {@link CheckPermissionsResponse}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CheckPermissionsResponse of(final Map<String, Boolean> permissionResults,
            final DittoHeaders dittoHeaders) {
        checkNotNull(permissionResults, "permissionResults");
        final JsonObjectBuilder builder = JsonObject.newBuilder();
        permissionResults.forEach(builder::set);

        return new CheckPermissionsResponse(builder.build(), HTTP_STATUS, dittoHeaders);
    }

    /**
     * Create a {@code CheckPermissionsResponse} from a JSON object.
     *
     * @param jsonObject the JSON object representing the response.
     * @param dittoHeaders the headers of the preceding command.
     * @return a new instance of {@link CheckPermissionsResponse}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed-in {@code jsonObject}
     *         was not in the expected format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if any expected fields were missing in the
     *         passed-in {@code jsonObject}.
     */
    public static CheckPermissionsResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CheckPermissionsResponse(jsonObject.getValueOrThrow(JSON_PERMISSION_RESULTS), HTTP_STATUS,
                dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate)
    {
        final Predicate<JsonField> extendedPredicate = schemaVersion.and(predicate);
        jsonObjectBuilder.set(JSON_PERMISSION_RESULTS, permissionResults, extendedPredicate);
    }

    @Override
    public CheckPermissionsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new CheckPermissionsResponse(permissionResults, getHttpStatus(), dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (super.equals(o) && o instanceof CheckPermissionsResponse that) {
            return Objects.equals(permissionResults, that.permissionResults);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), permissionResults);
    }

    @Override
    @Nonnull
    public String toString() {
        return getClass().getSimpleName() + "[" +
                super.toString() +
                "permissionResults=" + permissionResults +
                "]";
    }

    @Override
    @Nonnull
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    @Override
    public CheckPermissionsResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return new CheckPermissionsResponse(entity.asObject(), getHttpStatus(), getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return permissionResults;
    }
}
