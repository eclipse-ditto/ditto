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
package org.eclipse.ditto.protocol.adapter.things;

import java.util.Locale;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.EventsTopicPathBuilder;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.protocol.PayloadPathMatcher;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.protocol.UnknownChannelException;
import org.eclipse.ditto.protocol.UnknownEventException;
import org.eclipse.ditto.protocol.adapter.AbstractAdapter;
import org.eclipse.ditto.protocol.mappingstrategies.MappingStrategies;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * Base class for {@link org.eclipse.ditto.protocol.adapter.Adapter}s that handle thing commands.
 *
 * @param <T> the type of the thing commands
 */
abstract class AbstractThingAdapter<T extends Signal<?>> extends AbstractAdapter<T> implements ThingAdapter<T> {

    /**
     * Constructor.
     *
     * @param mappingStrategies the mapping strategies used to convert from
     * {@link org.eclipse.ditto.protocol.Adaptable}s to {@link org.eclipse.ditto.base.model.signals.Signal}s
     * @param headerTranslator the header translator used for the mapping
     */
    protected AbstractThingAdapter(final MappingStrategies<T> mappingStrategies,
            final HeaderTranslator headerTranslator) {

        this(mappingStrategies, headerTranslator, ThingModifyPathMatcher.getInstance());
    }

    /**
     * Constructor.
     *
     * @param mappingStrategies the mapping strategies used to convert from
     * {@link org.eclipse.ditto.protocol.Adaptable}s to {@link org.eclipse.ditto.base.model.signals.Signal}s
     * @param headerTranslator the header translator used for the mapping
     * @param pathMatcher the path matcher used for the mapping
     */
    protected AbstractThingAdapter(final MappingStrategies<T> mappingStrategies,
            final HeaderTranslator headerTranslator,
            final PayloadPathMatcher pathMatcher) {

        super(mappingStrategies, headerTranslator, pathMatcher);
    }

    protected static EventsTopicPathBuilder getEventTopicPathBuilderFor(final ThingEvent<?> event,
            final TopicPath.Channel channel) {

        final EventsTopicPathBuilder topicPathBuilder = getEventsTopicPathBuilderOrThrow(event, channel);
        final String eventName = getLowerCaseEventName(event);
        if (isAction(eventName, TopicPath.Action.CREATED)) {
            topicPathBuilder.created();
        } else if (isAction(eventName, TopicPath.Action.MODIFIED)) {
            topicPathBuilder.modified();
        } else if (isAction(eventName, TopicPath.Action.DELETED)) {
            topicPathBuilder.deleted();
        } else if (isAction(eventName, TopicPath.Action.MERGED)) {
            topicPathBuilder.merged();
        } else {
            throw UnknownEventException.newBuilder(eventName).build();
        }
        return topicPathBuilder;
    }

    private static EventsTopicPathBuilder getEventsTopicPathBuilderOrThrow(final ThingEvent<?> event,
            final TopicPath.Channel channel) {

        TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(event.getEntityId());
        if (TopicPath.Channel.TWIN == channel) {
            topicPathBuilder = topicPathBuilder.twin();
        } else if (TopicPath.Channel.LIVE == channel) {
            topicPathBuilder = topicPathBuilder.live();
        } else {
            throw UnknownChannelException.newBuilder(channel, event.getType())
                    .dittoHeaders(event.getDittoHeaders())
                    .build();
        }
        return topicPathBuilder.events();
    }

    private static String getLowerCaseEventName(final ThingEvent<?> thingEvent) {
        final Class<?> thingEventClass = thingEvent.getClass();
        final String eventClassSimpleName = thingEventClass.getSimpleName();
        return eventClassSimpleName.toLowerCase(Locale.ENGLISH);
    }

    private static boolean isAction(final String eventName, final TopicPath.Action expectedAction) {
        return eventName.contains(expectedAction.getName());
    }

}
