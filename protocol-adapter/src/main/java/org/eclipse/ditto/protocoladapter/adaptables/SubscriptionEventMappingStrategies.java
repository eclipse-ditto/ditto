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
package org.eclipse.ditto.protocoladapter.adaptables;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionComplete;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionCreated;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionFailed;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionHasNext;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing events.
 */
final class SubscriptionEventMappingStrategies extends AbstractSearchMappingStrategies<SubscriptionEvent<?>> {

    private static final SubscriptionEventMappingStrategies INSTANCE = new SubscriptionEventMappingStrategies();

    private SubscriptionEventMappingStrategies() {
        super(initMappingStrategies());
    }

    static SubscriptionEventMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<SubscriptionEvent<?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<SubscriptionEvent<?>>> mappingStrategies = new HashMap<>();
        mappingStrategies.put(SubscriptionCreated.TYPE,
                adaptable -> SubscriptionCreated.of(Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SubscriptionComplete.TYPE,
                adaptable -> SubscriptionComplete.of(Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SubscriptionFailed.TYPE,
                adaptable -> SubscriptionFailed.of(Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        errorFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SubscriptionHasNext.TYPE,
                adaptable -> SubscriptionHasNext.of(Objects.requireNonNull(subscriptionIdFrom(adaptable)),
                        itemsFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    private static JsonArray itemsFrom(final Adaptable adaptable) {
        if (adaptable.getPayload().getValue().isPresent()) {
            final JsonObject value = JsonObject.of(
                    adaptable
                            .getPayload()
                            .getValue()
                            .map(JsonValue::formatAsString)
                            .orElseThrow(() -> JsonParseException.newBuilder().build()));

            return value.getValue("items").map(JsonValue::asArray).orElse(JsonArray.empty());
        }
        return JsonArray.empty();
    }

    private static DittoRuntimeException errorFrom(final Adaptable adaptable) {
        if (adaptable.getPayload().getValue().isPresent()) {
            final JsonObject value = JsonObject.of(
                    adaptable
                            .getPayload()
                            .getValue()
                            .map(JsonValue::formatAsString)
                            .orElseThrow(() -> JsonParseException.newBuilder().build()));

            return DittoRuntimeException.fromUnknownErrorJson(
                    JsonObject.of(value.getValue("error").map(JsonValue::formatAsString)
                            .orElseThrow(() -> JsonParseException.newBuilder().build())), dittoHeadersFrom(adaptable))
                    .orElseThrow(() -> JsonParseException.newBuilder().build());


        }
        return DittoRuntimeException.newBuilder(JsonParseException.newBuilder().build().toString(),
                HttpStatusCode.BAD_REQUEST).build();
    }

}
