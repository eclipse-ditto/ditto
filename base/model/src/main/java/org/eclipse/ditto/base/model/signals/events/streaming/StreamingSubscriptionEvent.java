/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.events.streaming;

import static org.eclipse.ditto.base.model.json.FieldType.REGULAR;
import static org.eclipse.ditto.base.model.json.JsonSchemaVersion.V_2;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.entity.type.WithEntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;

/**
 * Interface for all outgoing messages related to a subscription for streaming something.
 *
 * @param <T> the type of the implementing class.
 * @since 3.2.0
 */
public interface StreamingSubscriptionEvent<T extends StreamingSubscriptionEvent<T>> extends Event<T>,
        WithEntityId, WithEntityType {

    /**
     * Resource type of streaming subscription events.
     */
    String RESOURCE_TYPE = "streaming.subscription";

    /**
     * Type Prefix of Streaming events.
     */
    String TYPE_PREFIX = RESOURCE_TYPE + "." + TYPE_QUALIFIER + ":";

    /**
     * Returns the subscriptionId identifying the session of this streaming signal.
     *
     * @return the subscriptionId.
     */
    String getSubscriptionId();

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    /**
     * This class contains definitions for all specific fields of this event's JSON representation.
     */
    final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        public static final JsonFieldDefinition<String> JSON_ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", REGULAR, V_2);

        public static final JsonFieldDefinition<String> JSON_ENTITY_TYPE =
                JsonFactory.newStringFieldDefinition("entityType", REGULAR, V_2);

    }

}
