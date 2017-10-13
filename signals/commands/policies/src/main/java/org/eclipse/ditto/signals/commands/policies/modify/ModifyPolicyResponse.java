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
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link ModifyPolicy} command.
 */
@Immutable
public final class ModifyPolicyResponse extends AbstractCommandResponse<ModifyPolicyResponse> implements
        PolicyModifyCommandResponse<ModifyPolicyResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyPolicy.NAME;

    static final JsonFieldDefinition<JsonValue> JSON_POLICY =
            JsonFactory.newJsonValueFieldDefinition("policy", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String policyId;
    @Nullable private final Policy policyCreated;

    private ModifyPolicyResponse(final String policyId,
            final HttpStatusCode statusCode,
            @Nullable final Policy policyCreated,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "Policy ID");
        this.policyCreated = policyCreated;
    }

    /**
     * Returns a new {@code ModifyPolicyResponse} for a created Policy. This corresponds to the HTTP status code {@link
     * HttpStatusCode#CREATED}.
     *
     * @param policyId the Policy ID of the created policy.
     * @param policy the created Policy.
     * @param dittoHeaders the headers of the PolicyCommand which caused the new response.
     * @return a command response for a created Policy.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyResponse created(final String policyId, final Policy policy,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyResponse(policyId, HttpStatusCode.CREATED, policy, dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyPolicyResponse} for a modified Policy. This corresponds to the HTTP status code {@link
     * HttpStatusCode#NO_CONTENT}.
     *
     * @param policyId the Policy ID of the modified policy.
     * @param dittoHeaders the headers of the PolicyCommand which caused the new response.
     * @return a command response for a modified Policy.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ModifyPolicyResponse modified(final String policyId, final DittoHeaders dittoHeaders) {
        return new ModifyPolicyResponse(policyId, HttpStatusCode.NO_CONTENT, null, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicy} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected format.
     */
    public static ModifyPolicyResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicy} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static ModifyPolicyResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ModifyPolicyResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String policyId =
                            jsonObject.getValueOrThrow(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID);
                    final Policy extractedPolicyCreated = jsonObject.getValue(JSON_POLICY)
                            .map(JsonValue::asObject)
                            .map(PoliciesModelFactory::newPolicy)
                            .orElse(null);

                    return new ModifyPolicyResponse(policyId, statusCode, extractedPolicyCreated, dittoHeaders);
                });
    }

    @Override
    public String getId() {
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
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID, policyId, predicate);
        if (null != policyCreated) {
            jsonObjectBuilder.set(JSON_POLICY, policyCreated.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public ModifyPolicyResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return (null != policyCreated) ? created(policyId, policyCreated, dittoHeaders) :
                modified(policyId, dittoHeaders);
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
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) &&
                Objects.equals(policyCreated, that.policyCreated) && super.equals(o);
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
