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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.ErrorRegistry;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionComplete;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionCreated;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionEvent;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionFailed;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionHasNext;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.protocol.adapter.AbstractErrorResponseAdapter;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for streaming subscription events.
 */
final class StreamingSubscriptionEventMappingStrategies
        extends AbstractStreamingSubscriptionMappingStrategies<StreamingSubscriptionEvent<?>> {

    private StreamingSubscriptionEventMappingStrategies(final ErrorRegistry<?> errorRegistry) {
        super(initMappingStrategies(errorRegistry));
    }

    static StreamingSubscriptionEventMappingStrategies getInstance(final ErrorRegistry<?> errorRegistry) {
        return new StreamingSubscriptionEventMappingStrategies(errorRegistry);
    }

    private static Map<String, JsonifiableMapper<StreamingSubscriptionEvent<?>>> initMappingStrategies(
            final ErrorRegistry<?> errorRegistry) {

        final Map<String, JsonifiableMapper<StreamingSubscriptionEvent<?>>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(StreamingSubscriptionCreated.TYPE,
                adaptable -> StreamingSubscriptionCreated.of(Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        entityIdFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(StreamingSubscriptionComplete.TYPE,
                adaptable -> StreamingSubscriptionComplete.of(Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        entityIdFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(StreamingSubscriptionFailed.TYPE,
                adaptable -> StreamingSubscriptionFailed.of(Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        entityIdFrom(adaptable), errorFrom(adaptable, errorRegistry), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(StreamingSubscriptionHasNext.TYPE,
                adaptable -> StreamingSubscriptionHasNext.of(Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        entityIdFrom(adaptable),itemFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    private static JsonValue itemFrom(final Adaptable adaptable) {
        return getFromValue(adaptable, StreamingSubscriptionHasNext.JsonFields.ITEM).orElseGet(JsonValue::nullLiteral);
    }

    private static DittoRuntimeException errorFrom(final Adaptable adaptable, final ErrorRegistry<?> errorRegistry) {
        return getFromValue(adaptable, StreamingSubscriptionFailed.JsonFields.ERROR)
                .map(error ->
                        AbstractErrorResponseAdapter.parseWithErrorRegistry(error, DittoHeaders.empty(), errorRegistry))
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

}
