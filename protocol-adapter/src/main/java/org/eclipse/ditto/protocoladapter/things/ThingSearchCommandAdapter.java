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
package org.eclipse.ditto.protocoladapter.things;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
import org.eclipse.ditto.protocoladapter.signals.SignalMapper;
import org.eclipse.ditto.protocoladapter.signals.SignalMapperFactory;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;

/**
 * Adapter for mapping a {@link org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand} to and from
 * an {@link org.eclipse.ditto.protocoladapter.Adaptable}.
 *
 * @since 1.2.0
 */
public class ThingSearchCommandAdapter extends AbstractThingAdapter<ThingSearchCommand<?>> {

    private SignalMapper<ThingSearchCommand<?>> signalMapper = SignalMapperFactory.newThingSearchSignalMapper();

    private ThingSearchCommandAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getThingSearchCommandMappingStrategies(), headerTranslator);
    }

    /**
     * Returns a new ThingSearchCommandAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingSearchCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingSearchCommandAdapter(requireNonNull(headerTranslator));
    }

    @Override
    public Adaptable mapSignalToAdaptable(final ThingSearchCommand<?> command, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToAdaptable(command, channel);
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        return ThingSearchCommand.TYPE_PREFIX + adaptable.getTopicPath().getSearchAction().orElse(null);
    }

    @Override
    public Set<TopicPath.Criterion> getCriteria() {
        return EnumSet.of(TopicPath.Criterion.SEARCH);
    }

    @Override
    public Set<TopicPath.Action> getActions() {
        return Collections.emptySet();
    }

    @Override
    public boolean isForResponses() {
        return false;
    }

    @Override
    public Set<TopicPath.SearchAction> getSearchActions() {
        return EnumSet.of(TopicPath.SearchAction.SUBSCRIBE, TopicPath.SearchAction.REQUEST,
                TopicPath.SearchAction.CANCEL);
    }
}
