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
import org.eclipse.ditto.policies.model.SubjectAlias;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link ModifySubjectAlias} command.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommandResponse(type = ModifySubjectAliasResponse.TYPE)
public final class ModifySubjectAliasResponse
        extends AbstractCommandResponse<ModifySubjectAliasResponse>
        implements PolicyModifyCommandResponse<ModifySubjectAliasResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifySubjectAlias.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_SUBJECT_ALIAS =
            JsonFieldDefinition.ofJsonValue("subjectAlias", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifySubjectAliasResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));

                        return newInstance(
                                PolicyId.of(jsonObject.getValueOrThrow(
                                        PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                label,
                                jsonObject.getValue(JSON_SUBJECT_ALIAS)
                                        .map(JsonValue::asObject)
                                        .map(obj -> PoliciesModelFactory.newSubjectAlias(label, obj))
                                        .orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final Label label;
    @Nullable private final SubjectAlias subjectAlias;

    private ModifySubjectAliasResponse(final PolicyId policyId, final Label label,
            @Nullable final SubjectAlias subjectAlias, final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.subjectAlias = ConditionChecker.checkArgument(
                subjectAlias,
                subjectAliasArgument -> {
                    final boolean result;
                    if (HttpStatus.NO_CONTENT.equals(httpStatus)) {
                        result = subjectAliasArgument == null;
                    } else {
                        result = subjectAliasArgument != null;
                    }
                    return result;
                },
                () -> MessageFormat.format("SubjectAlias <{0}> is illegal in conjunction with <{1}>.",
                        subjectAlias,
                        httpStatus));
    }

    /**
     * Creates a response to a {@code ModifySubjectAlias} command for the case when a SubjectAlias was created.
     *
     * @param policyId the Policy ID of the created subject alias.
     * @param subjectAliasCreated the SubjectAlias created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifySubjectAliasResponse created(final PolicyId policyId,
            final SubjectAlias subjectAliasCreated,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId,
                checkNotNull(subjectAliasCreated, "subjectAliasCreated").getLabel(),
                subjectAliasCreated,
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifySubjectAlias} command for the case when an existing SubjectAlias
     * was modified.
     *
     * @param policyId the Policy ID of the modified subject alias.
     * @param label the Label of the modified SubjectAlias.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifySubjectAliasResponse modified(final PolicyId policyId, final Label label,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId, label, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifySubjectAliasResponse} for the specified arguments.
     *
     * @param policyId the Policy ID of the modified subject alias.
     * @param label the Label of the SubjectAlias.
     * @param subjectAlias (optional) the SubjectAlias created.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifySubjectAliasResponse} instance.
     * @throws NullPointerException if any argument but {@code subjectAlias} is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a
     * {@code ModifySubjectAliasResponse}.
     */
    public static ModifySubjectAliasResponse newInstance(final PolicyId policyId, final Label label,
            @Nullable final SubjectAlias subjectAlias, final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifySubjectAliasResponse(policyId,
                label,
                subjectAlias,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifySubjectAliasResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifySubjectAlias} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifySubjectAliasResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifySubjectAlias} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifySubjectAliasResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the {@code Label} of the {@code SubjectAlias} which was modified.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the created {@code SubjectAlias}.
     *
     * @return the created SubjectAlias.
     */
    public Optional<SubjectAlias> getSubjectAliasCreated() {
        return Optional.ofNullable(subjectAlias);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(subjectAlias)
                .map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public ModifySubjectAliasResponse setEntity(final JsonValue entity) {
        return newInstance(policyId, label,
                getHttpStatus() == HttpStatus.CREATED ?
                        PoliciesModelFactory.newSubjectAlias(label, entity.asObject()) : null,
                getHttpStatus(), getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/subjectAliases/" + label);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        if (null != subjectAlias) {
            jsonObjectBuilder.set(JSON_SUBJECT_ALIAS, subjectAlias.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public ModifySubjectAliasResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(policyId, label, subjectAlias, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifySubjectAliasResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifySubjectAliasResponse that = (ModifySubjectAliasResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(subjectAlias, that.subjectAlias) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, subjectAlias);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId +
                ", label=" + label + ", subjectAlias=" + subjectAlias + "]";
    }

}
