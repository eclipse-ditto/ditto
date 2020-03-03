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

import org.eclipse.ditto.json.JsonObject;
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
        // TODO: match event types by "instanceOf" or by event.getType(),
        //      instead of by event.getClass.getSimpleName().toLowerCase()
        // TODO: use JsonObjectBuilder instead of JsonObject.of(String.format("{...}", ....)); danger of injection.

        TopicPath topicPath;
        final PayloadBuilder payloadBuilder = Payload.newBuilder(event.getResourcePath());

        final String eventName = event.getClass().getSimpleName().toLowerCase().replace("subscription", "");
        if (eventName.startsWith(TopicPath.SearchAction.GENERATED.toString().toLowerCase())) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().generated().build();
            SubscriptionCreated createdEvent = (SubscriptionCreated) event;
            payloadBuilder.withValue(JsonObject.of(
                    String.format("{\"subscriptionId\": \"%s\"}", createdEvent.getSubscriptionId())));

        } else if (eventName.startsWith(TopicPath.SearchAction.COMPLETE.toString().toLowerCase())) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().complete().build();
            SubscriptionComplete completedEvent = (SubscriptionComplete) event;
            payloadBuilder.withValue(JsonObject.of(
                    String.format("{\"subscriptionId\": \"%s\"}", completedEvent.getSubscriptionId())));

        } else if (eventName.startsWith(TopicPath.SearchAction.FAILED.toString().toLowerCase())) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().failed().build();
            SubscriptionFailed failedEvent = (SubscriptionFailed) event;
            payloadBuilder.withValue(JsonObject.of(
                    String.format("{\"subscriptionId\": \"%s\", \"error\": %s}",
                            failedEvent.getSubscriptionId(),
                            failedEvent.getError().toJson())));

        } else if (eventName.startsWith(TopicPath.SearchAction.HAS_NEXT.toString().toLowerCase())) {
            topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER).things().twin().search().hasNext().build();
            SubscriptionHasNext hasNextEvent = (SubscriptionHasNext) event;
            payloadBuilder.withValue(JsonObject.of(
                    String.format("{\"subscriptionId\": \"%s\", \"items\": %s}",
                            hasNextEvent.getSubscriptionId(),
                            hasNextEvent.getItems())));

        } else {
            throw UnknownEventException.newBuilder(eventName).build();
        }


        return Adaptable.newBuilder(topicPath)
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(event.getDittoHeaders()))
                .build();
    }
}


