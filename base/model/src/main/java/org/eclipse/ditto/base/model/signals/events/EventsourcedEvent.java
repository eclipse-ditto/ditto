/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Base interface for all "eventsources" events implemented in Ditto.
 * Eventsourced events are events which were first applied to a persistence and after that was a success, they are
 * published to subscribers in the Ditto cluster (and as a result also to external subscribers).
 *
 * @param <T> the type of the implementing class.
 * @since 2.0.0
 */
public interface EventsourcedEvent<T extends EventsourcedEvent<T>> extends Event<T>, WithEntityId {

    /**
     * Returns the event's revision.
     *
     * @return the event's revision.
     */
    long getRevision();

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of an eventsourced event.
     */
    class JsonFields {

        /**
         * JSON field containing the event's revision derived from the eventsourcing persistence.
         */
        public static final JsonFieldDefinition<Long> REVISION =
                JsonFactory.newLongFieldDefinition("revision", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
