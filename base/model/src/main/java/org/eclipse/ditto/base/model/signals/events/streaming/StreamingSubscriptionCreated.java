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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.signals.WithStreamingSubscriptionId;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;

/**
 * This event is emitted after a stream is established for items to be streamed in the back-end.
 * Corresponds to the reactive-streams signal {@code Subscriber#onSubscribe(Subscription)}.
 *
 * @since 3.2.0
 */
@Immutable
@JsonParsableEvent(name = StreamingSubscriptionCreated.NAME, typePrefix = StreamingSubscriptionEvent.TYPE_PREFIX)
public final class StreamingSubscriptionCreated
        extends AbstractStreamingSubscriptionEvent<StreamingSubscriptionCreated> {

    /**
     * Name of the event.
     */
    public static final String NAME = "created";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private StreamingSubscriptionCreated(final String subscriptionId,
            final EntityId entityId,
            final DittoHeaders dittoHeaders) {
        super(TYPE, subscriptionId, entityId, dittoHeaders);
    }

    /**
     * Constructs a new {@code StreamingSubscriptionCreated} event.
     *
     * @param subscriptionId the subscription ID.
     * @param entityId the entity ID of this streaming subscription event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if either argument is null.
     */
    public static StreamingSubscriptionCreated of(final String subscriptionId,
            final EntityId entityId,
            final DittoHeaders dittoHeaders) {
        return new StreamingSubscriptionCreated(subscriptionId, entityId, dittoHeaders);
    }

    /**
     * Creates a new {@code StreamingSubscriptionCreated} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new StreamingSubscriptionCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code StreamingSubscriptionCreated} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static StreamingSubscriptionCreated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<StreamingSubscriptionCreated>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String subscriptionId = jsonObject
                            .getValueOrThrow(WithStreamingSubscriptionId.JsonFields.SUBSCRIPTION_ID);
                    final EntityId entityId = deserializeEntityId(jsonObject);
                    return new StreamingSubscriptionCreated(subscriptionId, entityId, dittoHeaders);
                });
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public StreamingSubscriptionCreated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new StreamingSubscriptionCreated(getSubscriptionId(), getEntityId(), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder) {
        // nothing to add
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
