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
package org.eclipse.ditto.policies.model.signals.events;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
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

/**
 * This event is emitted after a {@link org.eclipse.ditto.policies.model.Resource} was created.
 */
@Immutable
@JsonParsableEvent(name = ResourceCreated.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class ResourceCreated extends AbstractPolicyEvent<ResourceCreated>
        implements PolicyEvent<ResourceCreated> {

    /**
     * Name of this event.
     */
    public static final String NAME = "resourceCreated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    public static final JsonFieldDefinition<String> JSON_RESOURCE_KEY =
            JsonFactory.newStringFieldDefinition("resourceKey", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_RESOURCE =
            JsonFactory.newJsonObjectFieldDefinition("resource", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label label;
    private final Resource resource;

    private ResourceCreated(final PolicyId policyId,
            final Label label,
            final Resource resource,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "Policy identifier"), revision, timestamp, dittoHeaders, metadata);
        this.label = checkNotNull(label, "Label");
        this.resource = checkNotNull(resource, "Resource");
    }

    /**
     * Constructs a new {@code ResourceCreated} object.
     *
     * @param policyId the identifier of the Policy to which the created Resource belongs
     * @param label the label of the Policy Entry to which the created Resource belongs
     * @param resource the created {@link org.eclipse.ditto.policies.model.Resource}
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created ResourceCreated.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static ResourceCreated of(final PolicyId policyId,
            final Label label,
            final Resource resource,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new ResourceCreated(policyId, label, resource, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code ResourceCreated} from a JSON string.
     *
     * @param jsonString the JSON string from which a new ResourceCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ResourceCreated} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'ResourceCreated' format.
     */
    public static ResourceCreated fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ResourceCreated} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new ResourceCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ResourceCreated} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'ResourceCreated' format.
     */
    public static ResourceCreated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ResourceCreated>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
            final ResourceKey resourceKey = ResourceKey.newInstance(jsonObject.getValueOrThrow(JSON_RESOURCE_KEY));
            final JsonObject resourceJsonObject = jsonObject.getValueOrThrow(JSON_RESOURCE);
            final Resource extractedCreatedResource = PoliciesModelFactory.newResource(resourceKey, resourceJsonObject);

            return of(policyId, label, extractedCreatedResource, revision, timestamp, dittoHeaders, metadata);
        });
    }

    /**
     * Returns the label of the Policy Entry to which the created Resource belongs.
     *
     * @return the label
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the created {@link org.eclipse.ditto.policies.model.Resource}.
     *
     * @return the created {@link org.eclipse.ditto.policies.model.Resource}.
     */
    public Resource getResource() {
        return resource;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(resource.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/resources" + resource.getPath();
        return JsonPointer.of(path);
    }

    @Override
    public ResourceCreated setRevision(final long revision) {
        return of(getPolicyEntityId(), label, resource, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public ResourceCreated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), label, resource, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_RESOURCE_KEY, resource.getFullQualifiedPath(), predicate);
        jsonObjectBuilder.set(JSON_RESOURCE, resource.toJson(schemaVersion, thePredicate), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(label);
        result = prime * result + Objects.hashCode(resource);
        return result;
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        final ResourceCreated that = (ResourceCreated) o;
        return that.canEqual(this) && Objects.equals(label, that.label) && Objects.equals(resource, that.resource)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ResourceCreated;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", label=" + label + ", resource=" + resource +
                "]";
    }

}
