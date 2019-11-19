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
package org.eclipse.ditto.signals.commands.policies.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link ModifySubject} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifySubjectResponse.TYPE)
public final class ModifySubjectResponse extends AbstractCommandResponse<ModifySubjectResponse>
        implements PolicyModifyCommandResponse<ModifySubjectResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifySubject.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_SUBJECT_ID =
            JsonFactory.newStringFieldDefinition("subjectId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_SUBJECT =
            JsonFactory.newJsonValueFieldDefinition("subject", FieldType.REGULAR, JsonSchemaVersion.V_2);


    private final PolicyId policyId;
    private final Label label;
    private final SubjectId subjectId;
    @Nullable private final Subject subjectCreated;

    private ModifySubjectResponse(final PolicyId policyId,
            final Label label,
            final SubjectId subjectId,
            @Nullable final Subject subjectCreated,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.subjectId = checkNotNull(subjectId, "subjectId");
        this.label = checkNotNull(label, "label");
        this.subjectCreated = subjectCreated;
    }

    /**
     * Creates a response to a {@code ModifySubject} command.
     *
     * @param policyId the Policy ID of the created subject.
     * @param label the Label of the PolicyEntry.
     * @param subjectCreated (optional) the Subject created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code statusCode} or {@code dittoHeaders} is {@code null}.
     * @deprecated Policy ID is now typed. Use {@link #created(org.eclipse.ditto.model.policies.PolicyId,
     * org.eclipse.ditto.model.policies.Label, org.eclipse.ditto.model.policies.Subject,
     * org.eclipse.ditto.model.base.headers.DittoHeaders)} instead.
     */
    @Deprecated
    public static ModifySubjectResponse created(final String policyId,
            final Label label,
            final Subject subjectCreated,
            final DittoHeaders dittoHeaders) {

        return created(PolicyId.of(policyId), label, subjectCreated, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifySubject} command.
     *
     * @param policyId the Policy ID of the created subject.
     * @param label the Label of the PolicyEntry.
     * @param subjectCreated (optional) the Subject created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code statusCode} or {@code dittoHeaders} is {@code null}.
     */
    public static ModifySubjectResponse created(final PolicyId policyId,
            final Label label,
            final Subject subjectCreated,
            final DittoHeaders dittoHeaders) {

        return new ModifySubjectResponse(policyId, label, subjectCreated.getId(), subjectCreated,
                HttpStatusCode.CREATED, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifySubject} command.
     *
     * @param policyId the Policy ID of the modified subject.
     * @param label the Label of the PolicyEntry.
     * @param subjectId the subject id of the modified subject
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @deprecated Policy ID is now typed. Use
     * {@link #modified(org.eclipse.ditto.model.policies.PolicyId, org.eclipse.ditto.model.policies.Label, org.eclipse.ditto.model.policies.SubjectId, org.eclipse.ditto.model.base.headers.DittoHeaders)}
     * instead.
     */
    @Deprecated
    public static ModifySubjectResponse modified(final String policyId, final Label label, final SubjectId subjectId,
            final DittoHeaders dittoHeaders) {

        return modified(PolicyId.of(policyId), label, subjectId, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifySubject} command.
     *
     * @param policyId the Policy ID of the modified subject.
     * @param label the Label of the PolicyEntry.
     * @param subjectId the subject id of the modified subject
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifySubjectResponse modified(final PolicyId policyId, final Label label, final SubjectId subjectId,
            final DittoHeaders dittoHeaders) {
        return new ModifySubjectResponse(policyId, label, subjectId, null, HttpStatusCode.NO_CONTENT, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifySubject} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected format.
     */
    public static ModifySubjectResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifySubject} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static ModifySubjectResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ModifySubjectResponse>(TYPE, jsonObject).deserialize(statusCode -> {
            final String extractedPolicyId =
                    jsonObject.getValueOrThrow(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final Label label = PoliciesModelFactory.newLabel(jsonObject.getValueOrThrow(JSON_LABEL));

            final SubjectId extractedSubjectId = SubjectId.newInstance(jsonObject.getValueOrThrow(JSON_SUBJECT_ID));

            final Subject extractedSubjectCreated = jsonObject.getValue(JSON_SUBJECT)
                    .map(JsonValue::asObject)
                    .map(obj -> PoliciesModelFactory.newSubject(extractedSubjectId, obj))
                    .orElse(null);

            return new ModifySubjectResponse(policyId, label, extractedSubjectId, extractedSubjectCreated, statusCode,
                    dittoHeaders);
        });
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose {@code Subject} was modified
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the created {@code Subject}.
     *
     * @return the created Subject.
     */
    Optional<Subject> getSubjectCreated() {
        return Optional.ofNullable(subjectCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(subjectCreated).map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/subjects/" + subjectId;
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId),
                predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ID, subjectId.toString(), predicate);
        if (null != subjectCreated) {
            jsonObjectBuilder.set(JSON_SUBJECT, subjectCreated.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public ModifySubjectResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return subjectCreated != null
                ? created(policyId, label, subjectCreated, dittoHeaders)
                : modified(policyId, label, subjectId, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifySubjectResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifySubjectResponse that = (ModifySubjectResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(subjectCreated, that.subjectCreated) &&
                Objects.equals(subjectId, that.subjectId) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, subjectCreated, subjectId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label +
                ", subjectCreated=" + subjectCreated + ", subjectId=" + subjectId + "]";
    }

}
