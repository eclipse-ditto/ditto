/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.base.api.persistence.PersistenceLifecycle;
import org.eclipse.ditto.base.api.persistence.SnapshotTaken;

/**
 * Event published when a thing snapshot is taken.
 */
@JsonParsableEvent(name = ThingSnapshotTaken.NAME, typePrefix = ThingSnapshotTaken.TYPE_PREFIX)
public final class ThingSnapshotTaken extends SnapshotTaken<ThingSnapshotTaken> {

    /**
     * Pub-sub topic of this event.
     */
    public static final String PUB_SUB_TOPIC = "thing:snapshottaken";

    /**
     * The resource type of this event.
     */
    static final String RESOURCE_TYPE = "thing";

    /**
     * The prefix of this event's type.
     */
    static final String TYPE_PREFIX = RESOURCE_TYPE + ":";

    static final String NAME = "thingSnapshotTaken";

    /**
     * The type of this event.
     */
    static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_POLICY_ID =
            JsonFieldDefinition.ofString("policyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingId thingId;
    @Nullable private final PolicyId policyId;

    private ThingSnapshotTaken(final Builder builder) {
        super(TYPE,
                builder.revisionNumber,
                builder.timestamp,
                builder.metadata,
                builder.thingJson,
                builder.lifecycle,
                builder.dittoHeaders);
        thingId = builder.thingId;
        policyId = builder.policyId;
    }

    /**
     * Returns a new builder with a fluent API for creating a {@code ThingSnapshotTaken}.
     *
     * @param thingId the ID of the thing of which a snapshot was taken.
     * @param revisionNumber the revision number of the thing of which a snapshot was taken.
     * @param lifecycle the lifecycle of the thing of which a snapshot was taken.
     * @param thingJson the JSON representation of the thing of which a snapshot was taken.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Builder newBuilder(final ThingId thingId,
            final long revisionNumber,
            final PersistenceLifecycle lifecycle,
            final JsonObject thingJson) {

        return new Builder(ConditionChecker.checkNotNull(thingId, "thingId"),
                revisionNumber,
                ConditionChecker.checkNotNull(lifecycle, "lifecycle"),
                ConditionChecker.checkNotNull(thingJson, "thingJson"));
    }

    /**
     * Deserializes a {@code ThingSnapshotTaken} instance from the specified JSON object.
     *
     * @param jsonObject the JSON object that should be deserialized.
     * @param dittoHeaders the headers of the deserialized event.
     * @return the deserialized ThingSnapshotTaken instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException – if {@code jsonObject} did not contain all required
     * fields.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} does not represent a valid
     * {@code ThingSnapshotTaken}.
     */
    public static ThingSnapshotTaken fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        ConditionChecker.checkNotNull(jsonObject, "jsonObject");
        ConditionChecker.checkNotNull(dittoHeaders, "dittoHeaders");

        final var deserializer = JsonDeserializer.of(jsonObject, TYPE);
        return newBuilder(deserializeThingId(jsonObject),
                deserializer.deserializeRevision(),
                deserializer.deserializePersistenceLifecycle(),
                deserializer.deserializeEntity())
                .policyId(deserializePolicyId(jsonObject))
                .timestamp(deserializer.deserializeTimestamp())
                .metadata(deserializer.deserializeMetadata())
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static ThingId deserializeThingId(final JsonObject jsonObject) {
        final var fieldDefinition = SnapshotTaken.JsonFields.ENTITY_ID;
        try {
            return ThingId.of(jsonObject.getValueOrThrow(fieldDefinition));
        } catch (final ThingIdInvalidException e) {
            throw JsonDeserializer.getJsonParseException(fieldDefinition, ThingId.class, e);
        }
    }

    @Nullable
    private static PolicyId deserializePolicyId(final JsonObject jsonObject) {
        try {
            return jsonObject.getValue(JSON_POLICY_ID)
                    .map(PolicyId::of)
                    .orElse(null);
        } catch (final PolicyIdInvalidException e) {
            throw JsonDeserializer.getJsonParseException(JSON_POLICY_ID, PolicyId.class, e);
        }
    }

    /**
     * Gets the policy ID associated with the thing for which the snapshot was taken.
     *
     * @return an Optional containing the policyId, or an empty Optional if the policyId of the Thing is unknown.
     */
    public Optional<PolicyId> getPolicyId() {
        return Optional.ofNullable(policyId);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    @Override
    public String getPubSubTopic() {
        return PUB_SUB_TOPIC;
    }

    @Override
    protected ThingSnapshotTaken setDittoHeaders(final DittoHeaders dittoHeaders, final JsonObject thingJson) {
        return newBuilder(thingId, getRevision(), getLifecycle(), thingJson)
                .policyId(getPolicyId().orElse(null))
                .timestamp(getTimestamp().orElse(null))
                .metadata(getMetadata().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        var result = super.toJson(schemaVersion, predicate);
        if (null != policyId) {
            result = JsonFactory.newObjectBuilder(result)
                    .set(JSON_POLICY_ID, policyId.toString(), schemaVersion.and(predicate))
                    .build();
        }
        return result;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final var that = (ThingSnapshotTaken) o;
        return Objects.equals(thingId, that.thingId) && Objects.equals(policyId, that.policyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, policyId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", thingId=" + thingId +
                ", policyId=" + policyId +
                "]";
    }

    /**
     * A mutable builder with a fluent API for creating instances of {@code ThingSnapshotTaken}.
     * By default the builder sets the timestamp of the event to {@link Instant#now()} and empty DittoHeaders.
     * Both properties can be overwritten by calling the appropriate setter methods.
     */
    @NotThreadSafe
    public static final class Builder {

        private final ThingId thingId;
        private final long revisionNumber;
        private final PersistenceLifecycle lifecycle;
        private final JsonObject thingJson;
        @Nullable private PolicyId policyId;
        @Nullable private Instant timestamp;
        @Nullable private Metadata metadata;
        private DittoHeaders dittoHeaders;

        private Builder(final ThingId thingId,
                final long revisionNumber,
                final PersistenceLifecycle lifecycle,
                final JsonObject thingJson) {

            this.thingId = thingId;
            this.revisionNumber = revisionNumber;
            this.lifecycle = lifecycle;
            this.thingJson = thingJson;
            policyId = null;
            timestamp = Instant.now();
            metadata = null;
            dittoHeaders = DittoHeaders.empty();
        }

        /**
         * Sets the specified policy ID.
         *
         * @param policyId the policy ID to be set or {@code null} if the event has none (default).
         * @return this builder instance.
         */
        public Builder policyId(@Nullable final PolicyId policyId) {
            this.policyId = policyId;
            return this;
        }

        /**
         * Sets the timestamp when the snapshot was taken. By default the timestamp is set to {@link Instant#now()}.
         *
         * @param timestamp the timestamp to be set or {@code null} if the event does not have a timestamp at all.
         * @return this builder instance.
         */
        public Builder timestamp(@Nullable final Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Sets the specified metadata.
         *
         * @param metadata the metadata to be set or {@code null} if the event has none (default).
         * @return this builder instance.
         */
        public Builder metadata(@Nullable final Metadata metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Sets the specified DittoHeaders. By default empty headers are set.
         *
         * @param dittoHeaders the headers to be set.
         * @return this builder instance.
         * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
         */
        public Builder dittoHeaders(final DittoHeaders dittoHeaders) {
            this.dittoHeaders = ConditionChecker.checkNotNull(dittoHeaders, "dittoHeaders");
            return this;
        }

        /**
         * Constructs a new {@code ThingSnapshotTaken} instance.
         *
         * @return the ThingSnapshotTaken instance.
         */
        public ThingSnapshotTaken build() {
            return new ThingSnapshotTaken(this);
        }

    }

}
