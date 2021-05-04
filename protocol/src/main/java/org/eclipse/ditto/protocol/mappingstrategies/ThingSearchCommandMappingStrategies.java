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

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CancelSubscription;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CreateSubscription;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.RequestFromSubscription;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing search commands.
 */
final class ThingSearchCommandMappingStrategies extends AbstractSearchMappingStrategies<ThingSearchCommand<?>> {

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

        mappingStrategies.put(RequestFromSubscription.TYPE, adaptable -> RequestFromSubscription.of(
                requireNonNull(subscriptionIdFrom(adaptable)), demandFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    private static Set<String> namespacesFrom(final Adaptable adaptable) {
        return getFromValue(adaptable, CreateSubscription.JsonFields.NAMESPACES)
                .map(array -> array.stream().map(JsonValue::asString).collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }

    @Nullable
    private static String filterFrom(final Adaptable adaptable) {
        return getFromValue(adaptable, CreateSubscription.JsonFields.FILTER).orElse(null);
    }

    @Nullable
    private static String optionsFrom(final Adaptable adaptable) {
        return getFromValue(adaptable, CreateSubscription.JsonFields.OPTIONS).orElse(null);
    }

    private static long demandFrom(final Adaptable adaptable) {
        return getFromValue(adaptable, RequestFromSubscription.JsonFields.DEMAND).orElse(0L);
    }
}
