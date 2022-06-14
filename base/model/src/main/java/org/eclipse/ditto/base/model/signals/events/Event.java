/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.events;

import java.time.Instant;
import java.util.Optional;

import javax.annotation.Nullable;

import org.atteo.classindex.IndexSubclasses;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.WithType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * Base interface for all events emitted by Ditto.
 *
 * @param <T> the type of the implementing class.
 */
@IndexSubclasses
public interface Event<T extends Event<T>> extends Signal<T>, WithOptionalEntity {

    /**
     * Type qualifier of events.
     */
    String TYPE_QUALIFIER = "events";

    /**
     * Revision of not yet persisted events.
     */
    long DEFAULT_REVISION = 0;

    /**
     * Returns the type of this event.
     *
     * @return the type.
     */
    @Override
    String getType();

    @Override
    default JsonSchemaVersion getImplementedSchemaVersion() {
        return getDittoHeaders().getSchemaVersion().orElse(getLatestSchemaVersion());
    }

    /**
     * Returns the event's timestamp.
     *
     * @return the timestamp.
     */
    Optional<Instant> getTimestamp();

    /**
     * Returns the event's metadata.
     *
     * @return the metadata.
     * @since 1.3.0
     */
    Optional<Metadata> getMetadata();

    /**
     * Returns all non-hidden marked fields of this event.
     *
     * @return a JSON object representation of this event including only non-hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    /**
     * Indicates whether the specified signal argument is a live event.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is an instance of {@link Event} with channel
     * {@value CHANNEL_LIVE} in its headers, {@code false} else.
     * @since 3.0.0
     */
    static boolean isLiveEvent(@Nullable final Signal<?> signal) {
        return Event.isEvent(signal) && Signal.isChannelLive(signal);
    }

    /**
     * Indicates whether the specified signal argument is an instance of {@code Event}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is an instance of {@link Event}, {@code false} else.
     * @since 3.0.0
     */
    static boolean isEvent(@Nullable final Signal<?> signal) {
        return signal instanceof Event;
    }

    /**
     * Indicates whether the specified signal argument is a {@code ThingEvent} without requiring a direct dependency
     * to the things-model.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code ThingEvent}, {@code false} else.
     * @since 3.0.0
     */
    static boolean isThingEvent(@Nullable final WithType signal) {
        return WithType.hasTypePrefix(signal, WithType.THINGS_EVENTS_PREFIX);
    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of an event.
     */
    class JsonFields {

        /**
         * JSON field containing the event's type. Always included in new events.
         */
        public static final JsonFieldDefinition<String> TYPE = JsonFactory.newStringFieldDefinition("type");

        /**
         * JSON field containing the event's timestamp.
         */
        public static final JsonFieldDefinition<String> TIMESTAMP =
                JsonFactory.newStringFieldDefinition("_timestamp", FieldType.SPECIAL,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the event's metadata.
         *
         * @since 1.3.0
         */
        public static final JsonFieldDefinition<JsonObject> METADATA =
                JsonFactory.newJsonObjectFieldDefinition("_metadata", FieldType.SPECIAL,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
