/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.events.base;

import java.time.Instant;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithOptionalEntity;

/**
 * Base interface for all events emitted by Ditto.
 *
 * @param <T> the type of the implementing class.
 */
public interface Event<T extends Event> extends Signal<T>, WithOptionalEntity {

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
     * Returns the event's revision.
     *
     * @return the event's revision.
     */
    long getRevision();

    /**
     * Return a new immutable copy of this event with the given {@code revision}.
     *
     * @param revision the event's revision.
     * @return the copy of the event with the given revision.
     */
    T setRevision(long revision);

    /**
     * Returns the event's timestamp.
     *
     * @return the timestamp.
     */
    Optional<Instant> getTimestamp();

    /**
     * Returns all non hidden marked fields of this event.
     *
     * @return a JSON object representation of this event including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of an event.
     */
    class JsonFields {

        /**
         * JSON field containing the event's identifier - was used in SchemaVersion 1 instead of "type".
         * @deprecated was replaced by {@link #TYPE} in Schema Version 2
         */
        @Deprecated
        public static final JsonFieldDefinition<String> ID =
                JsonFactory.newStringFieldDefinition("event", FieldType.REGULAR, JsonSchemaVersion.V_1);

        /**
         * JSON field containing the event's type. Always included in new events.
         */
        public static final JsonFieldDefinition<String> TYPE = JsonFactory.newStringFieldDefinition("type");

        /**
         * JSON field containing the event's revision.
         */
        public static final JsonFieldDefinition<Long> REVISION =
                JsonFactory.newLongFieldDefinition("revision", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the event's timestamp.
         */
        public static final JsonFieldDefinition<String> TIMESTAMP =
                JsonFactory.newStringFieldDefinition("_timestamp", FieldType.SPECIAL, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
