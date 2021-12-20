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
package org.eclipse.ditto.policies.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
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
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

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
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_SUBJECT_ID =
            JsonFieldDefinition.ofString("subjectId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_SUBJECT =
            JsonFieldDefinition.ofJsonValue("subject", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifySubjectResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final SubjectId subjectId =
                                SubjectId.newInstance(jsonObject.getValueOrThrow(JSON_SUBJECT_ID));

                        return newInstance(
                                PolicyId.of(jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                Label.of(jsonObject.getValueOrThrow(JSON_LABEL)),
                                subjectId,
                                jsonObject.getValue(JSON_SUBJECT)
                                        .map(JsonValue::asObject)
                                        .map(obj -> PoliciesModelFactory.newSubject(subjectId, obj))
                                        .orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final Label label;
    private final SubjectId subjectId;
    @Nullable private final Subject subject;

    private ModifySubjectResponse(final PolicyId policyId,
            final Label label,
            final SubjectId subjectId,
            @Nullable final Subject subject,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.subjectId = checkNotNull(subjectId, "subjectId");
        this.subject = ConditionChecker.checkArgument(
                subject,
                subjectArgument -> {
                    final boolean result;
                    if (HttpStatus.NO_CONTENT.equals(httpStatus)) {
                        result = subjectArgument == null;
                    } else {
                        result = subjectArgument != null;
                    }
                    return result;
                },
                () -> MessageFormat.format("Subject <{0}> is illegal in conjunction with <{1}>.",
                        subject,
                        httpStatus));
    }

    /**
     * Creates a response to a {@code ModifySubject} command.
     *
     * @param policyId the Policy ID of the created subject.
     * @param label the Label of the PolicyEntry.
     * @param subjectCreated (optional) the Subject created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifySubjectResponse created(final PolicyId policyId,
            final Label label,
            final Subject subjectCreated,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId,
                label,
                checkNotNull(subjectCreated, "subjectCreated").getId(),
                subjectCreated,
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifySubject} command.
     *
     * @param policyId the Policy ID of the modified subject.
     * @param label the Label of the PolicyEntry.
     * @param subjectId the subject id of the modified subject
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument but {@code subjectId} is {@code null}.
     * @since 1.1.0
     */
    public static ModifySubjectResponse modified(final PolicyId policyId,
            final Label label,
            final SubjectId subjectId,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId, label, subjectId, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifySubjectResponse} for the specified arguments.
     *
     * @param policyId the Policy ID of the modified subject.
     * @param label the Label of the PolicyEntry.
     * @param subjectId the subject id of the modified subject.
     * @param subject (optional) the Subject created.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifySubjectResponse} instance.
     * @throws NullPointerException if any argument but {@code attributeValue} is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code ModifySubjectResponse}.
     * @since 2.3.0
     */
    public static ModifySubjectResponse newInstance(final PolicyId policyId,
            final Label label,
            final SubjectId subjectId,
            @Nullable final Subject subject,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifySubjectResponse(policyId,
                label,
                subjectId,
                subject,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifySubjectResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifySubject} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifySubjectResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifySubject} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifySubjectResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
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
    public Optional<Subject> getSubjectCreated() {
        return Optional.ofNullable(subject);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(subject).map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/entries/" + label + "/subjects/" + subjectId);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ID, subjectId.toString(), predicate);
        if (null != subject) {
            jsonObjectBuilder.set(JSON_SUBJECT, subject.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public ModifySubjectResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(policyId, label, subjectId, subject, getHttpStatus(), dittoHeaders);
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
                Objects.equals(subject, that.subject) &&
                Objects.equals(subjectId, that.subjectId) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, subject, subjectId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label +
                ", subject=" + subject + ", subjectId=" + subjectId + "]";
    }

}
