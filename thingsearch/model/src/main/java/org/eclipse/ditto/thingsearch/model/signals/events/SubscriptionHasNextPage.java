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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;

/**
 * This event is emitted after a page of search result is ready.
 * Corresponds to the reactive-streams signal {@code Subscriber#onNext(T)}.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableEvent(name = SubscriptionHasNextPage.NAME, typePrefix = SubscriptionEvent.TYPE_PREFIX)
public final class SubscriptionHasNextPage extends AbstractSubscriptionEvent<SubscriptionHasNextPage> {

    /**
     * Name of the event.
     */
    public static final String NAME = "next";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final JsonArray items;

    private SubscriptionHasNextPage(final String subscriptionId, final JsonArray items,
            final DittoHeaders dittoHeaders) {
        super(TYPE, subscriptionId, dittoHeaders);
        this.items = items;
    }

    /**
     * Constructs a new {@code SubscriptionHasNext} object.
     *
     * @param subscriptionId the subscription ID.
     * @param items items in the next page of search results.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the SubscriptionHasNext created.
     * @throws NullPointerException if either argument is null.
     */
    public static SubscriptionHasNextPage of(final String subscriptionId,
            final JsonArray items,
            final DittoHeaders dittoHeaders) {
        return new SubscriptionHasNextPage(subscriptionId, items, dittoHeaders);
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
    public static SubscriptionHasNextPage fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<SubscriptionHasNextPage>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String subscriptionId =
                            jsonObject.getValueOrThrow(SubscriptionEvent.JsonFields.SUBSCRIPTION_ID);
                    final JsonArray items = jsonObject.getValueOrThrow(JsonFields.ITEMS);
                    return new SubscriptionHasNextPage(subscriptionId, items, dittoHeaders);
                });
    }

    /**
     * Get the next page of search results.
     *
     * @return the search results as a JSON array.
     */
    public JsonArray getItems() {
        return items;
    }

    /**
     * Create a copy of this event with a new error.
     *
     * @param items the new error.
     * @return the copied event with new error.
     */
    public SubscriptionHasNextPage setItems(final JsonArray items) {
        return new SubscriptionHasNextPage(getSubscriptionId(), items, getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public SubscriptionHasNextPage setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SubscriptionHasNextPage(getSubscriptionId(), items, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder) {
        jsonObjectBuilder.set(JsonFields.ITEMS, items);
    }

    @Override
    public boolean equals(final Object o) {
        // super.equals(o) guarantees getClass() == o.getClass()
        return super.equals(o) && Objects.equals(items, ((SubscriptionHasNextPage) o).items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), items);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", items=" + items + "]";
    }

    /**
     * Json fields of this event.
     */
    public static final class JsonFields {

        /**
         * Json field for results in a page.
         */
        public static final JsonFieldDefinition<JsonArray> ITEMS =
                JsonFactory.newJsonArrayFieldDefinition("items");

        JsonFields() {
            throw new AssertionError();
        }
    }
}
