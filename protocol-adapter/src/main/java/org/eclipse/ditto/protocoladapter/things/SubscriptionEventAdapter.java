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
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocoladapter.AbstractAdapter;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.UnknownCommandException;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionComplete;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionCreated;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionFailed;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionHasNext;

/**
 * Adapter for mapping a {@link ThingEvent} to and from an {@link Adaptable}.
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

    private static String getActionNameWithFirstLetterUpperCase(final TopicPath topicPath) {
        return topicPath.getAction()
                .map(TopicPath.Action::toString)
                .map(AbstractAdapter::upperCaseFirst)
                .orElseThrow(() -> new NullPointerException("TopicPath did not contain an Action!"));
    }


    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String eventName = payloadPathMatcher.match(path) + getActionNameWithFirstLetterUpperCase(topicPath);
        return topicPath.getGroup() + "." + topicPath.getCriterion() + ":" + eventName;
    }

    @Override
    protected Adaptable mapSignalToAdaptable(final SubscriptionEvent<?> event, final TopicPath.Channel channel) {

        TopicPath topicPath = null;
        final PayloadBuilder payloadBuilder = Payload.newBuilder();

        final String eventName = event.getClass().getSimpleName().toLowerCase();
        if (eventName.startsWith(TopicPath.SearchAction.GENERATED.toString())) {
            topicPath = TopicPath.fromNamespace("_").things().twin().search().generated().build();
            SubscriptionCreated createdEvent = (SubscriptionCreated) event;
            payloadBuilder.withValue(JsonObject.of(
                    String.format("{\"subscriptionId\": \"%s\"}", createdEvent.getSubscriptionId())));

        } else if (eventName.startsWith(TopicPath.SearchAction.CANCEL.toString())) {
            topicPath = TopicPath.fromNamespace("_").things().twin().search().cancel().build();
            SubscriptionComplete completedEvent = (SubscriptionComplete) event;
            payloadBuilder.withValue(JsonObject.of(
                    String.format("{\"subscriptionId\": \"%s\"}", completedEvent.getSubscriptionId())));

        } else if (eventName.startsWith(TopicPath.SearchAction.FAILED.toString())) {
            topicPath = TopicPath.fromNamespace("_").things().twin().search().failed().build();
            SubscriptionFailed failedEvent = (SubscriptionFailed) event;
            payloadBuilder.withValue(JsonObject.of(
                    String.format("{\"subscriptionId\": \"%s\", \"error\": \"%s\"}",
                            failedEvent.getSubscriptionId(),
                            failedEvent.getError())));

        } else if (eventName.startsWith(TopicPath.SearchAction.HAS_NEXT.toString())) {
            topicPath = TopicPath.fromNamespace("_").things().twin().search().hasNext().build();
            SubscriptionHasNext hasNextEvent = (SubscriptionHasNext) event;
            payloadBuilder.withValue(JsonObject.of(
                    String.format("{\"subscriptionId\": \"%s\", \"items\": \"%s\"}",
                            hasNextEvent.getSubscriptionId(),
                            hasNextEvent.getItems())));

        } else {
            throw UnknownCommandException.newBuilder(eventName).build();
        }


        return Adaptable.newBuilder(topicPath)
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(event.getDittoHeaders()))
                .build();
    }
}


