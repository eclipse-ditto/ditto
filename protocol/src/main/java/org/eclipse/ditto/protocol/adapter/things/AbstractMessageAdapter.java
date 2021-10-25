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
package org.eclipse.ditto.protocol.adapter.things;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.protocol.MessagePath;
import org.eclipse.ditto.protocol.PayloadPathMatcher;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.AbstractAdapter;
import org.eclipse.ditto.protocol.mapper.SignalMapper;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategies;

abstract class AbstractMessageAdapter<T extends Signal<?>> extends AbstractAdapter<T>
        implements ThingMessageAdapter<T> {

    private final SignalMapper<T> signalMapper;

    AbstractMessageAdapter(
            final MappingStrategies<T> mappingStrategies,
            final SignalMapper<T> signalMapper,
            final HeaderTranslator headerTranslator,
            final PayloadPathMatcher payloadPathMatcher) {

        super(mappingStrategies, headerTranslator, payloadPathMatcher);
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
    public Adaptable validateAndPreprocess(final Adaptable adaptable) {
        // skip validation of message subjects, which are not valid JSON pointers.
        final MessagePath messagePath = adaptable.getPayload().getPath();
        if (messagePath.getFeatureId().isPresent()) {
            final int featurePathPrefixLevel = 4; // /features/<ID>/<inbox|outbox>/messages
            return validateAndPreprocessMessagePathPrefix(adaptable, featurePathPrefixLevel);
        } else {
            return adaptable;
        }
    }
}
