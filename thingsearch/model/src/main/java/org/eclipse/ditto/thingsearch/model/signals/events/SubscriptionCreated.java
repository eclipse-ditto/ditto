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
package org.eclipse.ditto.thingsearch.model.signals.events;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;

/**
 * This event is emitted after a stream is established for search results in the back-end.
 * Corresponds to the reactive-streams signal {@code Subscriber#onSubscribe(Subscription)}.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableEvent(name = SubscriptionCreated.NAME, typePrefix = SubscriptionEvent.TYPE_PREFIX)
public final class SubscriptionCreated extends AbstractSubscriptionEvent<SubscriptionCreated> {

    /**
     * Name of the event.
     */
    public static final String NAME = "created";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private SubscriptionCreated(final String subscriptionId, final DittoHeaders dittoHeaders) {
        super(TYPE, subscriptionId, dittoHeaders);
    }

    /**
     * Constructs a new {@code SubscriptionCreated} event.
     *
     * @param subscriptionId the subscription ID.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the event.
     * @throws NullPointerException if either argument is null.
     */
    public static SubscriptionCreated of(final String subscriptionId, final DittoHeaders dittoHeaders) {
        return new SubscriptionCreated(subscriptionId, dittoHeaders);
    }

    /**
     * Creates a new {@code SubscriptionCreated} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubscriptionCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubscriptionCreated} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SubscriptionCreated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<SubscriptionCreated>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String subscriptionId = jsonObject.getValueOrThrow(JsonFields.SUBSCRIPTION_ID);
                    return new SubscriptionCreated(subscriptionId, dittoHeaders);
                });
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public SubscriptionCreated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SubscriptionCreated(getSubscriptionId(), dittoHeaders);
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
