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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.signals.WithStreamingSubscriptionId;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * This event is emitted after the next items to stream are ready.
 * Corresponds to the reactive-streams signal {@code Subscriber#onNext(T)}.
 *
 * @since 3.2.0
 */
@Immutable
@JsonParsableEvent(name = StreamingSubscriptionHasNext.NAME, typePrefix = StreamingSubscriptionEvent.TYPE_PREFIX)
public final class StreamingSubscriptionHasNext
        extends AbstractStreamingSubscriptionEvent<StreamingSubscriptionHasNext> {

    /**
     * Name of the event.
     */
    public static final String NAME = "next";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final JsonValue item;

    private StreamingSubscriptionHasNext(final String subscriptionId,
            final EntityId entityId,
            final JsonValue item,
            final DittoHeaders dittoHeaders) {
        super(TYPE, subscriptionId, entityId, dittoHeaders);
        this.item = item;
    }

    /**
     * Constructs a new {@code SubscriptionHasNext} object.
     *
     * @param subscriptionId the subscription ID.
     * @param entityId the entity ID of this streaming subscription event.
     * @param item the "next" item.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the SubscriptionHasNext created.
     * @throws NullPointerException if either argument is null.
     */
    public static StreamingSubscriptionHasNext of(final String subscriptionId,
            final EntityId entityId,
            final JsonValue item,
            final DittoHeaders dittoHeaders) {
        return new StreamingSubscriptionHasNext(subscriptionId, entityId, item, dittoHeaders);
    }

    /**
     * Creates a new {@code SubscriptionHasNext} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubscriptionHasNext instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubscriptionHasNext} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static StreamingSubscriptionHasNext fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<StreamingSubscriptionHasNext>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String subscriptionId =
                            jsonObject.getValueOrThrow(WithStreamingSubscriptionId.JsonFields.SUBSCRIPTION_ID);
                    final EntityId entityId = deserializeEntityId(jsonObject);
                    final JsonValue item = jsonObject.getValueOrThrow(JsonFields.ITEM);
                    return new StreamingSubscriptionHasNext(subscriptionId, entityId, item, dittoHeaders);
                });
    }

    /**
     * Get the "next" item.
     *
     * @return the next item.
     */
    public JsonValue getItem() {
        return item;
    }

    /**
     * Create a copy of this event with a new item.
     *
     * @param item the new item.
     * @return the copied event with new item.
     */
    public StreamingSubscriptionHasNext setItem(final JsonValue item) {
        return new StreamingSubscriptionHasNext(getSubscriptionId(), getEntityId(), item, getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public StreamingSubscriptionHasNext setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new StreamingSubscriptionHasNext(getSubscriptionId(), getEntityId(), item, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder) {
        jsonObjectBuilder.set(JsonFields.ITEM, item);
    }

    @Override
    public boolean equals(final Object o) {
        // super.equals(o) guarantees getClass() == o.getClass()
        return super.equals(o) && Objects.equals(item, ((StreamingSubscriptionHasNext) o).item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), item);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", item=" + item + "]";
    }

    /**
     * Json fields of this event.
     */
    public static final class JsonFields {

        /**
         * Json field for "next" item.
         */
        public static final JsonFieldDefinition<JsonValue> ITEM =
                JsonFactory.newJsonValueFieldDefinition("item");

        JsonFields() {
            throw new AssertionError();
        }
    }
}
