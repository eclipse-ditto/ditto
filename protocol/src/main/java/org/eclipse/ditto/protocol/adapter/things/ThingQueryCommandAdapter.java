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
import org.eclipse.ditto.protocol.adapter.QueryCommandAdapter;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;

/**
 * Adapter for mapping a {@link ThingQueryCommand} to and from an {@link Adaptable}.
 */
final class ThingQueryCommandAdapter extends AbstractThingAdapter<ThingQueryCommand<?>>
        implements QueryCommandAdapter<ThingQueryCommand<?>> {

    private ThingQueryCommandAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getThingQueryCommandMappingStrategies(),
                SignalMapperFactory.newThingQuerySignalMapper(),
                headerTranslator);
    }

    /**
     * Returns a new ThingQueryCommandAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingQueryCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingQueryCommandAdapter(requireNonNull(headerTranslator));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        if (topicPath.isWildcardTopic()) {
            return RetrieveThings.TYPE;
        } else {
            // use default for none wildcard topics
            return super.getType(adaptable);
        }
    }

}
