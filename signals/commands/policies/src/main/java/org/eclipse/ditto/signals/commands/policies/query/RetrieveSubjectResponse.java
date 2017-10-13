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
package org.eclipse.ditto.signals.commands.policies.query;

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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link RetrieveSubject} command.
 */
@Immutable
public final class RetrieveSubjectResponse extends AbstractCommandResponse<RetrieveSubjectResponse> implements
        PolicyQueryCommandResponse<RetrieveSubjectResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveSubject.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_SUBJECT_ID =
            JsonFactory.newStringFieldDefinition("subjectId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_SUBJECT =
            JsonFactory.newJsonObjectFieldDefinition("subject", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String policyId;
    private final Label label;
    private final String subjectId;
    private final JsonObject subject;

    private RetrieveSubjectResponse(final String policyId,
            final Label label,
            final String subjectId,
            final JsonObject subject,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "Policy ID");
        this.label = checkNotNull(label, "Label");
        this.subjectId = checkNotNull(subjectId, "Subject ID");
        this.subject = checkNotNull(subject, "Subject");
    }

    /**
     * Creates a response to a {@code RetrieveSubject} command.
     *
     * @param policyId the Policy ID of the retrieved subject.
     * @param label the Label of the PolicyEntry.
     * @param subjectId the ID of the retrieved Subject.
     * @param subject the retrieved Subject.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveSubjectResponse of(final String policyId,
            final Label label,
            final String subjectId,
            final JsonObject subject,
            final DittoHeaders dittoHeaders) {

        return new RetrieveSubjectResponse(policyId, label, subjectId, subject, HttpStatusCode.OK, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveSubject} command.
     *
     * @param policyId the Policy ID of the retrieved subject.
     * @param label the Label of the PolicyEntry.
     * @param subject the retrieved Subject.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveSubjectResponse of(final String policyId,
            final Label label,
            final Subject subject,
            final DittoHeaders dittoHeaders) {

        return new RetrieveSubjectResponse(policyId, label, subject.getId().toString(),
                checkNotNull(subject, "Subject").toJson(
                        dittoHeaders.getSchemaVersion().orElse(subject.getLatestSchemaVersion())), HttpStatusCode.OK,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveSubject} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveSubjectResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveSubject} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveSubjectResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<RetrieveSubjectResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String policyId =
                            jsonObject.getValueOrThrow(PolicyQueryCommandResponse.JsonFields.JSON_POLICY_ID);
                    final Label label = PoliciesModelFactory.newLabel(jsonObject.getValueOrThrow(JSON_LABEL));
                    final String extractedSubjectId = jsonObject.getValueOrThrow(JSON_SUBJECT_ID);
                    final JsonObject extractedSubject = jsonObject.getValueOrThrow(JSON_SUBJECT);

                    return of(policyId, label, extractedSubjectId, extractedSubject, dittoHeaders);
                });
    }

    @Override
    public String getId() {
        return policyId;
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose {@code Subject} was retrieved.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the retrieved Subject.
     *
     * @return the retrieved Subject.
     */
    public Subject getSubject() {
        return PoliciesModelFactory.newSubject(subjectId, subject);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return subject;
    }

    @Override
    public RetrieveSubjectResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, label, subjectId, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveSubjectResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, getSubject(), dittoHeaders);
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
        jsonObjectBuilder.set(PolicyQueryCommandResponse.JsonFields.JSON_POLICY_ID, policyId, predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ID, subjectId, predicate);
        jsonObjectBuilder.set(JSON_SUBJECT, subject, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveSubjectResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveSubjectResponse that = (RetrieveSubjectResponse) o;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId)
                && Objects.equals(label, that.label) && Objects.equals(subjectId, that.subjectId)
                && Objects.equals(subject, that.subject) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, subjectId, subject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId
                + ", label=" + label + ", subjectId=" + subjectId + ", subject=" + subject + "]";
    }

}
