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
 * This event is emitted after all pages of a search subscription are sent.
 * Corresponds to the reactive-streams signal {@code Subscriber#onComplete()}.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableEvent(name = SubscriptionComplete.NAME, typePrefix = SubscriptionEvent.TYPE_PREFIX)
public final class SubscriptionComplete extends AbstractSubscriptionEvent<SubscriptionComplete> {

    /**
     * Name of the event.
     */
    public static final String NAME = "complete";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private SubscriptionComplete(final String subscriptionId, final DittoHeaders dittoHeaders) {
        super(TYPE, subscriptionId, dittoHeaders);
    }

    /**
     * Constructs a new {@code SubscriptionComplete} object.
     *
     * @param subscriptionId the subscription ID.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the SubscriptionComplete created.
     * @throws NullPointerException if either argument is null.
     */
    public static SubscriptionComplete of(final String subscriptionId, final DittoHeaders dittoHeaders) {
        return new SubscriptionComplete(subscriptionId, dittoHeaders);
    }

    /**
     * Creates a new {@code SubscriptionComplete} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubscriptionComplete instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubscriptionComplete} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SubscriptionComplete fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<SubscriptionComplete>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String subscriptionId = jsonObject.getValueOrThrow(JsonFields.SUBSCRIPTION_ID);
                    return new SubscriptionComplete(subscriptionId, dittoHeaders);
                });
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public SubscriptionComplete setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SubscriptionComplete(getSubscriptionId(), dittoHeaders);
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
