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

import java.util.Locale;
import java.util.Optional;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.protocol.EventsTopicPathBuilder;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.protocol.UnknownChannelException;
import org.eclipse.ditto.protocol.UnknownEventException;

final class PolicyEventSignalMapper extends AbstractSignalMapper<PolicyEvent<?>> {

    @Override
    void enhancePayloadBuilder(final PolicyEvent<?> signal, final PayloadBuilder payloadBuilder) {
        payloadBuilder.withRevision(signal.getRevision())
                .withTimestamp(signal.getTimestamp().orElse(null));
        final Optional<JsonValue> value =
                signal.getEntity(signal.getDittoHeaders().getSchemaVersion().orElse(signal.getLatestSchemaVersion()));
        value.ifPresent(payloadBuilder::withValue);
    }

    @Override
    DittoHeaders enhanceHeaders(final PolicyEvent<?> signal) {
        final Optional<JsonValue> value =
                signal.getEntity(signal.getDittoHeaders().getSchemaVersion().orElse(signal.getLatestSchemaVersion()));
        if (value.isPresent()) {
            return ProtocolFactory.newHeadersWithJsonContentType(signal.getDittoHeaders());
        } else {
            return signal.getDittoHeaders();
        }
    }

    @Override
    TopicPath getTopicPath(final PolicyEvent<?> signal, final TopicPath.Channel channel) {
        final EventsTopicPathBuilder topicPathBuilder = getEventsTopicPathBuilderOrThrow(signal, channel);
        final String eventName = getLowerCaseEventName(signal);
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
        return topicPathBuilder.build();
    }

    private static EventsTopicPathBuilder getEventsTopicPathBuilderOrThrow(final PolicyEvent<?> event,
            final TopicPath.Channel channel) {

        TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(event.getEntityId());
        if (TopicPath.Channel.NONE == channel) {
            topicPathBuilder = topicPathBuilder.none();
        } else {
            throw UnknownChannelException.newBuilder(channel, event.getType())
                    .dittoHeaders(event.getDittoHeaders())
                    .build();
        }
        return topicPathBuilder.events();
    }

    private static String getLowerCaseEventName(final PolicyEvent<?> thingEvent) {
        final Class<?> thingEventClass = thingEvent.getClass();
        final String eventClassSimpleName = thingEventClass.getSimpleName();
        return eventClassSimpleName.toLowerCase(Locale.ENGLISH);
    }

    private static boolean isAction(final String eventName, final TopicPath.Action expectedAction) {
        return eventName.contains(expectedAction.getName());
    }
}
