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
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;

/**
 * This event is emitted after a {@link org.eclipse.ditto.policies.model.Resource} was deleted.
 */
@Immutable
@JsonParsableEvent(name = ResourceDeleted.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class ResourceDeleted extends AbstractPolicyEvent<ResourceDeleted>
        implements PolicyEvent<ResourceDeleted> {

    /**
     * Name of this event.
     */
    public static final String NAME = "resourceDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_RESOURCE_KEY =
            JsonFactory.newStringFieldDefinition("resourceKey", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label label;
    private final ResourceKey resourceKey;

    private ResourceDeleted(final PolicyId policyId,
            final Label label,
            final ResourceKey resourceKey,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "Policy identifier"), revision, timestamp, dittoHeaders, metadata);
        this.label = checkNotNull(label, "Label");
        this.resourceKey = checkNotNull(resourceKey, "ResourceKey");
    }

    /**
     * Constructs a new {@code ResourceDeleted} object.
     *
     * @param policyId the identifier of the Policy to which the modified Resource belongs
     * @param label the label of the Policy Entry to which the modified Resource belongs
     * @param resourceKey the deleted {@link org.eclipse.ditto.policies.model.ResourceKey}.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created ResourceDeleted.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static ResourceDeleted of(final PolicyId policyId,
            final Label label,
            final ResourceKey resourceKey,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new ResourceDeleted(policyId, label, resourceKey, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code ResourceDeleted} from a JSON string.
     *
     * @param jsonString the JSON string from which a new ResourceDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ResourceDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'ResourceDeleted' format.
     */
    public static ResourceDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ResourceDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new ResourceDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ResourceDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'ResourceDeleted' format.
     */
    public static ResourceDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<ResourceDeleted>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
                    final String resourceKey = jsonObject.getValueOrThrow(JSON_RESOURCE_KEY);

                    return of(policyId, label, ResourceKey.newInstance(resourceKey), revision, timestamp, dittoHeaders,
                            metadata);
                });
    }

    /**
     * Returns the label of the Policy Entry to which the deleted Resource belongs.
     *
     * @return the label
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the deleted {@link org.eclipse.ditto.policies.model.ResourceKey}.
     *
     * @return the deleted {@link org.eclipse.ditto.policies.model.ResourceKey}.
     */
    public ResourceKey getResourceKey() {
        return resourceKey;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/resources/" + resourceKey;
        return JsonPointer.of(path);
    }

    @Override
    public ResourceDeleted setRevision(final long revision) {
        return of(getPolicyEntityId(), label, resourceKey, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public ResourceDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), label, resourceKey, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_RESOURCE_KEY, resourceKey.toString(), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(label);
        result = prime * result + Objects.hashCode(resourceKey);
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
        final ResourceDeleted that = (ResourceDeleted) o;
        return that.canEqual(this) && Objects.equals(label, that.label) && Objects.equals(resourceKey, that.resourceKey)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ResourceDeleted;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", label=" + label + ", resourceKey=" +
                resourceKey + "]";
    }

}
