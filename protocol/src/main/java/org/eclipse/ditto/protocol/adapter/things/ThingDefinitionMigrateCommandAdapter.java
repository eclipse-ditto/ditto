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
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.MigrateThingDefinitionCommandAdapter;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinition;


/**
 * Adapter for mapping a {@link MigrateThingDefinition} to and from an {@link Adaptable}.
 */
final class ThingDefinitionMigrateCommandAdapter extends AbstractThingAdapter<MigrateThingDefinition>
implements MigrateThingDefinitionCommandAdapter {

    private ThingDefinitionMigrateCommandAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getThingDefinitionMigrateCommandMappingStrategies(),
                SignalMapperFactory.newThingDefinitionMigrateSignalMapper(),
                headerTranslator);
    }

    /**
     * Returns a new ThingDefinitionMigrateCommandAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingDefinitionMigrateCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingDefinitionMigrateCommandAdapter(requireNonNull(headerTranslator));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        return MigrateThingDefinition.TYPE;
    }

}
