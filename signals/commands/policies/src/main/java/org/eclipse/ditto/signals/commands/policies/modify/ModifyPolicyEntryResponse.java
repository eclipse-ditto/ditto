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
package org.eclipse.ditto.signals.commands.policies.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
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
 * Response to a {@link ModifyPolicyEntry} command.
 */
@Immutable
public final class ModifyPolicyEntryResponse extends AbstractCommandResponse<ModifyPolicyEntryResponse>
        implements PolicyModifyCommandResponse<ModifyPolicyEntryResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyPolicyEntry.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_POLICY_ENTRY =
            JsonFactory.newJsonValueFieldDefinition("policyEntry", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String policyId;
    @Nullable private final PolicyEntry policyEntryCreated;

    private ModifyPolicyEntryResponse(final String policyId,
            final HttpStatusCode statusCode,
            @Nullable final PolicyEntry policyEntryCreated,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "Policy ID");
        this.policyEntryCreated = policyEntryCreated;
    }

    /**
     * Creates a response to a {@code ModifyPolicyEntry} command.
     *
     * @param policyId the Policy ID of the created policy entry.
     * @param policyEntryCreated (optional) the PolicyEntry created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code statusCode} or {@code dittoHeaders} is {@code null}.
     */
    public static ModifyPolicyEntryResponse created(final String policyId, final PolicyEntry policyEntryCreated,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyEntryResponse(policyId, HttpStatusCode.CREATED, policyEntryCreated, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyEntry} command.
     *
     * @param policyId the Policy ID of the modified policy entry.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyEntryResponse modified(final String policyId, final DittoHeaders dittoHeaders) {
        return new ModifyPolicyEntryResponse(policyId, HttpStatusCode.NO_CONTENT, null, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyEntry} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected format.
     */
    public static ModifyPolicyEntryResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyEntry} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static ModifyPolicyEntryResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ModifyPolicyEntryResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String policyId =
                            jsonObject.getValueOrThrow(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID);
                    final Optional<String> readLabel = jsonObject.getValue(JSON_LABEL);

                    final PolicyEntry extractedPolicyEntryCreated = jsonObject.getValue(JSON_POLICY_ENTRY)
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .map(obj -> readLabel.map(s -> PoliciesModelFactory.newPolicyEntry(s, obj)).orElse(null))
                            .orElse(null);

                    return new ModifyPolicyEntryResponse(policyId, statusCode, extractedPolicyEntryCreated,
                            dittoHeaders);
                });
    }

    @Override
    public String getId() {
        return policyId;
    }

    /**
     * Returns the created PolicyEntry.
     *
     * @return the created PolicyEntry.
     */
    public Optional<PolicyEntry> getPolicyEntryCreated() {
        return Optional.ofNullable(policyEntryCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(policyEntryCreated).map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        if (policyEntryCreated == null) {
            return JsonPointer.empty();
        }

        final String path = "/entries/" + policyEntryCreated.getLabel();
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID, policyId, predicate);
        if (null != policyEntryCreated) {
            jsonObjectBuilder.set(JSON_LABEL, policyEntryCreated.getLabel().toString(), predicate);
            jsonObjectBuilder.set(JSON_POLICY_ENTRY, policyEntryCreated.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public ModifyPolicyEntryResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return (policyEntryCreated != null) ? created(policyId, policyEntryCreated, dittoHeaders) :
                modified(policyId, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyEntryResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyPolicyEntryResponse that = (ModifyPolicyEntryResponse) o;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId)
                && Objects.equals(policyEntryCreated, that.policyEntryCreated) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, policyEntryCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId +
                ", policyEntryCreated=" + policyEntryCreated + "]";
    }

}
