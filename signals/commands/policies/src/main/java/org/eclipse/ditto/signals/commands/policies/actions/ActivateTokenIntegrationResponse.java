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
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandResponse;

/**
 * Response to an {@link ActivateTokenIntegration} command.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableCommandResponse(type = ActivateTokenIntegrationResponse.TYPE)
public final class ActivateTokenIntegrationResponse
        extends AbstractCommandResponse<ActivateTokenIntegrationResponse>
        implements PolicyActionCommandResponse<ActivateTokenIntegrationResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ActivateTokenIntegration.NAME;

    /**
     * Status code of this response.
     */
    public static final HttpStatus STATUS = HttpStatus.NO_CONTENT;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_SUBJECT_ID =
            JsonFactory.newStringFieldDefinition("subjectId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final Label label;
    private final SubjectId subjectId;

    private ActivateTokenIntegrationResponse(final PolicyId policyId, final Label label, final SubjectId subjectId,
            final DittoHeaders dittoHeaders) {

        super(TYPE, STATUS, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.subjectId = checkNotNull(subjectId, "subjectId");
    }

    /**
     * Creates a response to an {@code ActivateTokenIntegration} command.
     *
     * @param policyId the policy ID.
     * @param label the policy entry label.
     * @param subjectId the added subject ID.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ActivateTokenIntegrationResponse of(final PolicyId policyId, final Label label, final SubjectId subjectId,
            final DittoHeaders dittoHeaders) {
        return new ActivateTokenIntegrationResponse(policyId, label, subjectId, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ActivateTokenIntegration} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    @SuppressWarnings("unused") // called by reflection
    public static ActivateTokenIntegrationResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final PolicyId policyId =
                PolicyId.of(jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID));
        final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
        final SubjectId subjectId = SubjectId.newInstance(jsonObject.getValueOrThrow(JSON_SUBJECT_ID));
        return new ActivateTokenIntegrationResponse(policyId, label, subjectId, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return Policy.JsonFields.ENTRIES.getPointer()
                .addLeaf(JsonKey.of(label))
                .append(RESOURCE_PATH_ACTIONS)
                .addLeaf(JsonKey.of(ActivateTokenIntegration.NAME));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ID, subjectId.toString(), predicate);
    }

    @Override
    public ActivateTokenIntegrationResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ActivateTokenIntegrationResponse(policyId, label, subjectId, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ActivateTokenIntegrationResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ActivateTokenIntegrationResponse that = (ActivateTokenIntegrationResponse) o;
        return Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(subjectId, that.subjectId) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, subjectId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                " [" + super.toString() +
                ", policyId=" + policyId +
                ", label=" + label +
                ", subjectId=" + subjectId +
                "]";
    }

}
