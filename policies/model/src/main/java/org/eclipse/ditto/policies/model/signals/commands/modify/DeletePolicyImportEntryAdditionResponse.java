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
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link DeletePolicyImportEntryAddition} command.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommandResponse(type = DeletePolicyImportEntryAdditionResponse.TYPE)
public final class DeletePolicyImportEntryAdditionResponse
        extends AbstractCommandResponse<DeletePolicyImportEntryAdditionResponse>
        implements PolicyModifyCommandResponse<DeletePolicyImportEntryAdditionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + DeletePolicyImportEntryAddition.NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFieldDefinition.ofString("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.NO_CONTENT;

    private static final CommandResponseJsonDeserializer<DeletePolicyImportEntryAdditionResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                PolicyId.of(jsonObject.getValueOrThrow(
                                        PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                PolicyId.of(jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID)),
                                Label.of(jsonObject.getValueOrThrow(JSON_LABEL)),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final PolicyId importedPolicyId;
    private final Label label;

    private DeletePolicyImportEntryAdditionResponse(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final Label label,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.importedPolicyId = checkNotNull(importedPolicyId, "importedPolicyId");
        this.label = checkNotNull(label, "label");
    }

    /**
     * Creates a response to a {@code DeletePolicyImportEntryAddition} command.
     *
     * @param policyId the Policy ID of the deleted entry addition.
     * @param importedPolicyId the ID of the imported Policy.
     * @param label the Label of the deleted EntryAddition.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DeletePolicyImportEntryAdditionResponse of(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final Label label,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId, importedPolicyId, label, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code DeletePolicyImportEntryAdditionResponse} for the specified arguments.
     *
     * @param policyId the Policy ID of the deleted entry addition.
     * @param importedPolicyId the ID of the imported Policy.
     * @param label the Label of the deleted EntryAddition.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code DeletePolicyImportEntryAdditionResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a
     * {@code DeletePolicyImportEntryAdditionResponse}.
     */
    public static DeletePolicyImportEntryAdditionResponse newInstance(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final Label label,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new DeletePolicyImportEntryAdditionResponse(policyId,
                importedPolicyId,
                label,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        DeletePolicyImportEntryAdditionResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code DeletePolicyImportEntryAddition} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static DeletePolicyImportEntryAdditionResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code DeletePolicyImportEntryAddition} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static DeletePolicyImportEntryAdditionResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the ID of the imported {@code Policy}.
     *
     * @return the ID of the imported Policy.
     */
    public PolicyId getImportedPolicyId() {
        return importedPolicyId;
    }

    /**
     * Returns the {@code Label} of the deleted {@code EntryAddition}.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/imports/" + importedPolicyId + "/entriesAdditions/" + label);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, importedPolicyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
    }

    @Override
    public DeletePolicyImportEntryAdditionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(policyId, importedPolicyId, label, getHttpStatus(), dittoHeaders);
    }

    @Override
    public DeletePolicyImportEntryAdditionResponse setEntity(final JsonValue entity) {
        return this;
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DeletePolicyImportEntryAdditionResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeletePolicyImportEntryAdditionResponse that = (DeletePolicyImportEntryAdditionResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(importedPolicyId, that.importedPolicyId) &&
                Objects.equals(label, that.label) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, importedPolicyId, label);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId +
                ", importedPolicyId=" + importedPolicyId + ", label=" + label + "]";
    }

}
