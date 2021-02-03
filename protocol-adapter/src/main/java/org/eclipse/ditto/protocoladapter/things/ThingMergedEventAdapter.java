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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.EventsTopicPathBuilder;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.MergedEventAdapter;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
import org.eclipse.ditto.signals.events.things.ThingMerged;

/**
 * Adapter for mapping a {@link org.eclipse.ditto.signals.events.things.ThingMerged} to and from an
 * {@link org.eclipse.ditto.protocoladapter.Adaptable}.
 */
final class ThingMergedEventAdapter extends AbstractThingAdapter<ThingMerged> implements MergedEventAdapter {

    private ThingMergedEventAdapter(final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getThingMergedEventMappingStrategies(), headerTranslator,
                ThingMergePathMatcher.getInstance());
    }

    /**
     * Returns a new ThingEventAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingMergedEventAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingMergedEventAdapter(requireNonNull(headerTranslator));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final JsonPointer path = adaptable.getPayload().getPath();
        return payloadPathMatcher.match(path);
    }

    @Override
    protected Adaptable mapSignalToAdaptable(final ThingMerged event, final TopicPath.Channel channel) {
        final EventsTopicPathBuilder eventsTopicPathBuilder = getEventTopicPathBuilderFor(event, channel);

        final PayloadBuilder payloadBuilder = Payload.newBuilder(event.getResourcePath())
                .withRevision(event.getRevision());
        event.getTimestamp().ifPresent(payloadBuilder::withTimestamp);
        payloadBuilder.withValue(event.getValue());

        return Adaptable.newBuilder(eventsTopicPathBuilder.build())
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithJsonMergePatchContentType(event.getDittoHeaders()))
                .build();
    }

}
