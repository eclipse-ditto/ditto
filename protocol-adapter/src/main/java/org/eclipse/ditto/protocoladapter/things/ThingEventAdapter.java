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
package org.eclipse.ditto.protocoladapter.things;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocoladapter.AbstractAdapter;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.EventAdapter;
import org.eclipse.ditto.protocoladapter.EventsTopicPathBuilder;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Adapter for mapping a {@link ThingEvent} to and from an {@link Adaptable}.
 */
final class ThingEventAdapter extends AbstractThingAdapter<ThingEvent<?>> implements EventAdapter<ThingEvent<?>> {

    private ThingEventAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getThingEventMappingStrategies(), headerTranslator);
    }

    /**
     * Returns a new ThingEventAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingEventAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingEventAdapter(requireNonNull(headerTranslator));
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
        final String eventName = pathMatcher.match(path).getPath() + getActionNameWithFirstLetterUpperCase(topicPath);
        return topicPath.getGroup() + "." + topicPath.getCriterion() + ":" + eventName;
    }

    @Override
    public Adaptable mapSignalToAdaptable(final ThingEvent<?> event, final TopicPath.Channel channel) {
        final EventsTopicPathBuilder eventsTopicPathBuilder = getEventTopicPathBuilderFor(event, channel);
        final PayloadBuilder payloadBuilder = Payload.newBuilder(event.getResourcePath())
                .withRevision(event.getRevision());
        event.getTimestamp().ifPresent(payloadBuilder::withTimestamp);

        final Optional<JsonValue> value =
                event.getEntity(event.getDittoHeaders().getSchemaVersion().orElse(event.getLatestSchemaVersion()));
        value.ifPresent(payloadBuilder::withValue);

        return Adaptable.newBuilder(eventsTopicPathBuilder.build())
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(event.getDittoHeaders()))
                .build();
    }

}
