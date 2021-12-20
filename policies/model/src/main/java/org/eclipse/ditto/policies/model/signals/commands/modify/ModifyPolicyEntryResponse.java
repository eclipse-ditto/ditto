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
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link ModifyPolicyEntry} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyPolicyEntryResponse.TYPE)
public final class ModifyPolicyEntryResponse extends AbstractCommandResponse<ModifyPolicyEntryResponse>
        implements PolicyModifyCommandResponse<ModifyPolicyEntryResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyPolicyEntry.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_POLICY_ENTRY =
            JsonFieldDefinition.ofJsonValue("policyEntry", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyPolicyEntryResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final Label readLabel = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
                        @Nullable final PolicyEntry extractedPolicyEntryCreated =
                                jsonObject.getValue(JSON_POLICY_ENTRY)
                                        .filter(JsonValue::isObject)
                                        .map(JsonValue::asObject)
                                        .map(obj -> PoliciesModelFactory.newPolicyEntry(readLabel, obj))
                                        .orElse(null);

                        return newInstance(
                                PolicyId.of(
                                        jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                extractedPolicyEntryCreated,
                                readLabel,
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final Label label;
    @Nullable private final PolicyEntry policyEntry;

    private ModifyPolicyEntryResponse(final PolicyId policyId,
            @Nullable final PolicyEntry policyEntry,
            final Label label,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.policyEntry = ConditionChecker.checkArgument(
                policyEntry,
                policyEntryArgument -> {
                    final boolean result;
                    if (HttpStatus.NO_CONTENT.equals(httpStatus)) {
                        result = null == policyEntryArgument;
                    } else {
                        result = null != policyEntryArgument;
                    }
                    return result;
                },
                () -> MessageFormat.format("Policy entry <{0}> is illegal in conjunction with <{1}>.",
                        policyEntry, httpStatus)
        );
    }

    /**
     * Creates a response to a {@code ModifyPolicyEntry} command.
     *
     * @param policyId the Policy ID of the created policy entry.
     * @param policyEntryCreated the PolicyEntry created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyPolicyEntryResponse created(final PolicyId policyId,
            final PolicyEntry policyEntryCreated,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId,
                checkNotNull(policyEntryCreated, "policyEntryCreated"),
                policyEntryCreated.getLabel(),
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyEntry} command.
     *
     * @param policyId the Policy ID of the modified policy entry.
     * @param label the label of the modified policy entry.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code policyId} or {@code dittoHeaders} is {@code null}.
     * @since 1.1.0
     */
    public static ModifyPolicyEntryResponse modified(final PolicyId policyId,
            final Label label,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId, null, label, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyPolicyEntryResponse} for the specified arguments.
     *
     * @param policyId the Policy ID of the modified policy entry.
     * @param policyEntry (optional) the PolicyEntry.
     * @param label the label of the modified policy entry.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyPolicyEntryResponse} instance.
     * @throws NullPointerException if any argument is {@code null} except {@code policyEntry}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a
     * {@code ModifyPolicyEntryResponse}.
     * @since 2.3.0
     */
    public static ModifyPolicyEntryResponse newInstance(final PolicyId policyId,
            @Nullable final PolicyEntry policyEntry,
            final Label label,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyPolicyEntryResponse(policyId,
                policyEntry,
                label,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyPolicyEntryResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyEntry} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyPolicyEntryResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyPolicyEntry} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyPolicyEntryResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the created PolicyEntry.
     *
     * @return the created PolicyEntry.
     */
    public Optional<PolicyEntry> getPolicyEntryCreated() {
        return Optional.ofNullable(policyEntry);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(policyEntry).map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/entries/" + label);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        if (null != policyEntry) {
            jsonObjectBuilder.set(JSON_POLICY_ENTRY, policyEntry.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public ModifyPolicyEntryResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(policyId, policyEntry, label, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyPolicyEntryResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyPolicyEntryResponse that = (ModifyPolicyEntryResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(policyEntry, that.policyEntry) &&
                Objects.equals(label, that.label) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, policyEntry, label);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId +
                ", policyEntry=" + policyEntry +
                ", label=" + label
                + "]";
    }

}
