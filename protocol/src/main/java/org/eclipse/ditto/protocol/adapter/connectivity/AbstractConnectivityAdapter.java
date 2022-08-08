/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter.connectivity;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.AbstractAdapter;
import org.eclipse.ditto.protocol.adapter.EmptyPathMatcher;
import org.eclipse.ditto.protocol.mapper.SignalMapper;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategies;

/**
 * Base class for {@link org.eclipse.ditto.protocol.adapter.Adapter}s that handle connectivity commands.
 *
 * @param <T> the type of the connectivity command
 * @since 2.1.0
 */
abstract class AbstractConnectivityAdapter<T extends Signal<?>> extends AbstractAdapter<T> implements ConnectivityAdapter<T> {

    private final SignalMapper<T> signalMapper;

    /**
     * @param mappingStrategies the {@link MappingStrategies} used to convert {@link Adaptable}s to
     * {@link Signal}s
     * @param signalMapper the {@link SignalMapper} used to convert from a
     * {@link Signal} to an {@link Adaptable}
     * @param headerTranslator the header translator
     */
    protected AbstractConnectivityAdapter(final MappingStrategies<T> mappingStrategies,
            final SignalMapper<T> signalMapper, final HeaderTranslator headerTranslator) {
        super(mappingStrategies, headerTranslator, EmptyPathMatcher.getInstance());
        this.signalMapper = signalMapper;
    }

    @Override
    protected Adaptable mapSignalToAdaptable(final T signal, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToAdaptable(signal, channel);
    }

    @Override
    public Adaptable toAdaptable(final T t) {
        return super.toAdaptable(t, TopicPath.Channel.NONE);
    }

    @Override
    public TopicPath toTopicPath(final T t, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToTopicPath(t, channel);
    }

}
