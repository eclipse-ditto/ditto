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

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestSubscription;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing search commands.
 */
final class ThingSearchCommandMappingStrategies extends AbstractThingMappingStrategies<ThingSearchCommand<?>> {

    private static final ThingSearchCommandMappingStrategies INSTANCE = new ThingSearchCommandMappingStrategies();

    private ThingSearchCommandMappingStrategies() {
        super(initMappingStrategies());
    }

    public static ThingSearchCommandMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<ThingSearchCommand<?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<ThingSearchCommand<?>>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(CreateSubscription.TYPE,
                adaptable -> CreateSubscription.of(filterFrom(adaptable), optionsFrom(adaptable),
                        selectedFieldsFrom(adaptable), namespacesFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(CancelSubscription.TYPE,
                adaptable -> CancelSubscription.of(requireNonNull(subscriptionIdFrom(adaptable)),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RequestSubscription.TYPE, adaptable -> RequestSubscription.of(
                requireNonNull(subscriptionIdFrom(adaptable)), demandFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;

    }

    private static Set<String> namespacesFrom(final Adaptable adaptable) {
        if ("_".equals(adaptable.getTopicPath().getNamespace())) {
            return Collections.emptySet();
        }
        return new HashSet<>(
                Arrays.asList(adaptable.getTopicPath().getNamespace().split(",")));

    }

    private static @Nullable
    String filterFrom(final Adaptable adaptable) {

        if (adaptable.getPayload().getValue().isPresent()) {
            final JsonObject value = JsonObject.of(
                    adaptable
                            .getPayload()
                            .getValue()
                            .map(JsonValue::formatAsString)
                            .orElseThrow(() -> JsonParseException.newBuilder().build()));

            return value.getValue("filter").map(JsonValue::asString).orElse(null);

        }
        return null;
    }

    private static List<String> optionsFrom(final Adaptable adaptable) {
        if (adaptable.getPayload().getValue().isPresent()) {
            final JsonObject value = JsonObject.of(
                    adaptable
                            .getPayload()
                            .getValue()
                            .map(JsonValue::formatAsString)
                            .orElseThrow(() -> JsonParseException.newBuilder().build()));

            if (value.getValue("options").isPresent()) {
                return Arrays.asList(value.getValue("options")
                        .map(JsonValue::asString)
                        .orElseThrow(() -> JsonParseException.newBuilder().build())
                        .replace(" ", "")
                        .split(","));
            }
        }
        return Collections.emptyList();
    }

    private static @Nullable
    String subscriptionIdFrom(final Adaptable adaptable) {

        if (adaptable.getPayload().getValue().isPresent()) {
            final JsonObject value = JsonObject.of(
                    adaptable
                            .getPayload()
                            .getValue()
                            .map(JsonValue::formatAsString)
                            .orElseThrow(() -> JsonParseException.newBuilder().build()));

            return value.getValue("subscriptionId").map(JsonValue::asString).orElse(null);
        }
        return null;
    }

    protected static long demandFrom(final Adaptable adaptable) {


        if (adaptable.getPayload().getValue().isPresent()) {
            final JsonObject value = JsonObject.of(
                    adaptable
                            .getPayload()
                            .getValue()
                            .map(JsonValue::formatAsString)
                            .orElseThrow(() -> JsonParseException.newBuilder().build()));
            if (value.getValue("demand").isPresent()) {
                return Long.parseLong(value.getValue("demand")
                        .map(JsonValue::asString)
                        .orElseThrow(() -> JsonParseException.newBuilder().build()));
            }
        }
        return 0;
    }

}
