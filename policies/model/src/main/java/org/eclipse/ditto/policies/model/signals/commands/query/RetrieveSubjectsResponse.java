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
package org.eclipse.ditto.policies.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
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
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link RetrieveSubjects} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveSubjectsResponse.TYPE)
public final class RetrieveSubjectsResponse extends AbstractCommandResponse<RetrieveSubjectsResponse> implements
        PolicyQueryCommandResponse<RetrieveSubjectsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveSubjects.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_SUBJECTS =
            JsonFieldDefinition.ofJsonObject("subjects", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveSubjectsResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new RetrieveSubjectsResponse(
                                PolicyId.of(jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                Label.of(jsonObject.getValueOrThrow(JSON_LABEL)),
                                jsonObject.getValueOrThrow(JSON_SUBJECTS),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final Label label;
    private final JsonObject subjects;

    private RetrieveSubjectsResponse(final PolicyId policyId,
            final Label label,
            final JsonObject subjects,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveSubjectsResponse.class),
                dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.subjects = checkNotNull(subjects, "subjects");
    }

    /**
     * Creates a response to a {@code RetrieveSubjects} command.
     *
     * @param policyId the Policy ID of the retrieved subjects.
     * @param label the Label of the PolicyEntry.
     * @param subjects the retrieved Subjects.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveSubjectsResponse of(final PolicyId policyId,
            final Label label,
            final Subjects subjects,
            final DittoHeaders dittoHeaders) {

        checkNotNull(subjects, "subjects");
        return of(policyId,
                label,
                subjects.toJson(dittoHeaders.getSchemaVersion().orElse(subjects.getLatestSchemaVersion())),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveSubjects} command.
     *
     * @param policyId the Policy ID of the retrieved subjects.
     * @param label the Label of the PolicyEntry.
     * @param subjects the retrieved Subjects.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveSubjectsResponse of(final PolicyId policyId,
            final Label label,
            final JsonObject subjects,
            final DittoHeaders dittoHeaders) {

        return new RetrieveSubjectsResponse(policyId, label, subjects, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveSubjects} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveSubjectsResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveSubjects} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveSubjectsResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose {@code Subjects} were retrieved.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the retrieved Subjects.
     *
     * @return the retrieved Subjects.
     */
    public Subjects getSubjects() {
        return PoliciesModelFactory.newSubjects(subjects);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return subjects;
    }

    @Override
    public RetrieveSubjectsResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, label, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveSubjectsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, subjects, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/subjects";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECTS, subjects, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveSubjectsResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveSubjectsResponse that = (RetrieveSubjectsResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(subjects, that.subjects) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, subjects);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label +
                ", subjects=" + subjects +
                "]";
    }

}
