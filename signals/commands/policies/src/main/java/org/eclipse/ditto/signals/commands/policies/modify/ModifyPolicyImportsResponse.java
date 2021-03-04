/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.PolicyImports;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link ModifyPolicyImports} command.
 */
@Immutable
public final class ModifyPolicyImportsResponse extends AbstractCommandResponse<ModifyPolicyImportsResponse> implements
        PolicyModifyCommandResponse<ModifyPolicyImportsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyPolicyImports.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_POLICY_IMPORTS =
            JsonFactory.newJsonObjectFieldDefinition("policyImports", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    @Nullable private final PolicyImports policyImportsCreated;

    private ModifyPolicyImportsResponse(final PolicyId policyId, final HttpStatus statusCode,
            @Nullable final PolicyImports policyImportsCreated, final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "Policy ID");
        this.policyImportsCreated = policyImportsCreated;
    }

    /**
     * Creates a response to a {@code ModifyPolicyImport} command.
     *
     * @param policyId the Policy ID of the created policy imports.
     * @param policyImportsCreated (optional) the PolicyImports created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code statusCode} or {@code dittoHeaders} is {@code null}.
     */
    public static ModifyPolicyImportsResponse created(final PolicyId policyId, final PolicyImports policyImportsCreated,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyImportsResponse(policyId, HttpStatus.CREATED, policyImportsCreated, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyImports} command.
     *
     * @param policyId the Policy ID of the modified policy imports.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyImportsResponse modified(final PolicyId policyId, final DittoHeaders dittoHeaders) {
        return new ModifyPolicyImportsResponse(policyId, HttpStatus.NO_CONTENT, null, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyImports} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyPolicyImportsResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyImports} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyPolicyImportsResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new CommandResponseJsonDeserializer<ModifyPolicyImportsResponse>(TYPE, jsonObject)
                .deserialize(statusCode -> {
                    final PolicyId policyId = PolicyId.of(
                            jsonObject.getValueOrThrow(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID));

                    return modified(policyId, dittoHeaders);
                });
    }

    /**
     * Returns the created PolicyImport.
     *
     * @return the created PolicyImport.
     */
    public Optional<PolicyImports> getPolicyImportsCreated() {
        return Optional.ofNullable(policyImportsCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(policyImportsCreated).map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/imports");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        if (null != policyImportsCreated) {
            jsonObjectBuilder.set(JSON_POLICY_IMPORTS, policyImportsCreated.toJson(schemaVersion, thePredicate),
                    predicate);
        }
    }

    @Override
    public ModifyPolicyImportsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return (policyImportsCreated != null) ? created(policyId, policyImportsCreated, dittoHeaders) :
                modified(policyId, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyPolicyImportsResponse that = (ModifyPolicyImportsResponse) o;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId)
                && Objects.equals(policyImportsCreated, that.policyImportsCreated) && super.equals(o);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyImportsResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, policyImportsCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId +
                ", policyImportsCreated=" + policyImportsCreated + "]";
    }

}
