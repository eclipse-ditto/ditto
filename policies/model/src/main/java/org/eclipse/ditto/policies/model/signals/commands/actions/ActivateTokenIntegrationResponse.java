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
package org.eclipse.ditto.policies.model.signals.commands.actions;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

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

    static final JsonFieldDefinition<JsonArray> JSON_SUBJECT_IDS =
            JsonFactory.newJsonArrayFieldDefinition("subjectIds", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final Label label;
    private final Collection<SubjectId> subjectIds;

    private ActivateTokenIntegrationResponse(final PolicyId policyId, final Label label,
            final Collection<SubjectId> subjectIds, final DittoHeaders dittoHeaders) {

        super(TYPE, STATUS, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.subjectIds = Collections.unmodifiableSet(new LinkedHashSet<>(checkNotNull(subjectIds, "subjectIds")));
    }

    /**
     * Creates a response to an {@code ActivateTokenIntegration} command.
     *
     * @param policyId the policy ID.
     * @param label the policy entry label.
     * @param subjectIds the added subject IDs.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ActivateTokenIntegrationResponse of(final PolicyId policyId, final Label label,
            final Collection<SubjectId> subjectIds, final DittoHeaders dittoHeaders) {
        return new ActivateTokenIntegrationResponse(policyId, label, subjectIds, dittoHeaders);
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
    public static ActivateTokenIntegrationResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final PolicyId policyId =
                PolicyId.of(jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID));
        final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
        final Set<SubjectId> subjectIds = jsonObject.getValueOrThrow(JSON_SUBJECT_IDS).stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(SubjectId::newInstance)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ActivateTokenIntegrationResponse(policyId, label, subjectIds, dittoHeaders);
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
        jsonObjectBuilder.set(JSON_SUBJECT_IDS, subjectIds.stream()
                .map(SubjectId::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray()), predicate);
    }

    @Override
    public ActivateTokenIntegrationResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ActivateTokenIntegrationResponse(policyId, label, subjectIds, dittoHeaders);
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
                Objects.equals(subjectIds, that.subjectIds) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, subjectIds);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                " [" + super.toString() +
                ", policyId=" + policyId +
                ", label=" + label +
                ", subjectIds=" + subjectIds +
                "]";
    }

}
