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
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link RetrievePolicyEntry} command.
 */
@Immutable
public final class RetrievePolicyEntryResponse extends AbstractCommandResponse<RetrievePolicyEntryResponse> implements
        PolicyQueryCommandResponse<RetrievePolicyEntryResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrievePolicyEntry.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_POLICY_ENTRY =
            JsonFactory.newJsonObjectFieldDefinition("policyEntry", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String policyId;
    private final String policyEntryLabel;
    private final JsonObject policyEntry;

    private RetrievePolicyEntryResponse(final String policyId,
            final HttpStatusCode statusCode,
            final String policyEntryLabel,
            final JsonObject policyEntry,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "Policy ID");
        this.policyEntryLabel = checkNotNull(policyEntryLabel, "Policy entry label");
        this.policyEntry = checkNotNull(policyEntry, "Policy entry");
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntry} command.
     *
     * @param policyId the Policy ID of the retrieved policy entry.
     * @param policyEntry the retrieved Policy entry.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyEntryResponse of(final String policyId, final PolicyEntry policyEntry,
            final DittoHeaders dittoHeaders) {

        return new RetrievePolicyEntryResponse(policyId, HttpStatusCode.OK, policyEntry.getLabel().toString(),
                checkNotNull(policyEntry, "Policy Entry")
                        .toJson(dittoHeaders.getSchemaVersion().orElse(policyEntry.getLatestSchemaVersion())),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntry} command.
     *
     * @param policyId the Policy ID of the retrieved policy entry.
     * @param policyEntryLabel the Label for the PolicyEntry to create.
     * @param policyEntry the retrieved Policy entry.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyEntryResponse of(final String policyId,
            final String policyEntryLabel,
            final JsonObject policyEntry,
            final DittoHeaders dittoHeaders) {

        return new RetrievePolicyEntryResponse(policyId, HttpStatusCode.OK, policyEntryLabel, policyEntry,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntry} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrievePolicyEntryResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntry} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrievePolicyEntryResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandResponseJsonDeserializer<RetrievePolicyEntryResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String policyId =
                            jsonObject.getValueOrThrow(PolicyQueryCommandResponse.JsonFields.JSON_POLICY_ID);
                    final String extractedLabel = jsonObject.getValueOrThrow(JSON_LABEL);
                    final JsonObject extractedPolicyEntry = jsonObject.getValueOrThrow(JSON_POLICY_ENTRY);

                    return of(policyId, extractedLabel, extractedPolicyEntry, dittoHeaders);
                });
    }

    @Override
    public String getId() {
        return policyId;
    }

    /**
     * Returns the retrieved Policy entry.
     *
     * @return the retrieved Policy entry.
     */
    public PolicyEntry getPolicyEntry() {
        return PoliciesModelFactory.newPolicyEntry(policyEntryLabel, policyEntry);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return policyEntry;
    }

    @Override
    public RetrievePolicyEntryResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, policyEntryLabel, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrievePolicyEntryResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, policyEntryLabel, policyEntry, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + policyEntryLabel;
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyQueryCommandResponse.JsonFields.JSON_POLICY_ID, policyId, predicate);
        jsonObjectBuilder.set(JSON_LABEL, policyEntryLabel, predicate);
        jsonObjectBuilder.set(JSON_POLICY_ENTRY, policyEntry, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrievePolicyEntryResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrievePolicyEntryResponse that = (RetrievePolicyEntryResponse) o;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId)
                && Objects.equals(policyEntryLabel, that.policyEntryLabel)
                && Objects.equals(policyEntry, that.policyEntry) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, policyEntryLabel, policyEntry);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId
                + ", policyEntryLabel=" + policyEntryLabel + ", policyEntry=" + policyEntry + "]";
    }

}
