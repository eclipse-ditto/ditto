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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.signals.commands.streaming.CancelStreamingSubscription;
import org.eclipse.ditto.base.model.signals.commands.streaming.RequestFromStreamingSubscription;
import org.eclipse.ditto.base.model.signals.commands.streaming.StreamingSubscriptionCommand;
import org.eclipse.ditto.base.model.signals.commands.streaming.SubscribeForPersistedEvents;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for streaming subscription commands.
 */
final class StreamingSubscriptionCommandMappingStrategies
        extends AbstractStreamingSubscriptionMappingStrategies<StreamingSubscriptionCommand<?>> {

    private static final StreamingSubscriptionCommandMappingStrategies INSTANCE =
            new StreamingSubscriptionCommandMappingStrategies();

    private StreamingSubscriptionCommandMappingStrategies() {
        super(initMappingStrategies());
    }

    static StreamingSubscriptionCommandMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<StreamingSubscriptionCommand<?>>> initMappingStrategies() {

        final Map<String, JsonifiableMapper<StreamingSubscriptionCommand<?>>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(SubscribeForPersistedEvents.TYPE,
                adaptable -> SubscribeForPersistedEvents.of(entityIdFrom(adaptable),
                        resourcePathFrom(adaptable),
                        fromHistoricalRevision(adaptable),
                        toHistoricalRevision(adaptable),
                        fromHistoricalTimestamp(adaptable),
                        toHistoricalTimestamp(adaptable),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(CancelStreamingSubscription.TYPE,
                adaptable -> CancelStreamingSubscription.of(entityIdFrom(adaptable),
                        resourcePathFrom(adaptable),
                        Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(RequestFromStreamingSubscription.TYPE,
                adaptable -> RequestFromStreamingSubscription.of(entityIdFrom(adaptable),
                        resourcePathFrom(adaptable),
                        Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        demandFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    @Nullable
    private static Long fromHistoricalRevision(final Adaptable adaptable) {
        return getFromValue(adaptable, SubscribeForPersistedEvents.JsonFields.JSON_FROM_HISTORICAL_REVISION)
                .orElse(null);
    }

    @Nullable
    private static Long toHistoricalRevision(final Adaptable adaptable) {
        return getFromValue(adaptable, SubscribeForPersistedEvents.JsonFields.JSON_TO_HISTORICAL_REVISION)
                .orElse(null);
    }

    @Nullable
    private static Instant fromHistoricalTimestamp(final Adaptable adaptable) {
        return getFromValue(adaptable, SubscribeForPersistedEvents.JsonFields.JSON_FROM_HISTORICAL_TIMESTAMP)
                .map(Instant::parse)
                .orElse(null);
    }

    @Nullable
    private static Instant toHistoricalTimestamp(final Adaptable adaptable) {
        return getFromValue(adaptable, SubscribeForPersistedEvents.JsonFields.JSON_TO_HISTORICAL_TIMESTAMP)
                .map(Instant::parse)
                .orElse(null);
    }

    private static long demandFrom(final Adaptable adaptable) {
        return getFromValue(adaptable, RequestFromStreamingSubscription.JsonFields.DEMAND).orElse(0L);
    }

}
