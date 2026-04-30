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

import java.util.Collections;
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
import org.eclipse.ditto.policies.model.AllowedAddition;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link RetrievePolicyEntryAllowedAdditions} command.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrievePolicyEntryAllowedAdditionsResponse.TYPE)
public final class RetrievePolicyEntryAllowedAdditionsResponse
        extends AbstractCommandResponse<RetrievePolicyEntryAllowedAdditionsResponse> implements
        PolicyQueryCommandResponse<RetrievePolicyEntryAllowedAdditionsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrievePolicyEntryAllowedAdditions.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_ALLOWED_ADDITIONS =
            JsonFieldDefinition.ofJsonArray("allowedAdditions", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrievePolicyEntryAllowedAdditionsResponse>
            JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        final JsonArray rawArray = jsonObject.getValueOrThrow(JSON_ALLOWED_ADDITIONS);
                        // Validate strictness now (rejects non-string and unknown-name elements)
                        // — same rule the HTTP route uses, so wire-format acceptance is symmetric
                        // across transports.
                        PoliciesModelFactory.parseAllowedAdditions(rawArray);
                        return new RetrievePolicyEntryAllowedAdditionsResponse(
                                PolicyId.of(jsonObject.getValueOrThrow(
                                        PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                Label.of(jsonObject.getValueOrThrow(JSON_LABEL)),
                                rawArray,
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final Label label;
    private final JsonArray allowedAdditions;

    private RetrievePolicyEntryAllowedAdditionsResponse(final PolicyId policyId,
            final Label label,
            final JsonArray allowedAdditions,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrievePolicyEntryAllowedAdditionsResponse.class),
                dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.allowedAdditions = checkNotNull(allowedAdditions, "allowedAdditions");
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntryAllowedAdditions} command.
     *
     * @param policyId the Policy ID of the retrieved allowed import additions.
     * @param label the Label of the PolicyEntry.
     * @param allowedAdditions the retrieved AllowedAdditions.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyEntryAllowedAdditionsResponse of(final PolicyId policyId,
            final Label label,
            final Set<AllowedAddition> allowedAdditions,
            final DittoHeaders dittoHeaders) {

        checkNotNull(allowedAdditions, "allowedAdditions");
        final JsonArray allowedAdditionsJsonArray = allowedAdditions.stream()
                .map(a -> JsonValue.of(a.getName()))
                .collect(JsonCollectors.valuesToArray());
        return of(policyId, label, allowedAdditionsJsonArray, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntryAllowedAdditions} command.
     *
     * @param policyId the Policy ID of the retrieved allowed import additions.
     * @param label the Label of the PolicyEntry.
     * @param allowedAdditions the retrieved AllowedAdditions as JsonArray.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyEntryAllowedAdditionsResponse of(final PolicyId policyId,
            final Label label,
            final JsonArray allowedAdditions,
            final DittoHeaders dittoHeaders) {

        return new RetrievePolicyEntryAllowedAdditionsResponse(policyId, label, allowedAdditions,
                HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntryAllowedAdditions} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrievePolicyEntryAllowedAdditionsResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntryAllowedAdditions} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrievePolicyEntryAllowedAdditionsResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose {@code AllowedAdditions} were retrieved.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the retrieved AllowedAdditions.
     *
     * @return the retrieved AllowedAdditions.
     */
    public Set<AllowedAddition> getAllowedAdditions() {
        return PoliciesModelFactory.parseAllowedAdditions(allowedAdditions);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return allowedAdditions;
    }

    @Override
    public RetrievePolicyEntryAllowedAdditionsResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, label, entity.asArray(), getDittoHeaders());
    }

    @Override
    public RetrievePolicyEntryAllowedAdditionsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, allowedAdditions, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/allowedAdditions";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_ALLOWED_ADDITIONS, allowedAdditions, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrievePolicyEntryAllowedAdditionsResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrievePolicyEntryAllowedAdditionsResponse that =
                (RetrievePolicyEntryAllowedAdditionsResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(allowedAdditions, that.allowedAdditions) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, allowedAdditions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label +
                ", allowedAdditions=" + allowedAdditions +
                "]";
    }

}
