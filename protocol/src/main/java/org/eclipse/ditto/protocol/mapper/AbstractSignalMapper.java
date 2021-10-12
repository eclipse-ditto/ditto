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
package org.eclipse.ditto.protocol.mapper;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;

/**
 * Base class for all {@link SignalMapper}s. Constructs an {@link Adaptable} with data common to all signals
 * and provides extension points to further customize the {@link Adaptable} in sub classes.
 *
 * @param <T> the type of the signal
 */
abstract class AbstractSignalMapper<T extends Signal<?>> implements SignalMapper<T> {

    @Override
    public Adaptable mapSignalToAdaptable(final T signal, final TopicPath.Channel channel) {

        // optional validation of the signal
        validate(signal, channel);

        final PayloadBuilder payloadBuilder = Payload.newBuilder(signal.getResourcePath());

        // optional enhancement of the payload builder
        enhancePayloadBuilder(signal, payloadBuilder);

        // optional enhancement of signal headers
        final DittoHeaders dittoHeaders = enhanceHeaders(signal);

        return Adaptable.newBuilder(getTopicPath(signal, channel))
                .withPayload(payloadBuilder.build())
                .withHeaders(dittoHeaders)
                .build();
    }

    @Override
    public TopicPath mapSignalToTopicPath(final T signal, final TopicPath.Channel channel) {
        return getTopicPath(checkNotNull(signal, "signal"), checkNotNull(channel, "channel"));
    }

    /**
     * Generates the proper topic path for the given {@link Signal}.
     *
     * @param signal the {@code signal} for which the {@link TopicPathBuilder} should be returned
     * @return the {@link TopicPathBuilder} for the given {@code signal}
     */
    abstract TopicPath getTopicPath(T signal, TopicPath.Channel channel);

    /**
     * Validates the given {@code signal} and throws an exception if it cannot be processed.
     *
     * @param signal the {@code signal} to be validated
     * @param channel the channel to be used
     */
    void validate(final T signal, final TopicPath.Channel channel) {
        // do nothing by default
    }

    /**
     * Extension point that allows to adapt the payload builder that is used to build the payload of the
     * {@link Adaptable}.
     *
     * @param signal the {@code signal} that is processed
     * @param payloadBuilder the {@link PayloadBuilder} that can be enhanced
     */
    void enhancePayloadBuilder(final T signal, final PayloadBuilder payloadBuilder) {
        // do nothing by default
    }

    /**
     * Extension point that allows to adapt the headers of the {@link Adaptable}.
     *
     * @param signal the {@code signal} that is processed
     */
    DittoHeaders enhanceHeaders(final T signal) {
        return ProtocolFactory.newHeadersWithJsonContentType(signal.getDittoHeaders());
    }

}
