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
package org.eclipse.ditto.protocol.mapper;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.protocol.UnknownChannelException;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;

final class ThingMergedEventSignalMapper extends AbstractSignalMapper<ThingMerged> {

    @Override
    void enhancePayloadBuilder(final ThingMerged signal, final PayloadBuilder payloadBuilder) {
        payloadBuilder.withRevision(signal.getRevision())
                .withTimestamp(signal.getTimestamp().orElse(null))
                .withValue(signal.getValue());
    }

    @Override
    DittoHeaders enhanceHeaders(final ThingMerged signal) {
        return ProtocolFactory.newHeadersWithJsonMergePatchContentType(signal.getDittoHeaders());
    }

    @Override
    TopicPath getTopicPath(final ThingMerged signal, final TopicPath.Channel channel) {

        TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(signal.getEntityId())
                .things();
        if (TopicPath.Channel.TWIN == channel) {
            topicPathBuilder = topicPathBuilder.twin();
        } else if (TopicPath.Channel.LIVE == channel) {
            topicPathBuilder = topicPathBuilder.live();
        } else {
            throw UnknownChannelException.newBuilder(channel, signal.getType())
                    .dittoHeaders(signal.getDittoHeaders())
                    .build();
        }
        return topicPathBuilder
                .events()
                .merged()
                .build();
    }
}
