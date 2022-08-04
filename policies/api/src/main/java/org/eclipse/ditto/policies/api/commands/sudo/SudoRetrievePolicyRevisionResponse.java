/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.api.commands.sudo;

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
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Response to a {@link SudoRetrievePolicyRevisionResponse} command.
 */
@Immutable
@JsonParsableCommandResponse(type = SudoRetrievePolicyRevisionResponse.TYPE)
public final class SudoRetrievePolicyRevisionResponse
        extends AbstractCommandResponse<SudoRetrievePolicyRevisionResponse>
        implements PolicySudoQueryCommandResponse<SudoRetrievePolicyRevisionResponse>,
        SignalWithEntityId<SudoRetrievePolicyRevisionResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + SudoRetrievePolicyRevision.NAME;

    static final JsonFieldDefinition<Long> JSON_REVISION =
            JsonFieldDefinition.ofLong("payload/revision", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<SudoRetrievePolicyRevisionResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final var jsonObject = context.getJsonObject();
                        return new SudoRetrievePolicyRevisionResponse(
                                PolicyId.of(jsonObject.getValueOrThrow(PolicySudoQueryCommandResponse.JsonFields.JSON_POLICY_ID)),
                                jsonObject.getValueOrThrow(JSON_REVISION),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final long revision;

    private SudoRetrievePolicyRevisionResponse(final PolicyId policyId,
            final long revision,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        SudoRetrievePolicyRevisionResponse.class),
                dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.revision = revision;
    }

    /**
     * Creates a response to a {@code SudoRetrievePolicyRevision} command.
     *
     * @param policyId the policy ID.
     * @param revision the policy revision.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     */
    public static SudoRetrievePolicyRevisionResponse of(final PolicyId policyId,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return new SudoRetrievePolicyRevisionResponse(policyId, revision, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@code SudoRetrievePolicyResponse} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static SudoRetrievePolicyRevisionResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code SudoRetrievePolicyResponse} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SudoRetrievePolicyRevisionResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the retrieved Policy.
     *
     * @return the retrieved Policy.
     */
    public long getRevision() {
        return revision;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return JsonValue.of(revision);
    }

    @Override
    public SudoRetrievePolicyRevisionResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, entity.asLong(), getDittoHeaders());
    }

    @Override
    public SudoRetrievePolicyRevisionResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, revision, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final var predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicySudoQueryCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_REVISION, revision, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoRetrievePolicyRevisionResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (SudoRetrievePolicyRevisionResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                revision == that.revision &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, revision);
    }

    @Override
    public String toString() {
        return super.toString() + "policyId=" + policyId + "revision=" + revision + "]";
    }

}
