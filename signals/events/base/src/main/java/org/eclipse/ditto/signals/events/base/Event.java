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
     * External suffix for events.
     */
    String EXTERNAL = "external";

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
     *
     */
    class JsonFields {

        /**
         * JSON field containing the event's identifier - was used in SchemaVersion 1 instead of "type".
         */
        public static final JsonFieldDefinition ID =
                JsonFactory.newFieldDefinition("event", String.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_1);

        /**
         * JSON field containing the event's type.
         */
        public static final JsonFieldDefinition TYPE =
                JsonFactory.newFieldDefinition("type", String.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the event's revision.
         */
        public static final JsonFieldDefinition REVISION =
                JsonFactory.newFieldDefinition("revision", long.class, FieldType.REGULAR,
                        // available in schema versions:
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the event's timestamp.
         */
        public static final JsonFieldDefinition TIMESTAMP =
                JsonFactory.newFieldDefinition("_timestamp", String.class, FieldType.SPECIAL,
                        // available in schema versions:
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }

}
