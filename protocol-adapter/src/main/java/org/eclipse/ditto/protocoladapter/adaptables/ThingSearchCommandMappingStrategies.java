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
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestSubscription;

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

        mappingStrategies.put(RequestSubscription.TYPE, adaptable -> RequestSubscription.of(
                requireNonNull(subscriptionIdFrom(adaptable)), demandFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    private static Set<String> namespacesFrom(final Adaptable adaptable) {
        if (TopicPath.ID_PLACEHOLDER.equals(adaptable.getTopicPath().getNamespace())) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.asList(adaptable.getTopicPath().getNamespace().split(",")));
    }

    @Nullable
    private static String filterFrom(final Adaptable adaptable) {
        return getFromValue(adaptable, CreateSubscription.JsonFields.FILTER).orElse(null);
    }

    private static List<String> optionsFrom(final Adaptable adaptable) {
        // TODO: convert OPTIONS to String type, then use getFromValue
        return adaptable.getPayload()
                .getValue()
                .flatMap(value -> value.asObject().getValue(CreateSubscription.JsonFields.OPTIONS.getPointer()))
                .map(options -> Collections.singletonList(options.asString()))
                .orElse(Collections.emptyList());
    }

    private static long demandFrom(final Adaptable adaptable) {
        return getFromValue(adaptable, RequestSubscription.JsonFields.DEMAND).orElse(0L);
    }
}
