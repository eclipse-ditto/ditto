/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter;

import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.mapper.SignalMapper;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategies;

/**
 * Adapter for mapping a "streaming" {@link Signal} to and from an {@link Adaptable}.
 *
 * @param <T> the type of the signals mapped by this adapter.
 */
abstract class AbstractStreamingMessageAdapter<T extends Signal<?>> extends AbstractAdapter<T>
        implements Adapter<T> {

    private final SignalMapper<T> signalMapper;

    AbstractStreamingMessageAdapter(
            final MappingStrategies<T> mappingStrategies,
            final SignalMapper<T> signalMapper,
            final HeaderTranslator headerTranslator) {

        super(mappingStrategies, headerTranslator, EmptyPathMatcher.getInstance());
        this.signalMapper = signalMapper;
    }

    @Override
    protected Adaptable mapSignalToAdaptable(final T signal, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToAdaptable(signal, channel);
    }

    @Override
    public Adaptable toAdaptable(final T t) {
        return toAdaptable(t, TopicPath.Channel.LIVE);
    }

    @Override
    public TopicPath toTopicPath(final T signal, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToTopicPath(signal, channel);
    }

    @Override
    public Set<TopicPath.Group> getGroups() {
        return EnumSet.of(TopicPath.Group.POLICIES, TopicPath.Group.THINGS, TopicPath.Group.CONNECTIONS);
    }

    @Override
    public Set<TopicPath.Channel> getChannels() {
        return EnumSet.of(TopicPath.Channel.NONE, TopicPath.Channel.TWIN);
    }

}
