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
package org.eclipse.ditto.base.model.signals.events;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.signals.AbstractJsonParsableRegistry;
import org.eclipse.ditto.base.model.signals.JsonParsable;

/**
 * This class is responsible for adding or replacing parse strategies from the {@link GlobalEventRegistry}.
 */
public class CustomizedGlobalEventRegistry<T extends Event<?>> extends AbstractJsonParsableRegistry<T>
        implements EventRegistry<T> {

    private final GlobalEventRegistry<T> globalEventRegistry;

    CustomizedGlobalEventRegistry(
            final GlobalEventRegistry<T> globalEventRegistry,
            final Map<String, JsonParsable<T>> customParseStrategies) {
        super(mergeParseStrategy(globalEventRegistry, customParseStrategies));
        this.globalEventRegistry = globalEventRegistry;
    }

    private static <T extends Event<?>> Map<String, JsonParsable<T>> mergeParseStrategy(
            final GlobalEventRegistry<T> globalEventRegistry,
            final Map<String, JsonParsable<T>> customParseStrategies) {
        final Map<String, JsonParsable<T>> combinedStrategy = new HashMap<>();
        globalEventRegistry.getTypes().forEach(type -> combinedStrategy.put(type, globalEventRegistry));
        combinedStrategy.putAll(customParseStrategies);
        return combinedStrategy;
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return globalEventRegistry.resolveType(jsonObject);
    }
}
