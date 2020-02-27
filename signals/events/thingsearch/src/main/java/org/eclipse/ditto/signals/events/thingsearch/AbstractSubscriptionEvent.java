/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.events.thingsearch;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Abstract base class of subscription events. Package-private. Not to be extended in user code.
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
abstract class AbstractSubscriptionEvent<T extends AbstractSubscriptionEvent<T>> implements SubscriptionEvent<T> {

    private final String type;
    private final String subscriptionId;
    private final DittoHeaders dittoHeaders;

    /**
     * Constructs a new {@code AbstractThingEvent} object.
     *
     * @param type the type of this event.
     * @param subscriptionId the subscription ID.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    protected AbstractSubscriptionEvent(final String type,
            final String subscriptionId,
            final DittoHeaders dittoHeaders) {

        this.type = checkNotNull(type, "type");
        this.subscriptionId = checkNotNull(subscriptionId, "subscriptionId");
        this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders");
    }

    /**
     * Retrieve the subscription ID.
     *
     * @return the subscription ID.
     */
    public String getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public long getRevision() {
        // subscription events have no revision.
        return 0L;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T setRevision(final long revision) {
        // subscription events have no revision.
        return (T) this;
    }

    @Override
    public EntityId getEntityId() {
        // subscription events relate to no entity.
        // subscription ID is ephemeral, similar to correlation ID.
        return DefaultEntityId.dummy();
    }

    @Override
    public Optional<Instant> getTimestamp() {
        // subscription events have no timestamp.
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
                .set(JsonFields.SUBSCRIPTION_ID, subscriptionId);

        appendPayload(jsonObjectBuilder);

        return jsonObjectBuilder.build();
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
            final AbstractSubscriptionEvent<?> that = (AbstractSubscriptionEvent<?>) o;
            return Objects.equals(type, that.type) &&
                    Objects.equals(subscriptionId, that.subscriptionId) &&
                    Objects.equals(dittoHeaders, that.dittoHeaders);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, subscriptionId, dittoHeaders);
    }

    @Override
    public String toString() {
        return "type=" + type +
                ", subscriptionId=" + subscriptionId +
                ", dittoHeaders=" + dittoHeaders;
    }

    static final class JsonFields {

        static final JsonFieldDefinition<String> SUBSCRIPTION_ID =
                JsonFactory.newStringFieldDefinition("subscriptionId");
    }

}
