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

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.protocol.PayloadPathMatcher;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.AbstractAdapter;
import org.eclipse.ditto.protocol.mapper.SignalMapper;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategies;

/**
 * Base class for {@link org.eclipse.ditto.protocol.adapter.Adapter}s that handle thing commands.
 *
 * @param <T> the type of the thing commands
 */
abstract class AbstractThingAdapter<T extends Signal<?>> extends AbstractAdapter<T> implements ThingAdapter<T> {

    private final SignalMapper<T> signalMapper;

    /**
     * Constructor.
     *
     * @param mappingStrategies the mapping strategies used to convert from
     * {@link org.eclipse.ditto.protocol.Adaptable}s to {@link org.eclipse.ditto.base.model.signals.Signal}s
     * @param signalMapper the {@link SignalMapper} used to convert from a
     * {@link org.eclipse.ditto.base.model.signals.Signal} to an {@link Adaptable}
     * @param headerTranslator the header translator used for the mapping
     */
    protected AbstractThingAdapter(final MappingStrategies<T> mappingStrategies,
            final SignalMapper<T> signalMapper,
            final HeaderTranslator headerTranslator) {

        this(mappingStrategies, signalMapper, headerTranslator, ThingModifyPathMatcher.getInstance());
    }

    /**
     * Constructor.
     *
     * @param mappingStrategies the mapping strategies used to convert from
     * {@link org.eclipse.ditto.protocol.Adaptable}s to {@link org.eclipse.ditto.base.model.signals.Signal}s
     * @param signalMapper the signal mapper to use for mapping signals to adaptables and to topic paths.
     * @param headerTranslator the header translator used for the mapping
     * @param pathMatcher the path matcher used for the mapping
     */
    protected AbstractThingAdapter(final MappingStrategies<T> mappingStrategies,
            final SignalMapper<T> signalMapper,
            final HeaderTranslator headerTranslator,
            final PayloadPathMatcher pathMatcher) {

        super(mappingStrategies, headerTranslator, pathMatcher);
        this.signalMapper = signalMapper;
    }

    @Override
    protected Adaptable mapSignalToAdaptable(final T signal, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToAdaptable(signal, channel);
    }

    @Override
    public TopicPath toTopicPath(final T signal, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToTopicPath(signal, channel);
    }

}
