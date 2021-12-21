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
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link ModifyPolicy} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyPolicyResponse.TYPE)
public final class ModifyPolicyResponse extends AbstractCommandResponse<ModifyPolicyResponse>
        implements PolicyModifyCommandResponse<ModifyPolicyResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyPolicy.NAME;

    static final JsonFieldDefinition<JsonValue> JSON_POLICY =
            JsonFieldDefinition.ofJsonValue("policy", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyPolicyResponse> JSON_DESERIALIZER =
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
    @Nullable private final Policy policy;

    private ModifyPolicyResponse(final PolicyId policyId,
            @Nullable final Policy policy,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.policy = ConditionChecker.checkArgument(
                policy,
                policyArgument -> {
                    final boolean result;
                    if (HttpStatus.NO_CONTENT.equals(httpStatus)) {
                        result = null == policyArgument;
                    } else {
                        result = null != policyArgument;
                    }
                    return result;
                },
                () -> MessageFormat.format("Policy <{0}> is illegal in conjunction with <{1}>.",
                        policy, httpStatus)
        );
    }

    /**
     * Returns a new {@code ModifyPolicyResponse} for a created Policy. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
     *
     * @param policyId the Policy ID of the created policy.
     * @param policy the created Policy.
     * @param dittoHeaders the headers of the PolicyCommand which caused the new response.
     * @return a command response for a created Policy.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyResponse created(final PolicyId policyId,
            final Policy policy,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId, policy, HttpStatus.CREATED, dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyPolicyResponse} for a modified Policy. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#NO_CONTENT}.
     *
     * @param policyId the Policy ID of the modified policy.
     * @param dittoHeaders the headers of the PolicyCommand which caused the new response.
     * @return a command response for a modified Policy.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyResponse modified(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        return newInstance(policyId, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyPolicyResponse} for the specified arguments.
     *
     * @param policyId the Policy ID of the created policy.
     * @param policy the created Policy.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code DeleteFeatureResponse} instance.
     * @throws NullPointerException if any argument is {@code null} except {@code policy}.
     * @throws IllegalArgumentException if {@code policy} is empty or blank or if {@code httpStatus} is not allowed
     * for a {@code ModifyPolicyResponse}.
     * @since 2.3.0
     */
    public static ModifyPolicyResponse newInstance(final PolicyId policyId,
            @Nullable final Policy policy,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyResponse(policyId,
                policy,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyPolicyResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicy} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected format.
     */
    public static ModifyPolicyResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicy} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static ModifyPolicyResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the {@code Policy}.
     *
     * @return the Policy.
     */
    public Optional<Policy> getPolicyCreated() {
        return Optional.ofNullable(policy);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(policy).map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
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
        if (null != policy) {
            jsonObjectBuilder.set(JSON_POLICY, policy.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public ModifyPolicyResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(policyId, policy, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyPolicyResponse that = (ModifyPolicyResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(policy, that.policy) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, policy);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", policy=" +
                policy + "]";
    }

}
