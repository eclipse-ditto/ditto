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
package org.eclipse.ditto.protocol.mapper;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.WithStreamingSubscriptionId;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionComplete;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionCreated;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionEvent;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionFailed;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionHasNext;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityConstants;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.protocol.UnknownEventException;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;

/**
 *  Signal mapper implementation for {@link StreamingSubscriptionEvent}s.
 */
final class StreamingSubscriptionEventSignalMapper extends AbstractSignalMapper<StreamingSubscriptionEvent<?>> {

    private static final JsonFieldDefinition<String> SUBSCRIPTION_ID =
            WithStreamingSubscriptionId.JsonFields.SUBSCRIPTION_ID;

    @Override
    void enhancePayloadBuilder(final StreamingSubscriptionEvent<?> signal, final PayloadBuilder payloadBuilder) {
        final JsonObjectBuilder payloadContentBuilder = JsonFactory.newObjectBuilder();
        if (signal instanceof StreamingSubscriptionCreated) {
            final StreamingSubscriptionCreated createdEvent = (StreamingSubscriptionCreated) signal;
            payloadContentBuilder.set(SUBSCRIPTION_ID, createdEvent.getSubscriptionId());

        } else if (signal instanceof StreamingSubscriptionComplete) {
            final StreamingSubscriptionComplete completedEvent = (StreamingSubscriptionComplete) signal;
            payloadContentBuilder.set(SUBSCRIPTION_ID, completedEvent.getSubscriptionId());

        } else if (signal instanceof StreamingSubscriptionFailed) {
            final StreamingSubscriptionFailed failedEvent = (StreamingSubscriptionFailed) signal;
            payloadContentBuilder
                    .set(SUBSCRIPTION_ID, failedEvent.getSubscriptionId())
                    .set(StreamingSubscriptionFailed.JsonFields.ERROR, failedEvent.getError().toJson());

        } else if (signal instanceof StreamingSubscriptionHasNext) {
            final StreamingSubscriptionHasNext hasNext = (StreamingSubscriptionHasNext) signal;
            payloadContentBuilder
                    .set(SUBSCRIPTION_ID, hasNext.getSubscriptionId())
                    .set(StreamingSubscriptionHasNext.JsonFields.ITEM, hasNext.getItem());

        } else {
            throw UnknownEventException.newBuilder(signal.getClass().getCanonicalName()).build();
        }
        payloadBuilder.withValue(payloadContentBuilder.build());
    }

    @Override
    DittoHeaders enhanceHeaders(final StreamingSubscriptionEvent<?> signal) {
        return ProtocolFactory.newHeadersWithJsonContentType(signal.getDittoHeaders());
    }

    @Override
    TopicPath getTopicPath(final StreamingSubscriptionEvent<?> signal, final TopicPath.Channel channel) {

        final TopicPathBuilder topicPathBuilder;
        if (signal.getEntityType().equals(ThingConstants.ENTITY_TYPE)) {
            topicPathBuilder = TopicPath.newBuilder(ThingId.of(signal.getEntityId())).things().twin();
        } else if (signal.getEntityType().equals(PolicyConstants.ENTITY_TYPE)) {
            topicPathBuilder = TopicPath.newBuilder(PolicyId.of(signal.getEntityId())).policies().none();
        } else if (signal.getEntityType().equals(ConnectivityConstants.ENTITY_TYPE)) {
            topicPathBuilder = TopicPath.newBuilder(ConnectionId.of(signal.getEntityId())).connections().none();
        } else {
            throw UnknownEventException.newBuilder(signal.getClass().getCanonicalName()).build();
        }

        final TopicPath topicPath;
        if (signal instanceof StreamingSubscriptionCreated) {
            topicPath = topicPathBuilder.streaming().generated().build();
        } else if (signal instanceof StreamingSubscriptionComplete) {
            topicPath = topicPathBuilder.streaming().complete().build();
        } else if (signal instanceof StreamingSubscriptionFailed) {
            topicPath = topicPathBuilder.streaming().failed().build();
        } else if (signal instanceof StreamingSubscriptionHasNext) {
            topicPath = topicPathBuilder.streaming().hasNext().build();
        } else {
            throw UnknownEventException.newBuilder(signal.getClass().getCanonicalName()).build();
        }
        return topicPath;
    }

}
