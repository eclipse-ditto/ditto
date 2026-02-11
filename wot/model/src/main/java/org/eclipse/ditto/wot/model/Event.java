/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * An Event is an {@link Interaction} describing an event source which can push events to consumers.
 * <p>
 * Events enable a mechanism for Things to asynchronously notify Consumers about changes or occurrences.
 * Unlike Properties that are polled, Events are pushed from the Thing to subscribed Consumers.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#eventaffordance">WoT TD EventAffordance</a>
 * @since 2.4.0
 */
public interface Event extends Interaction<Event, EventFormElement, EventForms> {

    /**
     * Creates a new Event from the specified JSON object.
     *
     * @param eventName the name of the event (the key in the events map).
     * @param jsonObject the JSON object representing the event affordance.
     * @return the Event.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Event fromJson(final CharSequence eventName, final JsonObject jsonObject) {
        return new ImmutableEvent(checkNotNull(eventName, "eventName").toString(), jsonObject);
    }

    /**
     * Creates a new builder for building an Event.
     *
     * @param eventName the name of the event.
     * @return the builder.
     * @throws NullPointerException if {@code eventName} is {@code null}.
     */
    static Event.Builder newBuilder(final CharSequence eventName) {
        return Event.Builder.newBuilder(eventName);
    }

    /**
     * Creates a new builder for building an Event, initialized with the values from the specified JSON object.
     *
     * @param eventName the name of the event.
     * @param jsonObject the JSON object providing initial values.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Event.Builder newBuilder(final CharSequence eventName, final JsonObject jsonObject) {
        return Event.Builder.newBuilder(eventName, jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for building an Event, initialized with the values
     * of this instance.
     *
     * @return the builder.
     */
    @Override
    default Event.Builder toBuilder() {
        return Event.Builder.newBuilder(getEventName(), toJson());
    }

    /**
     * Returns the name of this event as defined in the Thing Description's events map.
     *
     * @return the event name.
     */
    String getEventName();

    /**
     * Returns the optional data schema describing the data that a Consumer sends to subscribe to the event.
     *
     * @return the optional subscription schema.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#eventaffordance">WoT TD EventAffordance (subscription)</a>
     */
    Optional<SingleDataSchema> getSubscription();

    /**
     * Returns the optional data schema describing the data that the Thing sends when the event occurs.
     *
     * @return the optional event data schema.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#eventaffordance">WoT TD EventAffordance (data)</a>
     */
    Optional<SingleDataSchema> getData();

    /**
     * Returns the optional data schema describing the data that the Thing sends as a response to an event subscription.
     *
     * @return the optional data response schema.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#eventaffordance">WoT TD EventAffordance (dataResponse)</a>
     */
    Optional<SingleDataSchema> getDataResponse();

    /**
     * Returns the optional data schema describing the data that a Consumer sends to cancel an event subscription.
     *
     * @return the optional cancellation schema.
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#eventaffordance">WoT TD EventAffordance (cancellation)</a>
     */
    Optional<SingleDataSchema> getCancellation();

    /**
     * A mutable builder with a fluent API for building an {@link Event}.
     */
    interface Builder extends Interaction.Builder<Builder, Event, EventFormElement, EventForms> {

        /**
         * Creates a new builder for building an Event.
         *
         * @param eventName the name of the event.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence eventName) {
            return new MutableEventBuilder(checkNotNull(eventName, "eventName").toString(),
                    JsonObject.newBuilder());
        }

        /**
         * Creates a new builder for building an Event, initialized with the values from the specified JSON object.
         *
         * @param eventName the name of the event.
         * @param jsonObject the JSON object providing initial values.
         * @return the builder.
         */
        static Builder newBuilder(final CharSequence eventName, final JsonObject jsonObject) {
            return new MutableEventBuilder(checkNotNull(eventName, "eventName").toString(), jsonObject.toBuilder());
        }

        /**
         * Sets the subscription data schema for this event.
         *
         * @param subscription the subscription schema, or {@code null} to remove.
         * @return this builder.
         * @see <a href="https://www.w3.org/TR/wot-thing-description11/#eventaffordance">WoT TD EventAffordance (subscription)</a>
         */
        Builder setSubscription(@Nullable SingleDataSchema subscription);

        /**
         * Sets the event data schema describing the data sent when the event occurs.
         *
         * @param data the event data schema, or {@code null} to remove.
         * @return this builder.
         * @see <a href="https://www.w3.org/TR/wot-thing-description11/#eventaffordance">WoT TD EventAffordance (data)</a>
         */
        Builder setData(@Nullable SingleDataSchema data);

        /**
         * Sets the data response schema for this event.
         *
         * @param dataResponse the data response schema, or {@code null} to remove.
         * @return this builder.
         * @see <a href="https://www.w3.org/TR/wot-thing-description11/#eventaffordance">WoT TD EventAffordance (dataResponse)</a>
         */
        Builder setDataResponse(@Nullable SingleDataSchema dataResponse);

        /**
         * Sets the cancellation data schema for this event.
         *
         * @param cancellation the cancellation schema, or {@code null} to remove.
         * @return this builder.
         * @see <a href="https://www.w3.org/TR/wot-thing-description11/#eventaffordance">WoT TD EventAffordance (cancellation)</a>
         */
        Builder setCancellation(@Nullable SingleDataSchema cancellation);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an Event.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field definition for the subscription data schema.
         */
        public static final JsonFieldDefinition<JsonObject> SUBSCRIPTION = JsonFactory.newJsonObjectFieldDefinition(
                "subscription");

        /**
         * JSON field definition for the event data schema.
         */
        public static final JsonFieldDefinition<JsonObject> DATA = JsonFactory.newJsonObjectFieldDefinition(
                "data");

        /**
         * JSON field definition for the data response schema.
         */
        public static final JsonFieldDefinition<JsonObject> DATA_RESPONSE = JsonFactory.newJsonObjectFieldDefinition(
                "dataResponse");

        /**
         * JSON field definition for the cancellation data schema.
         */
        public static final JsonFieldDefinition<JsonObject> CANCELLATION = JsonFactory.newJsonObjectFieldDefinition(
                "cancellation");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
