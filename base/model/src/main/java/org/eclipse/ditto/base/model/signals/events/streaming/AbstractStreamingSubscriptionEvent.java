/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.events.streaming;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.EntityIdJsonDeserializer;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.entity.type.EntityTypeJsonDeserializer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.WithStreamingSubscriptionId;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Abstract base class of subscription events. Package-private. Not to be extended in user code.
 *
 * @param <T> the type of the implementing class.
 * @since 3.2.0
 */
@Immutable
abstract class AbstractStreamingSubscriptionEvent<T extends AbstractStreamingSubscriptionEvent<T>> implements
        StreamingSubscriptionEvent<T> {

    private final String type;
    private final String subscriptionId;

    private final EntityId entityId;
    private final DittoHeaders dittoHeaders;

    /**
     * Constructs a new {@code AbstractStreamingSubscriptionEvent} object.
     *
     * @param type the type of this event.
     * @param subscriptionId the subscription ID.
     * @param entityId the entity ID of this streaming subscription event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    protected AbstractStreamingSubscriptionEvent(final String type,
            final String subscriptionId,
            final EntityId entityId,
            final DittoHeaders dittoHeaders) {

        this.type = checkNotNull(type, "type");
        this.subscriptionId = checkNotNull(subscriptionId, "subscriptionId");
        this.entityId = checkNotNull(entityId, "entityId");
        this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders");
    }

    @Override
    public String getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public EntityId getEntityId() {
        return entityId;
    }

    @Override
    public EntityType getEntityType() {
        return entityId.getEntityType();
    }

    @Override
    public Optional<Instant> getTimestamp() {
        // subscription events have no timestamp.
        return Optional.empty();
    }

    @Override
    public Optional<Metadata> getMetadata() {
        return Optional.empty();
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Nonnull
    @Override
    public String getManifest() {
        return getType();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                // TYPE is included unconditionally
                .set(Event.JsonFields.TYPE, type)
                .set(WithStreamingSubscriptionId.JsonFields.SUBSCRIPTION_ID, subscriptionId)
                .set(StreamingSubscriptionEvent.JsonFields.JSON_ENTITY_ID, entityId.toString())
                .set(StreamingSubscriptionEvent.JsonFields.JSON_ENTITY_TYPE, entityId.getEntityType().toString());

        appendPayload(jsonObjectBuilder);

        return jsonObjectBuilder.build();
    }

    protected static EntityId deserializeEntityId(final JsonObject jsonObject) {
        return EntityIdJsonDeserializer.deserializeEntityId(jsonObject,
                StreamingSubscriptionEvent.JsonFields.JSON_ENTITY_ID,
                EntityTypeJsonDeserializer.deserializeEntityType(jsonObject,
                        StreamingSubscriptionEvent.JsonFields.JSON_ENTITY_TYPE));
    }

    /**
     * Appends the event specific custom payload to the passed {@code jsonObjectBuilder}.
     *
     * @param jsonObjectBuilder the JsonObjectBuilder to add the custom payload to.
     */
    protected abstract void appendPayload(final JsonObjectBuilder jsonObjectBuilder);

    @Override
    public boolean equals(@Nullable final Object o) {
        if (o != null && getClass() == o.getClass()) {
            final AbstractStreamingSubscriptionEvent<?> that = (AbstractStreamingSubscriptionEvent<?>) o;
            return Objects.equals(type, that.type) &&
                    Objects.equals(subscriptionId, that.subscriptionId) &&
                    Objects.equals(entityId, that.entityId) &&
                    Objects.equals(dittoHeaders, that.dittoHeaders);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, subscriptionId, entityId, dittoHeaders);
    }

    @Override
    public String toString() {
        return "type=" + type +
                ", subscriptionId=" + subscriptionId +
                ", entityId=" + entityId +
                ", dittoHeaders=" + dittoHeaders;
    }

}
