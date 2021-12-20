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
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link ModifySubjects} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifySubjectsResponse.TYPE)
public final class ModifySubjectsResponse extends AbstractCommandResponse<ModifySubjectsResponse>
        implements PolicyModifyCommandResponse<ModifySubjectsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifySubjects.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.NO_CONTENT;

    private static final CommandResponseJsonDeserializer<ModifySubjectsResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                PolicyId.of(jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                Label.of(jsonObject.getValueOrThrow(JSON_LABEL)),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final Label label;

    private ModifySubjectsResponse(final PolicyId policyId,
            final Label label,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
    }

    /**
     * Creates a response to a {@code ModifySubjects} command.
     *
     * @param policyId the Policy ID of the modified subjects.
     * @param label the Label of the PolicyEntry.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifySubjectsResponse of(final PolicyId policyId,
            final Label label,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId, label, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifySubjectsResponse} for the specified arguments.
     *
     * @param policyId the Policy ID of the modified subjects.
     * @param label the Label of the PolicyEntry.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifySubjectsResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code ModifySubjectsResponse}.
     * @since 2.3.0
     */
    public static ModifySubjectsResponse newInstance(final PolicyId policyId,
            final Label label,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifySubjectsResponse(policyId,
                label,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singletonList(HTTP_STATUS),
                        ModifySubjectsResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifySubjects} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifySubjectsResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifySubjects} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifySubjectsResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose {@code Subjects} were modified.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/entries/" + label + "/subjects");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
    }

    @Override
    public ModifySubjectsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(policyId, label, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifySubjectsResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ModifySubjectsResponse that = (ModifySubjectsResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label +
                "]";
    }

}
