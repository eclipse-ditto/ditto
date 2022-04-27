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
package org.eclipse.ditto.protocol.adapter.things;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.ModifyCommandResponseAdapter;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommandResponse;

/**
 * Adapter for mapping a {@link ThingModifyCommandResponse} to and from an {@link Adaptable}.
 */
final class ThingModifyCommandResponseAdapter extends AbstractThingAdapter<ThingModifyCommandResponse<?>>
        implements ModifyCommandResponseAdapter<ThingModifyCommandResponse<?>> {

    private ThingModifyCommandResponseAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getThingModifyCommandResponseMappingStrategies(),
                SignalMapperFactory.newThingModifyResponseSignalMapper(),
                headerTranslator);
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

}
