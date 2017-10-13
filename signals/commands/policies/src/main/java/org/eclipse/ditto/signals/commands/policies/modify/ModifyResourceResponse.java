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
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link ModifyResource} command.
 */
@Immutable
public final class ModifyResourceResponse extends AbstractCommandResponse<ModifyResourceResponse> implements
        PolicyModifyCommandResponse<ModifyResourceResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyResource.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_RESOURCE_KEY =
            JsonFactory.newStringFieldDefinition("resourceKey", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_RESOURCE =
            JsonFactory.newJsonValueFieldDefinition("resource", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String policyId;
    private final Label label;
    @Nullable private final Resource resourceCreated;

    private ModifyResourceResponse(final String policyId,
            final Label label,
            @Nullable final Resource resourceCreated,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.policyId = checkNotNull(policyId, "Policy ID");
        this.label = checkNotNull(label, "Label");
        this.resourceCreated = resourceCreated;
    }

    /**
     * Creates a response to a {@code ModifyResource} command.
     *
     * @param policyId the Policy ID of the created resource.
     * @param label the Label of the PolicyEntry.
     * @param resourceCreated the Resource created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code statusCode} or {@code dittoHeaders} is {@code null}.
     */
    public static ModifyResourceResponse created(final String policyId,
            final Label label,
            final Resource resourceCreated,
            final DittoHeaders dittoHeaders) {

        return new ModifyResourceResponse(policyId, label, resourceCreated, HttpStatusCode.CREATED, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyResource} command.
     *
     * @param policyId the Policy ID of the modified resource.
     * @param label the Label of the PolicyEntry.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyResourceResponse modified(final String policyId, final Label label,
            final DittoHeaders dittoHeaders) {

        return new ModifyResourceResponse(policyId, label, null, HttpStatusCode.NO_CONTENT, dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyResource} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyResourceResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyResource} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyResourceResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ModifyResourceResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String policyId =
                            jsonObject.getValueOrThrow(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID);

                    final String stringLabel = jsonObject.getValueOrThrow(JSON_LABEL);
                    final Label label = PoliciesModelFactory.newLabel(stringLabel);

                    final Optional<ResourceKey> extractedResourceKey = jsonObject.getValue(JSON_RESOURCE_KEY)
                            .map(ResourceKey::newInstance);

                    final Resource extractedResourceCreated = jsonObject.getValue(JSON_RESOURCE)
                            .map(JsonValue::asObject)
                            .map(obj -> extractedResourceKey.map(
                                    resourceKey -> PoliciesModelFactory.newResource(resourceKey, obj)).orElse(null))
                            .orElse(null);

                    return new ModifyResourceResponse(policyId, label, extractedResourceCreated, statusCode,
                            dittoHeaders);
                });
    }

    @Override
    public String getId() {
        return policyId;
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} whose {@code Resource} was modified.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the created {@code Resource}.
     *
     * @return the created Resource.
     */
    public Optional<Resource> getResourceCreated() {
        return Optional.ofNullable(resourceCreated);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(resourceCreated).map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/resources/" + resourceCreated.getResourceKey();
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyModifyCommandResponse.JsonFields.JSON_POLICY_ID, policyId, predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        if (null != resourceCreated) {
            jsonObjectBuilder.set(JSON_RESOURCE_KEY, resourceCreated.getFullQualifiedPath(), predicate);
            jsonObjectBuilder.set(JSON_RESOURCE, resourceCreated.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public ModifyResourceResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return (null != resourceCreated) ? created(policyId, label, resourceCreated, dittoHeaders) :
                modified(policyId, label, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyResourceResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyResourceResponse that = (ModifyResourceResponse) o;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) && Objects.equals(label, that.label)
                && Objects.equals(resourceCreated, that.resourceCreated) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, resourceCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label +
                ", resourceCreated=" + resourceCreated + "]";
    }

}
