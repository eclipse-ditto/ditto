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
import org.eclipse.ditto.policies.model.EntryAddition;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link ModifyPolicyImportEntryAddition} command.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyPolicyImportEntryAdditionResponse.TYPE)
public final class ModifyPolicyImportEntryAdditionResponse
        extends AbstractCommandResponse<ModifyPolicyImportEntryAdditionResponse>
        implements PolicyModifyCommandResponse<ModifyPolicyImportEntryAdditionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyPolicyImportEntryAddition.NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFieldDefinition.ofString("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_ENTRY_ADDITION =
            JsonFieldDefinition.ofJsonValue("entryAddition", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyPolicyImportEntryAdditionResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));

                        return newInstance(
                                PolicyId.of(jsonObject.getValueOrThrow(
                                        PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                PolicyId.of(jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID)),
                                label,
                                jsonObject.getValue(JSON_ENTRY_ADDITION)
                                        .map(JsonValue::asObject)
                                        .map(obj -> PoliciesModelFactory.newEntryAddition(label, obj))
                                        .orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final PolicyId importedPolicyId;
    private final Label label;
    @Nullable private final EntryAddition entryAddition;

    private ModifyPolicyImportEntryAdditionResponse(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final Label label,
            @Nullable final EntryAddition entryAddition,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.importedPolicyId = checkNotNull(importedPolicyId, "importedPolicyId");
        this.label = checkNotNull(label, "label");
        this.entryAddition = ConditionChecker.checkArgument(
                entryAddition,
                entryAdditionArgument -> {
                    final boolean result;
                    if (HttpStatus.NO_CONTENT.equals(httpStatus)) {
                        result = entryAdditionArgument == null;
                    } else {
                        result = entryAdditionArgument != null;
                    }
                    return result;
                },
                () -> MessageFormat.format("EntryAddition <{0}> is illegal in conjunction with <{1}>.",
                        entryAddition,
                        httpStatus));
    }

    /**
     * Creates a response to a {@code ModifyPolicyImportEntryAddition} command for the case when an EntryAddition
     * was created.
     *
     * @param policyId the Policy ID of the created entry addition.
     * @param importedPolicyId the ID of the imported Policy.
     * @param entryAdditionCreated the EntryAddition created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyImportEntryAdditionResponse created(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final EntryAddition entryAdditionCreated,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId,
                importedPolicyId,
                checkNotNull(entryAdditionCreated, "entryAdditionCreated").getLabel(),
                entryAdditionCreated,
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyImportEntryAddition} command for the case when an existing
     * EntryAddition was modified.
     *
     * @param policyId the Policy ID of the modified entry addition.
     * @param importedPolicyId the ID of the imported Policy.
     * @param label the Label of the modified EntryAddition.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyImportEntryAdditionResponse modified(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final Label label,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId, importedPolicyId, label, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyPolicyImportEntryAdditionResponse} for the specified arguments.
     *
     * @param policyId the Policy ID of the modified entry addition.
     * @param importedPolicyId the ID of the imported Policy.
     * @param label the Label of the EntryAddition.
     * @param entryAddition (optional) the EntryAddition created.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyPolicyImportEntryAdditionResponse} instance.
     * @throws NullPointerException if any argument but {@code entryAddition} is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a
     * {@code ModifyPolicyImportEntryAdditionResponse}.
     */
    public static ModifyPolicyImportEntryAdditionResponse newInstance(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final Label label,
            @Nullable final EntryAddition entryAddition,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyImportEntryAdditionResponse(policyId,
                importedPolicyId,
                label,
                entryAddition,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyPolicyImportEntryAdditionResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyImportEntryAddition} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyPolicyImportEntryAdditionResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyImportEntryAddition} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyPolicyImportEntryAdditionResponse fromJson(final JsonObject jsonObject,
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
     * Returns the {@code Label} of the {@code EntryAddition} which was modified.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the created {@code EntryAddition}.
     *
     * @return the created EntryAddition.
     */
    public Optional<EntryAddition> getEntryAdditionCreated() {
        return Optional.ofNullable(entryAddition);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(entryAddition)
                .map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public ModifyPolicyImportEntryAdditionResponse setEntity(final JsonValue entity) {
        return newInstance(policyId, importedPolicyId, label,
                getHttpStatus() == HttpStatus.CREATED ?
                        PoliciesModelFactory.newEntryAddition(label, entity.asObject()) : null,
                getHttpStatus(), getDittoHeaders());
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
        if (null != entryAddition) {
            jsonObjectBuilder.set(JSON_ENTRY_ADDITION, entryAddition.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public ModifyPolicyImportEntryAdditionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(policyId, importedPolicyId, label, entryAddition, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyImportEntryAdditionResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyPolicyImportEntryAdditionResponse that = (ModifyPolicyImportEntryAdditionResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(importedPolicyId, that.importedPolicyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(entryAddition, that.entryAddition) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, importedPolicyId, label, entryAddition);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId +
                ", importedPolicyId=" + importedPolicyId + ", label=" + label +
                ", entryAddition=" + entryAddition + "]";
    }

}
