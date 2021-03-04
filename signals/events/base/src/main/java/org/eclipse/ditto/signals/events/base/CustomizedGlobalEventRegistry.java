/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.events.base;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;

/**
 * This class is responsible for adding or replacing parse strategies from the {@link GlobalEventRegistry}.
 */
public class CustomizedGlobalEventRegistry extends AbstractJsonParsableRegistry<Event> implements EventRegistry<Event> {

    private final GlobalEventRegistry globalEventRegistry;

    CustomizedGlobalEventRegistry(
            final GlobalEventRegistry globalEventRegistry,
            final Map<String, JsonParsable<Event>> customParseStrategies) {
        super(mergeParseStrategy(globalEventRegistry, customParseStrategies));
        this.globalEventRegistry = globalEventRegistry;
    }

    private static Map<String, JsonParsable<Event>> mergeParseStrategy(final GlobalEventRegistry globalEventRegistry,
            final Map<String, JsonParsable<Event>> customParseStrategies) {
        final Map<String, JsonParsable<Event>> combinedStrategy = new HashMap<>();
        globalEventRegistry.getTypes().forEach(type -> combinedStrategy.put(type, globalEventRegistry));
        combinedStrategy.putAll(customParseStrategies);
        return combinedStrategy;
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return globalEventRegistry.resolveType(jsonObject);
    }
}
