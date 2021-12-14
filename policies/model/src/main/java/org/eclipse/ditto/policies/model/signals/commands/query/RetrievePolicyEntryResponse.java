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
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link RetrievePolicyEntry} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrievePolicyEntryResponse.TYPE)
public final class RetrievePolicyEntryResponse extends AbstractCommandResponse<RetrievePolicyEntryResponse>
        implements PolicyQueryCommandResponse<RetrievePolicyEntryResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrievePolicyEntry.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_POLICY_ENTRY =
            JsonFieldDefinition.ofJsonObject("policyEntry", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrievePolicyEntryResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new RetrievePolicyEntryResponse(
                                PolicyId.of(jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                jsonObject.getValueOrThrow(JSON_LABEL),
                                jsonObject.getValueOrThrow(JSON_POLICY_ENTRY),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final String policyEntryLabel;
    private final JsonObject policyEntry;

    private RetrievePolicyEntryResponse(final PolicyId policyId,
            final String policyEntryLabel,
            final JsonObject policyEntry,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrievePolicyEntryResponse.class),
                dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.policyEntryLabel = checkNotNull(policyEntryLabel, "policyEntryLabel");
        this.policyEntry = checkNotNull(policyEntry, "policyEntry");
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
    public static RetrievePolicyEntryResponse of(final PolicyId policyId,
            final PolicyEntry policyEntry,
            final DittoHeaders dittoHeaders) {

        checkNotNull(policyEntry, "policyEntry");
        checkNotNull(dittoHeaders, "dittoHeaders");

        return of(policyId,
                String.valueOf(policyEntry.getLabel()),
                policyEntry.toJson(dittoHeaders.getSchemaVersion().orElse(policyEntry.getLatestSchemaVersion())),
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
    public static RetrievePolicyEntryResponse of(final PolicyId policyId,
            final String policyEntryLabel,
            final JsonObject policyEntry,
            final DittoHeaders dittoHeaders) {

        return new RetrievePolicyEntryResponse(policyId, policyEntryLabel, policyEntry, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntry} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrievePolicyEntryResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntry} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrievePolicyEntryResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
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
        return JsonPointer.of("/entries/" + policyEntryLabel);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
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
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(policyEntryLabel, that.policyEntryLabel) &&
                Objects.equals(policyEntry, that.policyEntry) &&
                super.equals(o);
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
