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
package org.eclipse.ditto.base.model.signals.commands.streaming;

import static org.eclipse.ditto.base.model.json.FieldType.REGULAR;
import static org.eclipse.ditto.base.model.json.JsonSchemaVersion.V_2;

import org.eclipse.ditto.base.model.entity.type.WithEntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.WithResource;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;

/**
 * Aggregates all {@link Command}s which request a stream (e.g. a {@code SourceRef}) of
 * {@link org.eclipse.ditto.base.model.signals.Signal}s to subscribe for.
 *
 * @param <T> the type of the implementing class.
 * @since 3.2.0
 */
public interface StreamingSubscriptionCommand<T extends StreamingSubscriptionCommand<T>> extends Command<T>,
        WithEntityType, SignalWithEntityId<T>, WithResource {

    /**
     * Resource type of streaming subscription commands.
     */
    String RESOURCE_TYPE = "streaming.subscription";

    /**
     * Type Prefix of Streaming commands.
     */
    String TYPE_PREFIX = RESOURCE_TYPE + "." + TYPE_QUALIFIER + ":";

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * This class contains definitions for all specific fields of this command's JSON representation.
     */
    final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        public static final JsonFieldDefinition<String> JSON_ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", REGULAR, V_2);

        public static final JsonFieldDefinition<String> JSON_ENTITY_TYPE =
                JsonFactory.newStringFieldDefinition("entityType", REGULAR, V_2);

        public static final JsonFieldDefinition<String> JSON_RESOURCE_PATH =
                JsonFactory.newStringFieldDefinition("resourcePath", REGULAR, V_2);

    }
}
