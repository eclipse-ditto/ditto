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
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.UnknownEventException;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionComplete;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionCreated;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionFailed;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionHasNextPage;

final class SubscriptionEventSignalMapper extends AbstractSignalMapper<SubscriptionEvent<?>> {

    private static final JsonFieldDefinition<String> SUBSCRIPTION_ID = SubscriptionEvent.JsonFields.SUBSCRIPTION_ID;

    @Override
    void enhancePayloadBuilder(final SubscriptionEvent<?> signal, final PayloadBuilder payloadBuilder) {
        final JsonObjectBuilder payloadContentBuilder = JsonFactory.newObjectBuilder();
        if (signal instanceof SubscriptionCreated) {
            SubscriptionCreated createdEvent = (SubscriptionCreated) signal;
            payloadContentBuilder.set(SUBSCRIPTION_ID, createdEvent.getSubscriptionId());

        } else if (signal instanceof SubscriptionComplete) {
            SubscriptionComplete completedEvent = (SubscriptionComplete) signal;
            payloadContentBuilder.set(SUBSCRIPTION_ID, completedEvent.getSubscriptionId());

        } else if (signal instanceof SubscriptionFailed) {
            SubscriptionFailed failedEvent = (SubscriptionFailed) signal;
            payloadContentBuilder
                    .set(SUBSCRIPTION_ID, failedEvent.getSubscriptionId())
                    .set(SubscriptionFailed.JsonFields.ERROR, failedEvent.getError().toJson());

        } else if (signal instanceof SubscriptionHasNextPage) {
            SubscriptionHasNextPage hasNextEvent = (SubscriptionHasNextPage) signal;
            payloadContentBuilder
                    .set(SUBSCRIPTION_ID, hasNextEvent.getSubscriptionId())
                    .set(SubscriptionHasNextPage.JsonFields.ITEMS, hasNextEvent.getItems());

        } else {
            throw UnknownEventException.newBuilder(signal.getClass().getCanonicalName()).build();
        }
        payloadBuilder.withValue(payloadContentBuilder.build());
    }

    @Override
    DittoHeaders enhanceHeaders(final SubscriptionEvent<?> signal) {
        return ProtocolFactory.newHeadersWithJsonContentType(signal.getDittoHeaders());
    }

    @Override
    TopicPath getTopicPath(final SubscriptionEvent<?> signal, final TopicPath.Channel channel) {
        final TopicPath topicPath;
        if (signal instanceof SubscriptionCreated) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().generated().build();

        } else if (signal instanceof SubscriptionComplete) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().complete().build();

        } else if (signal instanceof SubscriptionFailed) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().failed().build();

        } else if (signal instanceof SubscriptionHasNextPage) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().hasNext().build();
        } else {
            throw UnknownEventException.newBuilder(signal.getClass().getCanonicalName()).build();
        }
        return topicPath;
    }

}
