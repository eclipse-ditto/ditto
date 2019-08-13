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
package org.eclipse.ditto.signals.commands.policies.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link DeleteResource} command.
 */
@Immutable
@JsonParsableCommandResponse(type = DeleteResourceResponse.TYPE)
public final class DeleteResourceResponse extends AbstractCommandResponse<DeleteResourceResponse>
        implements PolicyModifyCommandResponse<DeleteResourceResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + DeleteResource.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_RESOURCE_KEY =
            JsonFactory.newStringFieldDefinition("resourceKey", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final Label label;
    private final ResourceKey resourceKey;

    private DeleteResourceResponse(final PolicyId policyId,
            final Label label,
            final ResourceKey resourceKey,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "Policy ID");
        this.label = checkNotNull(label, "Label");
        this.resourceKey = checkNotNull(resourceKey, "ResourceKey");
    }

    /**
     * Creates a response to a {@code DeleteResource} command.
     *
     * @param policyId the Policy ID of the deleted resource.
     * @param label the Label of the PolicyEntry.
     * @param resourceKey the ResourceKey of the deleted Resource.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @deprecated Policy ID is now typed. Use
     * {@link #of(org.eclipse.ditto.model.policies.PolicyId, org.eclipse.ditto.model.policies.Label, org.eclipse.ditto.model.policies.ResourceKey, org.eclipse.ditto.model.base.headers.DittoHeaders)}
     * instead.
     */
    @Deprecated
    public static DeleteResourceResponse of(final String policyId,
            final Label label,
            final ResourceKey resourceKey,
            final DittoHeaders dittoHeaders) {

        return of(PolicyId.of(policyId), label, resourceKey, dittoHeaders);
    }

    /**
     * Creates a response to a {@code DeleteResource} command.
     *
     * @param policyId the Policy ID of the deleted resource.
     * @param label the Label of the PolicyEntry.
     * @param resourceKey the ResourceKey of the deleted Resource.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DeleteResourceResponse of(final PolicyId policyId,
            final Label label,
            final ResourceKey resourceKey,
            final DittoHeaders dittoHeaders) {

        return new DeleteResourceResponse(policyId, label, resourceKey, HttpStatusCode.NO_CONTENT, dittoHeaders);
    }

    /**
     * Creates a response to a {@code DeleteResource} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static DeleteResourceResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code DeleteResource} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static DeleteResourceResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<DeleteResourceResponse>(TYPE, jsonObject)
                .deserialize(statusCode -> {
            final String extractedPolicyId =
                    jsonObject.getValueOrThrow(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label label = PoliciesModelFactory.newLabel(jsonObject.getValueOrThrow(JSON_LABEL));
                    final String path = jsonObject.getValueOrThrow(JSON_RESOURCE_KEY);

                    return of(policyId, label, ResourceKey.newInstance(path), dittoHeaders);
                });
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose {@code Resource} was deleted.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the {@code ResourceKey} of the deleted {@code Resource}.
     *
     * @return the ResourceKey of the Resource to delete.
     */
    public ResourceKey getResourceKey() {
        return resourceKey;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String p = "/entries/" + label + "/resources/" + resourceKey.toString();
        return JsonPointer.of(p);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId),
                predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_RESOURCE_KEY, resourceKey.toString(), predicate);
    }

    @Override
    public DeleteResourceResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, resourceKey, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DeleteResourceResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeleteResourceResponse that = (DeleteResourceResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(resourceKey, that.resourceKey) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, resourceKey);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label
                + ", resourceKey=" + resourceKey + "]";
    }

}
