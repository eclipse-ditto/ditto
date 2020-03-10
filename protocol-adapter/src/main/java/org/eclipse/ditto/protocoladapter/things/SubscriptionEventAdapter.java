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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.UnknownEventException;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionComplete;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionCreated;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionFailed;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionHasNext;

/**
 * Adapter for mapping a {@link org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent} to and from an {@link Adaptable}.
 */
final class SubscriptionEventAdapter extends AbstractThingAdapter<SubscriptionEvent<?>> {

    private SubscriptionEventAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getSubscriptionEventMappingStrategies(), headerTranslator);
    }

    /**
     * Returns a new ThingEventAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static SubscriptionEventAdapter of(final HeaderTranslator headerTranslator) {
        return new SubscriptionEventAdapter(requireNonNull(headerTranslator));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        return SubscriptionEvent.TYPE_PREFIX + adaptable.getTopicPath().getSearchAction().orElse(null);
    }

    @Override
    protected Adaptable mapSignalToAdaptable(final SubscriptionEvent<?> event, final TopicPath.Channel channel) {
        TopicPath topicPath;
        final PayloadBuilder payloadBuilder = Payload.newBuilder(event.getResourcePath());
        final JsonObjectBuilder payloadContentBuilder = JsonFactory.newObjectBuilder();

        final String eventName = event.getClass().getSimpleName().toLowerCase().replace("subscription", "");
        if (event instanceof SubscriptionCreated) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().generated().build();
            SubscriptionCreated createdEvent = (SubscriptionCreated) event;
            payloadContentBuilder.set("subscriptionId", createdEvent.getSubscriptionId());

        } else if (event instanceof SubscriptionComplete) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().complete().build();
            SubscriptionComplete completedEvent = (SubscriptionComplete) event;
            payloadContentBuilder.set("subscriptionId", completedEvent.getSubscriptionId());

        } else if (event instanceof SubscriptionFailed) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().failed().build();
            SubscriptionFailed failedEvent = (SubscriptionFailed) event;
            payloadContentBuilder
                    .set("subscriptionId", failedEvent.getSubscriptionId())
                    .set("error", failedEvent.getError().toJson());

        } else if (event instanceof SubscriptionHasNext) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().hasNext().build();
            SubscriptionHasNext hasNextEvent = (SubscriptionHasNext) event;
            payloadContentBuilder
                    .set("subscriptionId", hasNextEvent.getSubscriptionId())
                    .set("items", hasNextEvent.getItems());

        } else {
            throw UnknownEventException.newBuilder(eventName).build();
        }

        return Adaptable.newBuilder(topicPath)
                .withPayload(payloadBuilder.withValue(payloadContentBuilder.build()).build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(event.getDittoHeaders()))
                .build();
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
        return EnumSet.of(TopicPath.SearchAction.COMPLETE, TopicPath.SearchAction.HAS_NEXT,
                TopicPath.SearchAction.FAILED, TopicPath.SearchAction.GENERATED);
    }
}
