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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.EntryReference;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link ModifyPolicyEntryReferences} command.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyPolicyEntryReferencesResponse.TYPE)
public final class ModifyPolicyEntryReferencesResponse
        extends AbstractCommandResponse<ModifyPolicyEntryReferencesResponse>
        implements PolicyModifyCommandResponse<ModifyPolicyEntryReferencesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyPolicyEntryReferences.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_REFERENCES =
            JsonFieldDefinition.ofJsonValue("references", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyPolicyEntryReferencesResponse>
            JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        final List<EntryReference> references = jsonObject.getValue(JSON_REFERENCES)
                                .filter(JsonValue::isArray)
                                .map(JsonValue::asArray)
                                .map(PoliciesModelFactory::parseEntryReferences)
                                .orElse(null);
                        return newInstance(
                                PolicyId.of(jsonObject.getValueOrThrow(
                                        PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                Label.of(jsonObject.getValueOrThrow(JSON_LABEL)),
                                references,
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final Label label;
    @Nullable private final List<EntryReference> referencesCreated;

    private ModifyPolicyEntryReferencesResponse(final PolicyId policyId,
            final Label label,
            @Nullable final List<EntryReference> referencesCreated,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.referencesCreated = referencesCreated;
    }

    /**
     * Creates a response to a {@code ModifyPolicyEntryReferences} command where references were created (new).
     *
     * @param policyId the Policy ID.
     * @param label the Label of the PolicyEntry.
     * @param referencesCreated the references that were created.
     * @param dittoHeaders the headers.
     * @return the response with HTTP status 201 Created.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyEntryReferencesResponse created(final PolicyId policyId,
            final Label label,
            final List<EntryReference> referencesCreated,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId, label, checkNotNull(referencesCreated, "referencesCreated"),
                HttpStatus.CREATED, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyEntryReferences} command where references were modified (updated).
     *
     * @param policyId the Policy ID.
     * @param label the Label of the PolicyEntry.
     * @param dittoHeaders the headers.
     * @return the response with HTTP status 204 No Content.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyEntryReferencesResponse modified(final PolicyId policyId,
            final Label label,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId, label, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyEntryReferences} command.
     *
     * @param policyId the Policy ID of the modified references.
     * @param label the Label of the PolicyEntry.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @deprecated use {@link #created(PolicyId, Label, List, DittoHeaders)} or
     * {@link #modified(PolicyId, Label, DittoHeaders)} instead.
     */
    @Deprecated
    public static ModifyPolicyEntryReferencesResponse of(final PolicyId policyId,
            final Label label,
            final DittoHeaders dittoHeaders) {

        return modified(policyId, label, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyPolicyEntryReferencesResponse} for the specified arguments.
     *
     * @param policyId the Policy ID.
     * @param label the Label of the PolicyEntry.
     * @param referencesCreated the created references, or {@code null} for modifications.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers.
     * @return the response instance.
     * @throws NullPointerException if any required argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed.
     */
    public static ModifyPolicyEntryReferencesResponse newInstance(final PolicyId policyId,
            final Label label,
            @Nullable final List<EntryReference> referencesCreated,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyEntryReferencesResponse(policyId,
                label,
                referencesCreated,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyPolicyEntryReferencesResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response from a JSON string.
     *
     * @param jsonString the JSON string.
     * @param dittoHeaders the headers.
     * @return the response.
     */
    public static ModifyPolicyEntryReferencesResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers.
     * @return the response.
     */
    public static ModifyPolicyEntryReferencesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose {@code EntryReference}s were modified.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the created references, if this is a "created" response (201).
     *
     * @return an Optional of the created references.
     */
    public Optional<List<EntryReference>> getReferencesCreated() {
        return Optional.ofNullable(referencesCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        if (referencesCreated != null) {
            final JsonArray array = referencesCreated.stream()
                    .map(EntryReference::toJson)
                    .collect(JsonCollectors.valuesToArray());
            return Optional.of(array);
        }
        return Optional.empty();
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/entries/" + label + "/references");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        if (referencesCreated != null) {
            final JsonArray array = referencesCreated.stream()
                    .map(EntryReference::toJson)
                    .collect(JsonCollectors.valuesToArray());
            jsonObjectBuilder.set(JSON_REFERENCES, array, predicate);
        }
    }

    @Override
    public ModifyPolicyEntryReferencesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(policyId, label, referencesCreated, getHttpStatus(), dittoHeaders);
    }

    @Override
    public ModifyPolicyEntryReferencesResponse setEntity(final JsonValue entity) {
        return this;
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyEntryReferencesResponse;
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
        final ModifyPolicyEntryReferencesResponse that = (ModifyPolicyEntryReferencesResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(referencesCreated, that.referencesCreated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, referencesCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", label=" + label +
                ", referencesCreated=" + referencesCreated +
                "]";
    }

}
