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
package org.eclipse.ditto.protocoladapter.adaptables;

import java.util.stream.Stream;

import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.CommandsTopicPathBuilder;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.protocoladapter.UnknownCommandException;
import org.eclipse.ditto.signals.base.Signal;

abstract class AbstractAdaptableConstructor<T extends Signal> implements AdaptableConstructor<T> {

    @Override
    public Adaptable construct(final T command, final TopicPath.Channel channel) {

        validate(command);

        final TopicPathBuilder topicPathBuilder = getTopicPathBuilder(command);
        final CommandsTopicPathBuilder commandsTopicPathBuilder =
                fromTopicPathBuilderWithChannel(topicPathBuilder, channel);
        setTopicPathAction(commandsTopicPathBuilder, command, getSupportedActions());

        final PayloadBuilder payloadBuilder = Payload.newBuilder(command.getResourcePath());

        enhancePayloadBuilder(command, payloadBuilder);

        return Adaptable.newBuilder(commandsTopicPathBuilder.build())
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(command.getDittoHeaders()))
                .build();
    }

    private void setTopicPathAction(final CommandsTopicPathBuilder builder, final T signal,
            final TopicPath.Action... supportedActions) {

        final String commandName = signal.getClass().getSimpleName().toLowerCase();
        setAction(builder, Stream.of(supportedActions)
                .filter(action -> commandName.startsWith(action.toString()))
                .findAny()
                .orElseThrow(() -> UnknownCommandException.newBuilder(commandName).build()));
    }

    private void setAction(final CommandsTopicPathBuilder builder, final TopicPath.Action action) {
        switch (action) {
            case CREATE:
                builder.create();
                break;
            case RETRIEVE:
                builder.retrieve();
                break;
            case MODIFY:
                builder.modify();
                break;
            case DELETE:
                builder.delete();
                break;
            default:
                throw UnknownCommandException.newBuilder(action.getName()).build();
        }
    }

    static CommandsTopicPathBuilder fromTopicPathBuilderWithChannel(final TopicPathBuilder topicPathBuilder,
            final TopicPath.Channel channel) {
        final CommandsTopicPathBuilder commandsTopicPathBuilder;
        if (channel == TopicPath.Channel.TWIN) {
            commandsTopicPathBuilder = topicPathBuilder.twin().commands();
        } else if (channel == TopicPath.Channel.LIVE) {
            commandsTopicPathBuilder = topicPathBuilder.live().commands();
        } else {
            throw new IllegalArgumentException("Unknown Channel '" + channel + "'");
        }
        return commandsTopicPathBuilder;
    }

}
