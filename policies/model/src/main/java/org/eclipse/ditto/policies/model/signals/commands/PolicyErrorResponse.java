/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.base.model.signals.commands.AbstractErrorResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Response to a {@link PolicyCommand} which wraps the exception thrown while processing the command.
 */
@Immutable
@JsonParsableCommandResponse(type = PolicyErrorResponse.TYPE)
public final class PolicyErrorResponse extends AbstractErrorResponse<PolicyErrorResponse>
        implements PolicyCommandResponse<PolicyErrorResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + "errorResponse";

    private static final GlobalErrorRegistry GLOBAL_ERROR_REGISTRY = GlobalErrorRegistry.getInstance();

    private static final PolicyId FALLBACK_POLICY_ID = PolicyId.of(FALLBACK_ID);

    private final PolicyId policyId;
    private final DittoRuntimeException dittoRuntimeException;

    private PolicyErrorResponse(final PolicyId policyId, final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoRuntimeException.getHttpStatus(), dittoHeaders);
        this.policyId = checkNotNull(policyId, "Policy ID");
        this.dittoRuntimeException = checkNotNull(dittoRuntimeException, "dittoRuntimeException");
    }

    /**
     * Creates a new {@code PolicyErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param dittoRuntimeException the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static PolicyErrorResponse of(final DittoRuntimeException dittoRuntimeException) {
        final DittoHeaders dittoHeaders = dittoRuntimeException.getDittoHeaders();
        final String nullableEntityId = dittoHeaders.get(DittoHeaderDefinition.ENTITY_ID.getKey());
        final PolicyId policyId = Optional.ofNullable(nullableEntityId)
                .map(entityId -> entityId.substring(entityId.indexOf(":") + 1)) // starts with "policy:" - cut that off!
                .map(PolicyId::of)
                .orElse(FALLBACK_POLICY_ID);
        return of(policyId, dittoRuntimeException, dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param policyId the Policy ID related to the exception.
     * @param dittoRuntimeException the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static PolicyErrorResponse of(final PolicyId policyId, final DittoRuntimeException dittoRuntimeException) {
        return of(policyId, dittoRuntimeException, dittoRuntimeException.getDittoHeaders());
    }

    /**
     * Creates a new {@code PolicyErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param dittoRuntimeException the exception.
     * @param dittoHeaders the headers of the command which caused the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static PolicyErrorResponse of(final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {

        return of(FALLBACK_POLICY_ID, dittoRuntimeException, dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param policyId the Policy ID related to the exception.
     * @param dittoRuntimeException the exception.
     * @param dittoHeaders the headers of the command which caused the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static PolicyErrorResponse of(final PolicyId policyId, final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {

        return new PolicyErrorResponse(policyId, dittoRuntimeException, dittoHeaders);
    }

    public static PolicyErrorResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return fromJson(jsonObject, dittoHeaders);
    }

    public static PolicyErrorResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID);
        final PolicyId policyId = PolicyId.of(extractedPolicyId);
        final JsonObject payload = jsonObject.getValueOrThrow(CommandResponse.JsonFields.PAYLOAD).asObject();
        final DittoRuntimeException exception = buildExceptionFromJson(GLOBAL_ERROR_REGISTRY, payload, dittoHeaders);
        return of(policyId, exception, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public DittoRuntimeException getDittoRuntimeException() {
        return dittoRuntimeException;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(CommandResponse.JsonFields.PAYLOAD,
                dittoRuntimeException.toJson(schemaVersion, thePredicate),
                predicate);
    }

    @Override
    public PolicyErrorResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, dittoRuntimeException, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyErrorResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PolicyErrorResponse that = (PolicyErrorResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(dittoRuntimeException, that.dittoRuntimeException) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, dittoRuntimeException);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId
                + ", dittoRuntimeException=" + dittoRuntimeException +
                "]";
    }

}
