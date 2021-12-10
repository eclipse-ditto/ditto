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
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link ModifyResource} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyResourceResponse.TYPE)
public final class ModifyResourceResponse extends AbstractCommandResponse<ModifyResourceResponse>
        implements PolicyModifyCommandResponse<ModifyResourceResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyResource.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_RESOURCE_KEY =
            JsonFieldDefinition.ofString("resourceKey", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_RESOURCE =
            JsonFieldDefinition.ofJsonValue("resource", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyResourceResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final ResourceKey resourceKey =
                                ResourceKey.newInstance(jsonObject.getValueOrThrow(JSON_RESOURCE_KEY));

                        return newInstance(
                                PolicyId.of(jsonObject.getValueOrThrow(PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                Label.of(jsonObject.getValueOrThrow(JSON_LABEL)),
                                resourceKey,
                                jsonObject.getValue(JSON_RESOURCE)
                                        .map(JsonValue::asObject)
                                        .map(obj -> PoliciesModelFactory.newResource(resourceKey, obj))
                                        .orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final Label label;
    private final ResourceKey resourceKey;
    @Nullable private final Resource resource;

    private ModifyResourceResponse(final PolicyId policyId,
            final Label label,
            final ResourceKey resourceKey,
            @Nullable final Resource resource,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.resourceKey = checkNotNull(resourceKey, "resourceKey");
        this.resource = ConditionChecker.checkArgument(
                resource,
                resourceArgument -> {
                    final boolean result;
                    if (HttpStatus.NO_CONTENT.equals(httpStatus)) {
                        result = null == resourceArgument;
                    } else {
                        result = null != resourceArgument;
                    }
                    return result;
                },
                () -> MessageFormat.format("Resource <{0}> is illegal in conjunction with <{1}>.",
                        resource,
                        httpStatus)
        );
    }

    /**
     * Creates a response to a {@code ModifyResource} command.
     *
     * @param policyId the Policy ID of the created resource.
     * @param label the Label of the PolicyEntry.
     * @param resourceCreated the Resource created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyResourceResponse created(final PolicyId policyId,
            final Label label,
            final Resource resourceCreated,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId,
                label,
                checkNotNull(resourceCreated, "resourceCreated").getResourceKey(),
                resourceCreated,
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyResource} command.
     *
     * @param policyId the Policy ID of the modified resource.
     * @param label the Label of the PolicyEntry.
     * @param resourceKey the resource key of the modified resource
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument but {@code resourceKey} is {@code null}.
     * @since 1.1.0
     */
    public static ModifyResourceResponse modified(final PolicyId policyId,
            final Label label,
            final ResourceKey resourceKey,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId,
                label,
                resourceKey,
                null,
                HttpStatus.NO_CONTENT,
                dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyResourceResponse} for the specified arguments.
     *
     * @param policyId the Policy ID of the modified resource.
     * @param label the Label of the PolicyEntry.
     * @param resourceKey the resource key of the modified resource.
     * @param resource the Resource created.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyResourceResponse} instance.
     * @throws NullPointerException if any argument is {@code null} except {@code resource}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code ModifyResourceResponse}.
     * @since 2.3.0
     */
    public static ModifyResourceResponse newInstance(final PolicyId policyId,
            final Label label,
            final ResourceKey resourceKey,
            @Nullable final Resource resource,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyResourceResponse(policyId,
                label,
                resourceKey,
                resource,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyResourceResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyResource} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyResourceResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyResource} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyResourceResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
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
        return Optional.ofNullable(resource);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(resource).map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/entries/" + label + "/resources/" + resourceKey);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_RESOURCE_KEY, resourceKey.toString(), predicate);
        if (null != resource) {
            jsonObjectBuilder.set(JSON_RESOURCE, resource.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public ModifyResourceResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(policyId, label, resourceKey, resource, getHttpStatus(), dittoHeaders);
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
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(resource, that.resource) &&
                Objects.equals(resourceKey, that.resourceKey) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, resource, resourceKey);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label +
                ", resourceKey=" + resourceKey + ", resource=" + resource + "]";
    }

}
