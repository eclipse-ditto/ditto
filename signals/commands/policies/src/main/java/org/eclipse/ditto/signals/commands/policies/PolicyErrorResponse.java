/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.policies;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyErrorRegistry;

/**
 * Response to a {@link PolicyCommand} which wraps the exception thrown while processing the command.
 */
@Immutable
public final class PolicyErrorResponse extends AbstractCommandResponse<PolicyErrorResponse> implements
        PolicyCommandResponse<PolicyErrorResponse>, ErrorResponse<PolicyErrorResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + "errorResponse";

    private static final PolicyErrorRegistry POLICY_ERROR_REGISTRY = PolicyErrorRegistry.newInstance();

    private final String policyId;
    private final DittoRuntimeException dittoRuntimeException;

    private PolicyErrorResponse(final String policyId, final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoRuntimeException.getStatusCode(), dittoHeaders);
        this.policyId = requireNonNull(policyId, "Policy ID");
        this.dittoRuntimeException =
                requireNonNull(dittoRuntimeException, "The Ditto Runtime Exception must not be null");
    }

    /**
     * Creates a new {@code PolicyErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param dittoRuntimeException the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static PolicyErrorResponse of(final DittoRuntimeException dittoRuntimeException) {
        return of("unknown:unknown", dittoRuntimeException, dittoRuntimeException.getDittoHeaders());
    }

    /**
     * Creates a new {@code PolicyErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param policyId the Policy ID related to the exception.
     * @param dittoRuntimeException the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static PolicyErrorResponse of(final String policyId, final DittoRuntimeException dittoRuntimeException) {
        return new PolicyErrorResponse(policyId, dittoRuntimeException, dittoRuntimeException.getDittoHeaders());
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

        return of("unknown:unknown", dittoRuntimeException, dittoHeaders);
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
    public static PolicyErrorResponse of(final String policyId, final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {

        return new PolicyErrorResponse(policyId, dittoRuntimeException, dittoHeaders);
    }

    public static PolicyErrorResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return fromJson(jsonObject, dittoHeaders);
    }

    public static PolicyErrorResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String policyId = jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID);
        final JsonObject payload = jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.PAYLOAD).asObject();
        final DittoRuntimeException exception = POLICY_ERROR_REGISTRY.parse(payload, dittoHeaders);

        return of(policyId, exception, dittoHeaders);
    }

    @Override
    public String getId() {
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
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, policyId, predicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.PAYLOAD,
                dittoRuntimeException.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public PolicyErrorResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, dittoRuntimeException, dittoHeaders);
    }

    @Override
    protected boolean canEqual(final Object other) {
        return (other instanceof PolicyErrorResponse);
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
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) &&
                Objects.equals(dittoRuntimeException, that.dittoRuntimeException) && super.equals(o);
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
