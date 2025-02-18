/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter.things;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.MigratedEventAdapter;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionMigrated;

/**
 * Adapter for mapping a {@link ThingDefinitionMigrated} to and from an
 * {@link org.eclipse.ditto.protocol.Adaptable}.
 */
final class ThingDefinitionMigratedEventAdapter extends AbstractThingAdapter<ThingDefinitionMigrated> implements MigratedEventAdapter {

    private ThingDefinitionMigratedEventAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getThingDefinitionMigratedEventMappingStrategies(),
                SignalMapperFactory.newThingDefinitionMigratedEventSignalMapper(),
                headerTranslator);
    }

    /**
     * Returns a new ThingDefinitionMigratedEventAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingDefinitionMigratedEventAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingDefinitionMigratedEventAdapter(requireNonNull(headerTranslator));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final JsonPointer path = adaptable.getPayload().getPath();
        return payloadPathMatcher.match(path);
    }
}
