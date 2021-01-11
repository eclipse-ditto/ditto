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
package org.eclipse.ditto.signals.commands.policies.actions;

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
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandResponse;

/**
 * Response to an {@link DeactivatePolicyTokenIntegration} command.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableCommandResponse(type = DeactivatePolicyTokenIntegrationResponse.TYPE)
public final class DeactivatePolicyTokenIntegrationResponse
        extends AbstractCommandResponse<DeactivatePolicyTokenIntegrationResponse>
        implements PolicyActionCommandResponse<DeactivatePolicyTokenIntegrationResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + DeactivatePolicyTokenIntegration.NAME;

    /**
     * Status code of this response.
     */
    public static final HttpStatusCode STATUS = HttpStatusCode.OK;

    static final JsonFieldDefinition<String> JSON_SUBJECT_ID =
            JsonFactory.newStringFieldDefinition("subjectId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final SubjectId subjectId;

    private DeactivatePolicyTokenIntegrationResponse(final PolicyId policyId, final SubjectId subjectId,
            final DittoHeaders dittoHeaders) {

        super(TYPE, STATUS, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.subjectId = checkNotNull(subjectId, "subjectId");
    }

    /**
     * Creates a response to an {@code DeactivatePolicyTokenIntegration} command.
     *
     * @param policyId the policy ID.
     * @param subjectId the added subject ID.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DeactivatePolicyTokenIntegrationResponse of(final PolicyId policyId,
            final SubjectId subjectId,
            final DittoHeaders dittoHeaders) {
        return new DeactivatePolicyTokenIntegrationResponse(policyId, subjectId, dittoHeaders);
    }

    /**
     * Creates a response to a {@code DeactivatePolicyTokenIntegration} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    @SuppressWarnings("unused") // called by reflection
    public static DeactivatePolicyTokenIntegrationResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final PolicyId policyId =
                PolicyId.of(jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID));
        final SubjectId subjectId = SubjectId.newInstance(jsonObject.getValueOrThrow(JSON_SUBJECT_ID));
        return new DeactivatePolicyTokenIntegrationResponse(policyId, subjectId, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ID, subjectId.toString(), predicate);
    }

    @Override
    public DeactivatePolicyTokenIntegrationResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new DeactivatePolicyTokenIntegrationResponse(policyId, subjectId, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DeactivatePolicyTokenIntegrationResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeactivatePolicyTokenIntegrationResponse that = (DeactivatePolicyTokenIntegrationResponse) o;
        return Objects.equals(policyId, that.policyId) &&
                Objects.equals(subjectId, that.subjectId) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, subjectId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                " [" + super.toString() +
                ", policyId=" + policyId +
                ", subjectId=" + subjectId +
                "]";
    }

}
