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
import org.eclipse.ditto.protocoladapter.QueryCommandResponseAdapter;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
import org.eclipse.ditto.protocoladapter.signals.SignalMapper;
import org.eclipse.ditto.protocoladapter.signals.SignalMapperFactory;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;

/**
 * Adapter for mapping a {@link ThingQueryCommandResponse} to and from an {@link Adaptable}.
 */
final class ThingQueryCommandResponseAdapter extends AbstractThingAdapter<ThingQueryCommandResponse<?>>
        implements QueryCommandResponseAdapter<ThingQueryCommandResponse<?>> {


    private final SignalMapper<ThingQueryCommandResponse<?>>
            thingQueryResponseSignalMapper =
            SignalMapperFactory.newThingQueryResponseSignalMapper();

    private ThingQueryCommandResponseAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getThingQueryCommandResponseMappingStrategies(), headerTranslator);
    }

    /**
     * Returns a new ThingQueryCommandResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingQueryCommandResponseAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingQueryCommandResponseAdapter(requireNonNull(headerTranslator));
    }

    protected String getTypeCriterionAsString(final TopicPath topicPath) {
        return RESPONSES_CRITERION;
    }

    @Override
    public Adaptable mapSignalToAdaptable(final ThingQueryCommandResponse<?> commandResponse,
            final TopicPath.Channel channel) {
        return thingQueryResponseSignalMapper.mapSignalToAdaptable(commandResponse, channel);

    }
}
