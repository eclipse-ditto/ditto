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
package org.eclipse.ditto.protocoladapter.things;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
import org.eclipse.ditto.protocoladapter.signals.SignalMapper;
import org.eclipse.ditto.protocoladapter.signals.SignalMapperFactory;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * Adapter for mapping a {@link ThingModifyCommand} to and from an {@link Adaptable}.
 */
final class ThingModifyCommandAdapter extends AbstractThingAdapter<ThingModifyCommand<?>> {

    private final SignalMapper<ThingModifyCommand<?>> signalMapper =
            SignalMapperFactory.newThingModifySignalMapper();

    private ThingModifyCommandAdapter(
            final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getThingModifyCommandMappingStrategies(), headerTranslator);
    }

    /**
     * Returns a new ThingModifyCommandAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingModifyCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingModifyCommandAdapter(requireNonNull(headerTranslator));
    }

    @Override
    public Adaptable mapSignalToAdaptable(final ThingModifyCommand<?> command, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToAdaptable(command, channel);
    }
}
