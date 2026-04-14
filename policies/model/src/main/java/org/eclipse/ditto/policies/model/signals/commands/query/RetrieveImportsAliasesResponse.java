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
import org.eclipse.ditto.policies.model.ImportsAliases;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link RetrieveImportsAliases} command.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveImportsAliasesResponse.TYPE)
public final class RetrieveImportsAliasesResponse
        extends AbstractCommandResponse<RetrieveImportsAliasesResponse>
        implements PolicyQueryCommandResponse<RetrieveImportsAliasesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveImportsAliases.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_IMPORTS_ALIASES =
            JsonFactory.newJsonObjectFieldDefinition("importsAliases", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveImportsAliasesResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new RetrieveImportsAliasesResponse(
                                PolicyId.of(jsonObject.getValueOrThrow(
                                        PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                jsonObject.getValueOrThrow(JSON_IMPORTS_ALIASES),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final JsonObject importsAliases;

    private RetrieveImportsAliasesResponse(final PolicyId policyId, final JsonObject importsAliases,
            final HttpStatus statusCode, final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.importsAliases = checkNotNull(importsAliases, "importsAliases");
    }

    /**
     * Creates a response to a {@code RetrieveImportsAliases} command.
     *
     * @param policyId the Policy ID of the retrieved imports aliases.
     * @param importsAliases the retrieved imports aliases.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveImportsAliasesResponse of(final PolicyId policyId,
            final ImportsAliases importsAliases,
            final DittoHeaders dittoHeaders) {

        return new RetrieveImportsAliasesResponse(policyId,
                checkNotNull(importsAliases, "importsAliases")
                        .toJson(dittoHeaders.getSchemaVersion().orElse(importsAliases.getLatestSchemaVersion())),
                HTTP_STATUS,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveImportsAliases} command.
     *
     * @param policyId the Policy ID of the retrieved imports aliases.
     * @param importsAliases the retrieved imports aliases as JSON object.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveImportsAliasesResponse of(final PolicyId policyId,
            final JsonObject importsAliases,
            final DittoHeaders dittoHeaders) {

        return new RetrieveImportsAliasesResponse(policyId, importsAliases, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveImportsAliases} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveImportsAliasesResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveImportsAliases} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveImportsAliasesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * Returns the retrieved imports aliases.
     *
     * @return the retrieved imports aliases.
     */
    public ImportsAliases getImportsAliases() {
        return PoliciesModelFactory.newImportsAliases(importsAliases);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return importsAliases;
    }

    @Override
    public RetrieveImportsAliasesResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveImportsAliasesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, importsAliases, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/importsAliases");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_IMPORTS_ALIASES, importsAliases, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveImportsAliasesResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveImportsAliasesResponse that = (RetrieveImportsAliasesResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(importsAliases, that.importsAliases) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, importsAliases);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", importsAliases=" + importsAliases +
                "]";
    }

}
