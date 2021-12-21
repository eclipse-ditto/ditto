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
package org.eclipse.ditto.policies.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
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
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link CreatePolicy} command.
 */
@Immutable
@JsonParsableCommandResponse(type = CreatePolicyResponse.TYPE)
public final class CreatePolicyResponse extends AbstractCommandResponse<CreatePolicyResponse>
        implements PolicyModifyCommandResponse<CreatePolicyResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + CreatePolicy.NAME;

    static final JsonFieldDefinition<JsonValue> JSON_POLICY =
            JsonFieldDefinition.ofJsonValue("policy", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.CREATED;

    private static final CommandResponseJsonDeserializer<CreatePolicyResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return newInstance(
                                PolicyId.of(jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                jsonObject.getValue(JSON_POLICY)
                                        .map(JsonValue::asObject)
                                        .map(PoliciesModelFactory::newPolicy)
                                        .orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    @Nullable private final Policy policyCreated;

    private CreatePolicyResponse(final PolicyId policyId,
            @Nullable final Policy policyCreated,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.policyCreated = policyCreated;
    }

    /**
     * Returns a new instance of {@code CreatePolicyResponse} for the specified arguments.
     *
     * @param policyId the Policy ID of the created Policy
     * @param policy the created Policy.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code CreatePolicyResponse} instance.
     * @throws NullPointerException if any argument is {@code null} except the {@code policy}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code CreatePolicyResponse}.
     * @since 2.3.0
     */
    public static CreatePolicyResponse newInstance(final PolicyId policyId,
            @Nullable final Policy policy,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new CreatePolicyResponse(policyId,
                policy,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        CreatePolicyResponse.class),
                dittoHeaders);
    }

    /**
     * Returns a new {@code CreatePolicyResponse} for a created Policy. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
     *
     * @param policyId the Policy ID of the created Policy.
     * @param policy the created Policy.
     * @param dittoHeaders the headers of the PolicyCommand which caused the new response.
     * @return a command response for a created Policy.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CreatePolicyResponse of(final PolicyId policyId,
            @Nullable final Policy policy,
            final DittoHeaders dittoHeaders) {

        return new CreatePolicyResponse(policyId, policy, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@code CreatePolicy} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static CreatePolicyResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code CreatePolicy} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CreatePolicyResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the created {@code Policy}.
     *
     * @return the created Policy.
     */
    public Optional<Policy> getPolicyCreated() {
        return Optional.ofNullable(policyCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(policyCreated).map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        if (null != policyCreated) {
            jsonObjectBuilder.set(JSON_POLICY, policyCreated.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public CreatePolicyResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(policyId, policyCreated, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof CreatePolicyResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CreatePolicyResponse that = (CreatePolicyResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(policyCreated, that.policyCreated) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, policyCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", policyCreated=" +
                policyCreated + "]";
    }

}
