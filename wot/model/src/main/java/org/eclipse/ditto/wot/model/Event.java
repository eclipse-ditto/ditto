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
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#eventaffordance">WoT TD EventAffordance</a>
 * @since 2.4.0
 */
public interface Event extends Interaction<Event, EventFormElement, EventForms> {

    static Event fromJson(final CharSequence eventName, final JsonObject jsonObject) {
        return new ImmutableEvent(checkNotNull(eventName, "eventName").toString(), jsonObject);
    }

    static Event.Builder newBuilder(final CharSequence eventName) {
        return Event.Builder.newBuilder(eventName);
    }

    static Event.Builder newBuilder(final CharSequence eventName, final JsonObject jsonObject) {
        return Event.Builder.newBuilder(eventName, jsonObject);
    }

    @Override
    default Event.Builder toBuilder() {
        return Event.Builder.newBuilder(getEventName(), toJson());
    }

    String getEventName();

    Optional<SingleDataSchema> getSubscription();

    Optional<SingleDataSchema> getData();

    Optional<SingleDataSchema> getDataResponse();

    Optional<SingleDataSchema> getCancellation();

    interface Builder extends Interaction.Builder<Builder, Event, EventFormElement, EventForms> {

        static Builder newBuilder(final CharSequence eventName) {
            return new MutableEventBuilder(checkNotNull(eventName, "eventName").toString(),
                    JsonObject.newBuilder());
        }

        static Builder newBuilder(final CharSequence eventName, final JsonObject jsonObject) {
            return new MutableEventBuilder(checkNotNull(eventName, "eventName").toString(), jsonObject.toBuilder());
        }

        Builder setSubscription(@Nullable SingleDataSchema subscription);

        Builder setData(@Nullable SingleDataSchema data);

        Builder setDataResponse(@Nullable SingleDataSchema dataResponse);

        Builder setCancellation(@Nullable SingleDataSchema cancellation);

    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of an Event.
     */
    @Immutable
    final class JsonFields {

        public static final JsonFieldDefinition<JsonObject> SUBSCRIPTION = JsonFactory.newJsonObjectFieldDefinition(
                "subscription");

        public static final JsonFieldDefinition<JsonObject> DATA = JsonFactory.newJsonObjectFieldDefinition(
                "data");

        public static final JsonFieldDefinition<JsonObject> DATA_RESPONSE = JsonFactory.newJsonObjectFieldDefinition(
                "dataResponse");

        public static final JsonFieldDefinition<JsonObject> CANCELLATION = JsonFactory.newJsonObjectFieldDefinition(
                "cancellation");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
