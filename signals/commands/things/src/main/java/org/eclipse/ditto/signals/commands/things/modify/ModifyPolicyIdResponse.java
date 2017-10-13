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
package org.eclipse.ditto.signals.commands.things.modify;

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
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link ModifyPolicyId} command.
 */
@Immutable
public final class ModifyPolicyIdResponse extends AbstractCommandResponse<ModifyPolicyIdResponse> implements
        ThingModifyCommandResponse<ModifyPolicyIdResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyPolicyId.NAME;

    static final JsonFieldDefinition<String> JSON_POLICY_ID =
            JsonFactory.newStringFieldDefinition("policyId", FieldType.REGULAR, JsonSchemaVersion.V_2);


    private final String thingId;
    @Nullable private final String policyId;

    private ModifyPolicyIdResponse(final String thingId, final HttpStatusCode statusCode,
            @Nullable final String policyId, final DittoHeaders dittoHeaders) {
        super(TYPE, statusCode, dittoHeaders);
        this.thingId = checkNotNull(thingId, "Thing ID");
        this.policyId = policyId;
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

    /**
     * Returns a new {@code ModifyPolicyIdResponse} for a created Policy ID. This corresponds to the HTTP status code
     * {@link HttpStatusCode#CREATED}.
     *
     * @param thingId the Thing ID of the created policy ID.
     * @param policyId the created Policy ID.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created Policy ID.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyIdResponse created(final String thingId, final String policyId,
            final DittoHeaders dittoHeaders) {
        return new ModifyPolicyIdResponse(thingId, HttpStatusCode.CREATED, policyId, dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyPolicyIdResponse} for a modified Policy ID. This corresponds to the HTTP status code
     * {@link HttpStatusCode#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified policy ID.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified Policy ID.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ModifyPolicyIdResponse modified(final String thingId, final DittoHeaders dittoHeaders) {
        return new ModifyPolicyIdResponse(thingId, HttpStatusCode.NO_CONTENT, null, dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyPolicyId} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyPolicyIdResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyPolicyId} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyPolicyIdResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ModifyPolicyIdResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String thingId =
                            jsonObject.getValueOrThrow(ThingModifyCommandResponse.JsonFields.JSON_THING_ID);
                    final String policyId = jsonObject.getValue(JSON_POLICY_ID).orElse(null);

                    return new ModifyPolicyIdResponse(thingId, statusCode, policyId, dittoHeaders);
                });
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    /**
     * Returns the created Policy ID.
     *
     * @return the created Policy ID.
     */
    public Optional<String> getPolicyId() {
        return Optional.ofNullable(policyId);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(policyId).map(JsonValue::of);
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = Thing.JsonFields.POLICY_ID.getPointer().toString();
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
        if (policyId != null) {
            jsonObjectBuilder.set(JSON_POLICY_ID, policyId, predicate);
        }
    }

    @Override
    public ModifyPolicyIdResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return policyId != null ? created(thingId, policyId, dittoHeaders) : modified(thingId, dittoHeaders);
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
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(policyId, that.policyId) && super.equals(o);
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
