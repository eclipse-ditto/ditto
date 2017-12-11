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
package org.eclipse.ditto.signals.events.amqpbridge;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Interface for all {@link AmqpConnection} related events.
 *
 * @param <T> the type of the implementing class.
 */
public interface AmqpBridgeEvent<T extends AmqpBridgeEvent> extends Event<T> {

    /**
     * Type Prefix of AmqpBridge events.
     */
    String TYPE_PREFIX = "amqp.bridge." + TYPE_QUALIFIER + ":";

    /**
     * AmqpBridge resource type.
     */
    String RESOURCE_TYPE = "amqp.bridge";

    /**
     * Returns the identifier of the modified {@code Connection}.
     *
     * @return the identifier.
     */
    String getConnectionId();

    @Override
    default String getId() {
        return getConnectionId();
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonFactory.emptyPointer();
    }

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    /**
     * An {@code AmqpBridgeEvent} doesn't have a revision. Thus this implementation always throws an {@code
     * UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException if invoked.
     */
    @Override
    default long getRevision() {
        throw new UnsupportedOperationException("An AmqpBridgeEvent doesn't have a revision!");
    }

    /**
     * An {@code AmqpBridgeEvent} doesn't have a revision. Thus this implementation always throws an {@code
     * UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException if invoked.
     */
    @Override
    default T setRevision(final long revision) {
        throw new UnsupportedOperationException("An AmqpBridgeEvent doesn't have a revision!");
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of an event.
     */
    @Immutable
    final class JsonFields {

        /**
         * Payload JSON field containing the Connection ID.
         */
        public static final JsonFieldDefinition<String> CONNECTION_ID =
                JsonFactory.newStringFieldDefinition("connectionId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
