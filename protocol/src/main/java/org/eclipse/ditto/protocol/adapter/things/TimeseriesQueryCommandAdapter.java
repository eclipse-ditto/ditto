/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.AbstractAdapter;
import org.eclipse.ditto.protocol.adapter.EmptyPathMatcher;
import org.eclipse.ditto.protocol.mapper.SignalMapper;
import org.eclipse.ditto.protocol.mapper.SignalMapperFactory;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategiesFactory;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries;

/**
 * Adapter for mapping a {@link RetrieveTimeseries} command to and from an {@link Adaptable}.
 * <p>
 * Topic path: {@code <ns>/<name>/things/twin/timeseries/retrieve}
 *
 * @since 4.0.0
 */
public final class TimeseriesQueryCommandAdapter extends AbstractAdapter<RetrieveTimeseries> {

    private final SignalMapper<RetrieveTimeseries> signalMapper;

    private TimeseriesQueryCommandAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getTimeseriesQueryCommandMappingStrategies(),
                headerTranslator,
                EmptyPathMatcher.getInstance());
        this.signalMapper = SignalMapperFactory.newTimeseriesQuerySignalMapper();
    }

    /**
     * Returns a new {@code TimeseriesQueryCommandAdapter}.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static TimeseriesQueryCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new TimeseriesQueryCommandAdapter(requireNonNull(headerTranslator, "headerTranslator"));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        return RetrieveTimeseries.TYPE;
    }

    @Override
    protected Adaptable mapSignalToAdaptable(final RetrieveTimeseries signal,
            final TopicPath.Channel channel) {
        return signalMapper.mapSignalToAdaptable(signal, channel);
    }

    @Override
    public TopicPath toTopicPath(final RetrieveTimeseries signal, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToTopicPath(signal, channel);
    }

    @Override
    public Set<TopicPath.Group> getGroups() {
        return EnumSet.of(TopicPath.Group.THINGS);
    }

    @Override
    public Set<TopicPath.Channel> getChannels() {
        return EnumSet.of(TopicPath.Channel.TWIN);
    }

    @Override
    public Set<TopicPath.Criterion> getCriteria() {
        return EnumSet.of(TopicPath.Criterion.TIMESERIES);
    }

    @Override
    public Set<TopicPath.Action> getActions() {
        return EnumSet.of(TopicPath.Action.RETRIEVE);
    }

    @Override
    public boolean isForResponses() {
        return false;
    }

    @Override
    public boolean supportsWildcardTopics() {
        return false;
    }
}
