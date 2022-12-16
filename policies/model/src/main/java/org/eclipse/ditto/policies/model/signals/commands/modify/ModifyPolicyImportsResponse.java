/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link ModifyPolicyImports} command.
 *
 * @since 3.1.0
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyPolicyImportsResponse.TYPE)
public final class ModifyPolicyImportsResponse extends AbstractCommandResponse<ModifyPolicyImportsResponse> implements
        PolicyModifyCommandResponse<ModifyPolicyImportsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyPolicyImports.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_POLICY_IMPORTS =
            JsonFactory.newJsonObjectFieldDefinition("policyImports", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyPolicyImportsResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final PolicyImports extractedPolicyImportsCreated = jsonObject.getValue(JSON_POLICY_IMPORTS)
                                .filter(JsonValue::isObject)
                                .map(JsonValue::asObject)
                                .map(PoliciesModelFactory::newPolicyImports)
                                .orElse(null);

                        return newInstance(
                                PolicyId.of(jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                extractedPolicyImportsCreated,
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    @Nullable private final PolicyImports policyImportsCreated;

    private ModifyPolicyImportsResponse(final PolicyId policyId, @Nullable final PolicyImports policyImportsCreated,
            final HttpStatus statusCode, final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
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

        return new ModifyPolicyImportsResponse(policyId, policyImportsCreated, HttpStatus.CREATED, dittoHeaders);
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
        return new ModifyPolicyImportsResponse(policyId, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyPolicyImportsResponse} for the specified arguments.
     *
     * @param policyId the Policy ID of the modified resource.
     * @param policyImportsCreated the created PolicyImports.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyPolicyImportsResponse} instance.
     * @throws NullPointerException if any argument is {@code null} except {@code resource}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code ModifyPolicyImportsResponse}.
     */
    public static ModifyPolicyImportsResponse newInstance(final PolicyId policyId,
            @Nullable final PolicyImports policyImportsCreated,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyImportsResponse(policyId,
                policyImportsCreated,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyPolicyImportsResponse.class),
                dittoHeaders);
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
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
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
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
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
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(policyImportsCreated, that.policyImportsCreated) &&
                super.equals(o);
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
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", policyImportsCreated=" + policyImportsCreated +
                "]";
    }

}
