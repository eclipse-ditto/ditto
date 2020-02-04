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
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;

/**
 * Adapter for mapping a {@link ThingModifyCommandResponse} to and from an {@link Adaptable}.
 */
final class ThingModifyCommandResponseAdapter extends AbstractThingAdapter<ThingModifyCommandResponse<?>> {

    private final SignalMapper<ThingModifyCommandResponse<?>>
            signalMapper = SignalMapperFactory.newThingModifyResponseSignalMapper();

    private ThingModifyCommandResponseAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getThingModifyCommandResponseMappingStrategies(), headerTranslator);
    }

    /**
     * Returns a new ThingModifyCommandResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingModifyCommandResponseAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingModifyCommandResponseAdapter(requireNonNull(headerTranslator));
    }

    @Override
    protected String getTypeCriterionAsString(final TopicPath topicPath) {
        return RESPONSES_CRITERION;
    }

    @Override
    protected Adaptable mapSignalToAdaptable(final ThingModifyCommandResponse<?> signal,
            final TopicPath.Channel channel) {
        return signalMapper.mapSignalToAdaptable(signal, channel);
    }


}
