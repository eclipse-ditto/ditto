/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.connectivity.messaging;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithThingId;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Maps from a {@link org.eclipse.ditto.protocoladapter.TopicPath} and to an internal distributed pubsub topic e.g.
 * _/_/things/twin/events is mapped to {@code things.events:}.
 */
public class TopicPathMapper {

    private static final String TOPIC_TEMPLATE = "_/_/{0}/{1}/{2}";
    public static final Map<String, String> SUPPORTED_TOPICS;

    static {
        SUPPORTED_TOPICS = new HashMap<>();
        SUPPORTED_TOPICS.put("_/_/things/twin/events", ThingEvent.TYPE_PREFIX);
        SUPPORTED_TOPICS.put("_/_/things/live/messages", MessageCommand.TYPE_PREFIX);
        SUPPORTED_TOPICS.put("_/_/things/live/events", "things-live-events");
        SUPPORTED_TOPICS.put("_/_/things/live/commands", "things-live-commands");
    }

    public static Optional<String> mapToPubSubTopic(final String topicPath) {
        return Optional.ofNullable(SUPPORTED_TOPICS.get(topicPath));
    }

    public static String mapSignalToTopicPath(final Signal<?> signal) {
        // only things as group supported
        final String group = signal instanceof WithThingId ? TopicPath.Group.THINGS.getName() : "unsupported";
        final String channel = signal.getDittoHeaders().getChannel().orElse(TopicPath.Channel.TWIN.getName());
        final String criterion;
        if (signal instanceof MessageCommand || signal instanceof MessageCommandResponse) {
            criterion = TopicPath.Criterion.MESSAGES.getName();
        } else if (signal instanceof Command || signal instanceof CommandResponse) {
            criterion = TopicPath.Criterion.COMMANDS.getName();
        } else if (signal instanceof Event) {
            criterion = TopicPath.Criterion.EVENTS.getName();
        } else {
            criterion = "unsupported";
        }
        return MessageFormat.format(TOPIC_TEMPLATE, group, channel, criterion);
    }
}