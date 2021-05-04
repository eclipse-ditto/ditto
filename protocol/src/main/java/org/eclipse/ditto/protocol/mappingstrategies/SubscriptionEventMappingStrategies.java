/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.protocol.adapter.AbstractErrorResponseAdapter;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.base.model.signals.ErrorRegistry;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionComplete;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionCreated;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionFailed;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionHasNextPage;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing events.
 */
final class SubscriptionEventMappingStrategies extends AbstractSearchMappingStrategies<SubscriptionEvent<?>> {

    private SubscriptionEventMappingStrategies(final ErrorRegistry<?> errorRegistry) {
        super(initMappingStrategies(errorRegistry));
    }

    static SubscriptionEventMappingStrategies getInstance(final ErrorRegistry<?> errorRegistry) {
        return new SubscriptionEventMappingStrategies(errorRegistry);
    }

    private static Map<String, JsonifiableMapper<SubscriptionEvent<?>>> initMappingStrategies(
            final ErrorRegistry<?> errorRegistry) {
        final Map<String, JsonifiableMapper<SubscriptionEvent<?>>> mappingStrategies = new HashMap<>();
        mappingStrategies.put(SubscriptionCreated.TYPE,
                adaptable -> SubscriptionCreated.of(Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SubscriptionComplete.TYPE,
                adaptable -> SubscriptionComplete.of(Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SubscriptionFailed.TYPE,
                adaptable -> SubscriptionFailed.of(Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        errorFrom(adaptable, errorRegistry), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SubscriptionHasNextPage.TYPE,
                adaptable -> SubscriptionHasNextPage.of(Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        itemsFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    private static JsonArray itemsFrom(final Adaptable adaptable) {
        return getFromValue(adaptable, SubscriptionHasNextPage.JsonFields.ITEMS).orElseGet(JsonArray::empty);
    }

    private static DittoRuntimeException errorFrom(final Adaptable adaptable, final ErrorRegistry<?> errorRegistry) {
        return getFromValue(adaptable, SubscriptionFailed.JsonFields.ERROR)
                .map(error ->
                        AbstractErrorResponseAdapter.parseWithErrorRegistry(error, DittoHeaders.empty(), errorRegistry))
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

}
