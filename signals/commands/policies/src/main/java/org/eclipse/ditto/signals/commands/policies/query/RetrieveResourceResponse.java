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
package org.eclipse.ditto.signals.commands.policies.query;

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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link RetrieveResource} command.
 */
@Immutable
public final class RetrieveResourceResponse extends AbstractCommandResponse<RetrieveResourceResponse> implements
        PolicyQueryCommandResponse<RetrieveResourceResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveResource.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_RESOURCE_KEY =
            JsonFactory.newStringFieldDefinition("resourceKey", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_RESOURCE =
            JsonFactory.newJsonObjectFieldDefinition("resource", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String policyId;
    private final Label label;
    private final ResourceKey resourceKey;
    private final JsonObject resource;

    private RetrieveResourceResponse(final String policyId,
            final Label label,
            final ResourceKey resourceKey,
            final JsonObject resource,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "Policy ID");
        this.label = checkNotNull(label, "Label");
        this.resourceKey = checkNotNull(resourceKey, "ResourceKey");
        this.resource = checkNotNull(resource, "Resource");
    }

    /**
     * Creates a response to a {@code RetrieveResource} command.
     *
     * @param policyId the Policy ID of the retrieved resource.
     * @param label the Label of the PolicyEntry.
     * @param resource the retrieved Resource.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveResourceResponse of(final String policyId,
            final Label label,
            final Resource resource,
            final DittoHeaders dittoHeaders) {

        return new RetrieveResourceResponse(policyId, label, resource.getResourceKey(),
                checkNotNull(resource, "Resource").toJson(
                        dittoHeaders.getSchemaVersion().orElse(resource.getLatestSchemaVersion())), HttpStatusCode.OK,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveResource} command.
     *
     * @param policyId the Policy ID of the retrieved resource.
     * @param label the Label of the PolicyEntry.
     * @param resourceKey the ResourceKey of the retrieved Resource.
     * @param resource the retrieved Resource.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveResourceResponse of(final String policyId,
            final Label label,
            final ResourceKey resourceKey,
            final JsonObject resource,
            final DittoHeaders dittoHeaders) {

        return new RetrieveResourceResponse(policyId, label, resourceKey, resource, HttpStatusCode.OK, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveResource} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveResourceResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveResource} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveResourceResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<RetrieveResourceResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String policyId =
                            jsonObject.getValueOrThrow(PolicyQueryCommandResponse.JsonFields.JSON_POLICY_ID);
                    final Label label = PoliciesModelFactory.newLabel(jsonObject.getValueOrThrow(JSON_LABEL));
                    final String extractedResourceKey = jsonObject.getValueOrThrow(JSON_RESOURCE_KEY);
                    final JsonObject extractedResource = jsonObject.getValueOrThrow(JSON_RESOURCE);

                    return of(policyId, label, ResourceKey.newInstance(extractedResourceKey), extractedResource,
                            dittoHeaders);
                });
    }

    @Override
    public String getId() {
        return policyId;
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose {@code Resource} was retrieved.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the retrieved Resource.
     *
     * @return the retrieved Resource.
     */
    public Resource getResource() {
        return PoliciesModelFactory.newResource(resourceKey, resource);
    }

    /**
     * Returns the ResourceKey of the Resource.
     *
     * @return the ResourceKey of the Resource.
     */
    public ResourceKey getResourceKey() {
        return resourceKey;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return resource;
    }

    @Override
    public RetrieveResourceResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, label, resourceKey, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveResourceResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, getResource(), dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        final String p = "/entries/" + label + "/resources/" + resourceKey;
        return JsonPointer.of(p);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyQueryCommandResponse.JsonFields.JSON_POLICY_ID, policyId, predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_RESOURCE_KEY, resourceKey.toString(), predicate);
        jsonObjectBuilder.set(JSON_RESOURCE, resource, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveResourceResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveResourceResponse that = (RetrieveResourceResponse) o;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId)
                && Objects.equals(label, that.label) && Objects.equals(resourceKey, that.resourceKey)
                && Objects.equals(resource, that.resource) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, resourceKey, resource);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label +
                ", resourceKey=" + resourceKey + ", resource=" + resource + "]";
    }

}
