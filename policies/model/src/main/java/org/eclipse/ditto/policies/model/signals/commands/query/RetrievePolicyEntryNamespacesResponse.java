/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.PolicyEntryNamespaces;

/**
 * Response to a {@link RetrievePolicyEntryNamespaces} command.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrievePolicyEntryNamespacesResponse.TYPE)
public final class RetrievePolicyEntryNamespacesResponse
        extends AbstractCommandResponse<RetrievePolicyEntryNamespacesResponse> implements
        PolicyQueryCommandResponse<RetrievePolicyEntryNamespacesResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrievePolicyEntryNamespaces.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_NAMESPACES =
            JsonFieldDefinition.ofJsonArray("namespaces", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrievePolicyEntryNamespacesResponse>
            JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new RetrievePolicyEntryNamespacesResponse(
                                PolicyId.of(jsonObject.getValueOrThrow(
                                        PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                Label.of(jsonObject.getValueOrThrow(JSON_LABEL)),
                                jsonObject.getValueOrThrow(JSON_NAMESPACES),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final Label label;
    private final JsonArray namespaces;

    private RetrievePolicyEntryNamespacesResponse(final PolicyId policyId,
            final Label label,
            final JsonArray namespaces,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrievePolicyEntryNamespacesResponse.class),
                dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.namespaces = checkNotNull(namespaces, "namespaces");
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntryNamespaces} command.
     *
     * @param policyId the Policy ID of the retrieved namespaces.
     * @param label the Label of the PolicyEntry.
     * @param namespaces the retrieved namespace patterns.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyEntryNamespacesResponse of(final PolicyId policyId,
            final Label label,
            final List<String> namespaces,
            final DittoHeaders dittoHeaders) {

        checkNotNull(namespaces, "namespaces");
        PolicyEntryNamespaces.validate(namespaces);
        final JsonArray namespacesJsonArray = namespaces.stream()
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
        return of(policyId, label, namespacesJsonArray, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntryNamespaces} command.
     *
     * @param policyId the Policy ID of the retrieved namespaces.
     * @param label the Label of the PolicyEntry.
     * @param namespaces the retrieved namespace patterns as JsonArray.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrievePolicyEntryNamespacesResponse of(final PolicyId policyId,
            final Label label,
            final JsonArray namespaces,
            final DittoHeaders dittoHeaders) {

        return new RetrievePolicyEntryNamespacesResponse(policyId, label, namespaces, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntryNamespaces} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrievePolicyEntryNamespacesResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrievePolicyEntryNamespaces} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrievePolicyEntryNamespacesResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose namespaces were retrieved.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the retrieved namespace patterns.
     *
     * @return the retrieved namespace patterns.
     */
    public List<String> getNamespaces() {
        return PolicyEntryNamespaces.fromJsonArray(namespaces);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return namespaces;
    }

    @Override
    public RetrievePolicyEntryNamespacesResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, label, entity.asArray(), getDittoHeaders());
    }

    @Override
    public RetrievePolicyEntryNamespacesResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, namespaces, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/namespaces";
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_NAMESPACES, namespaces, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrievePolicyEntryNamespacesResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrievePolicyEntryNamespacesResponse that = (RetrievePolicyEntryNamespacesResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(namespaces, that.namespaces) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, namespaces);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label +
                ", namespaces=" + namespaces +
                "]";
    }

}
