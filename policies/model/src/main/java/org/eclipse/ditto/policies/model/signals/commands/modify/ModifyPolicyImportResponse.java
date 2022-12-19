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
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link ModifyPolicyImport} command.
 *
 * @since 3.1.0
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyPolicyImportResponse.TYPE)
public final class ModifyPolicyImportResponse extends AbstractCommandResponse<ModifyPolicyImportResponse>
        implements PolicyModifyCommandResponse<ModifyPolicyImportResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyPolicyImport.NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_POLICY_IMPORT =
            JsonFactory.newJsonValueFieldDefinition("policyImport", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyPolicyImportResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final PolicyId readImportedPolicyId = PolicyId.of(jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID));

                        final PolicyImport extractedPolicyImportCreated = jsonObject.getValue(JSON_POLICY_IMPORT)
                                .filter(JsonValue::isObject)
                                .map(JsonValue::asObject)
                                .map(obj -> PoliciesModelFactory.newPolicyImport(readImportedPolicyId, obj))
                                .orElse(null);

                        return newInstance(
                                PolicyId.of(jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                readImportedPolicyId,
                                extractedPolicyImportCreated,
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final PolicyId importedPolicyId;
    @Nullable private final PolicyImport policyImportCreated;

    private ModifyPolicyImportResponse(final PolicyId policyId,
            final PolicyId importedPolicyId,
            @Nullable final PolicyImport policyImportCreated,
            final HttpStatus statusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.importedPolicyId = checkNotNull(importedPolicyId, "importedPolicyId");
        this.policyId = checkNotNull(policyId, "policyId");
        this.policyImportCreated = policyImportCreated;
    }

    /**
     * Creates a response to a {@code ModifyPolicyImport} command.
     *
     * @param policyId the Policy ID of the created policy import.
     * @param policyImportCreated (optional) the PolicyImport created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyImportResponse created(final PolicyId policyId, final PolicyImport policyImportCreated,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyImportResponse(policyId,
                policyImportCreated.getImportedPolicyId(),
                checkNotNull(policyImportCreated, "policyImportCreated"),
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyImport} command.
     *
     * @param policyId the Policy ID of the modified policy import.
     * @param importedPolicyId the id of the modified policy import.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code policyId} or {@code dittoHeaders} is {@code null}.
     */
    public static ModifyPolicyImportResponse modified(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final DittoHeaders dittoHeaders) {
        return new ModifyPolicyImportResponse(policyId, importedPolicyId, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyPolicyImportResponse} for the specified arguments.
     *
     * @param policyId the Policy ID of the modified resource.
     * @param importedPolicyId the id of the modified policy import.
     * @param policyImportCreated the created PolicyImport.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyPolicyImportResponse} instance.
     * @throws NullPointerException if any argument is {@code null} except {@code resource}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code ModifyPolicyImportResponse}.
     */
    public static ModifyPolicyImportResponse newInstance(final PolicyId policyId,
            final PolicyId importedPolicyId,
            @Nullable final PolicyImport policyImportCreated,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyImportResponse(policyId,
                importedPolicyId,
                policyImportCreated,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyPolicyImportResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyImport} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected format.
     */
    public static ModifyPolicyImportResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyImport} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static ModifyPolicyImportResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * Returns the imported PolicyId.
     *
     * @return the imported PolicyId.
     */
    public PolicyId getImportedPolicyId() {
        return importedPolicyId;
    }

    /**
     * Returns the created PolicyImport.
     *
     * @return the created PolicyImport.
     */
    public Optional<PolicyImport> getPolicyImportCreated() {
        return Optional.ofNullable(policyImportCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(policyImportCreated).map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        if (policyImportCreated == null) {
            return JsonPointer.empty();
        }

        final String path = "/imports/" + importedPolicyId;
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, policyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, importedPolicyId.toString(), predicate);
        if (null != policyImportCreated) {
            jsonObjectBuilder.set(JSON_POLICY_IMPORT, policyImportCreated.toJson(schemaVersion, thePredicate),
                    predicate);
        }
    }

    @Override
    public ModifyPolicyImportResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return (policyImportCreated != null) ? created(policyId, policyImportCreated, dittoHeaders) :
                modified(policyId, importedPolicyId, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyImportResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyPolicyImportResponse that = (ModifyPolicyImportResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(importedPolicyId, that.importedPolicyId) &&
                Objects.equals(policyImportCreated, that.policyImportCreated) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, importedPolicyId, policyImportCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", importedPolicyId=" + importedPolicyId +
                ", policyImportCreated=" + policyImportCreated +
                "]";
    }

}
