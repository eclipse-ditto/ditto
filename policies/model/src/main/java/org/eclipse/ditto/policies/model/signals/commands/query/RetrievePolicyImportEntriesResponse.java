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
package org.eclipse.ditto.policies.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.ImportedLabels;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link RetrievePolicyImportEntries} command.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrievePolicyImportEntriesResponse.TYPE)
public final class RetrievePolicyImportEntriesResponse
        extends AbstractCommandResponse<RetrievePolicyImportEntriesResponse>
        implements PolicyQueryCommandResponse<RetrievePolicyImportEntriesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrievePolicyImportEntries.NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_ENTRIES =
            JsonFactory.newJsonArrayFieldDefinition("entries", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrievePolicyImportEntriesResponse>
            JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new RetrievePolicyImportEntriesResponse(
                                PolicyId.of(jsonObject.getValueOrThrow(
                                        PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                PolicyId.of(jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID)),
                                jsonObject.getValueOrThrow(JSON_ENTRIES),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final PolicyId importedPolicyId;
    private final JsonArray entries;

    private RetrievePolicyImportEntriesResponse(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final JsonArray entries,
            final HttpStatus statusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.importedPolicyId = checkNotNull(importedPolicyId, "importedPolicyId");
        this.entries = checkNotNull(entries, "entries");
    }

    /**
     * Creates a response to a {@code RetrievePolicyImportEntries} command.
     *
     * @param policyId the Policy ID of the retrieved entries.
     * @param importedPolicyId the imported Policy ID.
     * @param importedLabels the retrieved imported labels.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyImportEntriesResponse of(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final ImportedLabels importedLabels,
            final DittoHeaders dittoHeaders) {

        return new RetrievePolicyImportEntriesResponse(policyId, importedPolicyId,
                checkNotNull(importedLabels, "importedLabels").toJson(),
                HTTP_STATUS,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyImportEntries} command.
     *
     * @param policyId the Policy ID of the retrieved entries.
     * @param importedPolicyId the imported Policy ID.
     * @param entries the retrieved entries as JSON array.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyImportEntriesResponse of(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final JsonArray entries,
            final DittoHeaders dittoHeaders) {

        return new RetrievePolicyImportEntriesResponse(policyId, importedPolicyId, entries,
                HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyImportEntries} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrievePolicyImportEntriesResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyImportEntries} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrievePolicyImportEntriesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * Returns the retrieved imported labels.
     *
     * @return the retrieved imported labels.
     */
    public ImportedLabels getImportedLabels() {
        return PoliciesModelFactory.newImportedEntries(entries);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return entries;
    }

    @Override
    public RetrievePolicyImportEntriesResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, importedPolicyId, entity.asArray(), getDittoHeaders());
    }

    @Override
    public RetrievePolicyImportEntriesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, importedPolicyId, entries, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/imports/" + importedPolicyId + "/entries";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, importedPolicyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_ENTRIES, entries, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrievePolicyImportEntriesResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrievePolicyImportEntriesResponse that =
                (RetrievePolicyImportEntriesResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(importedPolicyId, that.importedPolicyId) &&
                Objects.equals(entries, that.entries) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, importedPolicyId, entries);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", importedPolicyId=" + importedPolicyId +
                ", entries=" + entries +
                "]";
    }

}
