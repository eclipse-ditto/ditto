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
package org.eclipse.ditto.things.model.signals.commands.modify;

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
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link ModifyPolicyId} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyPolicyIdResponse.TYPE)
public final class ModifyPolicyIdResponse extends AbstractCommandResponse<ModifyPolicyIdResponse>
        implements ThingModifyCommandResponse<ModifyPolicyIdResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyPolicyId.NAME;

    static final JsonFieldDefinition<String> JSON_POLICY_ID =
            JsonFieldDefinition.ofString("policyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyPolicyIdResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID)),
                                jsonObject.getValue(JSON_POLICY_ID)
                                        .map(PolicyId::of)
                                        .orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final ThingId thingId;
    @Nullable private final PolicyId policyId;

    private ModifyPolicyIdResponse(final ThingId thingId,
            @Nullable final PolicyId policyId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.policyId = policyId;
        if (HttpStatus.NO_CONTENT.equals(httpStatus) && null != policyId) {
            throw new IllegalArgumentException(
                    MessageFormat.format("Policy ID <{0}> is illegal in conjunction with <{1}>.",
                    policyId,
                    httpStatus)
            );
        }
    }

    /**
     * Returns a new {@code ModifyPolicyIdResponse} for a created Policy ID. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
     *
     * @param thingId the Thing ID of the created policy ID.
     * @param policyId the created Policy ID.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created Policy ID.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyIdResponse created(final ThingId thingId,
            final PolicyId policyId,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, checkNotNull(policyId, "policyId"), HttpStatus.CREATED, dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyPolicyIdResponse} for a modified Policy ID. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified policy ID.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified Policy ID.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyIdResponse modified(final ThingId thingId, final DittoHeaders dittoHeaders) {
        return newInstance(thingId, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyPolicyIdResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the attribute belongs to.
     * @param policyId the created policy ID or {@code null} if an existing policy ID was modified.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyPolicyIdResponse} instance.
     * @throws NullPointerException if any argument but {@code policyId} is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code ModifyPolicyIdResponse} or
     * if {@code httpStatus} conflicts with {@code policyId}.
     * @since 2.3.0
     */
    public static ModifyPolicyIdResponse newInstance(final ThingId thingId,
            @Nullable final PolicyId policyId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyIdResponse(thingId,
                policyId,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyPolicyIdResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyPolicyId} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyPolicyIdResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyPolicyId} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyPolicyIdResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * ModifyPolicyIdResponse is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions.
     */
    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the created Policy ID.
     *
     * @return the created Policy ID.
     */
    public Optional<PolicyId> getPolicyEntityId() {
        return Optional.ofNullable(policyId);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(policyId).map(JsonValue::of);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of(Thing.JsonFields.POLICY_ID.getPointer().toString());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        if (policyId != null) {
            jsonObjectBuilder.set(JSON_POLICY_ID, String.valueOf(policyId), predicate);
        }
    }

    @Override
    public ModifyPolicyIdResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, policyId, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyIdResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyPolicyIdResponse that = (ModifyPolicyIdResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(policyId, that.policyId) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, policyId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", policyId=" +
                policyId + "]";
    }

}
