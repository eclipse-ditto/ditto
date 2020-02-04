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
package org.eclipse.ditto.protocoladapter.signals;

import java.util.stream.Stream;

import org.eclipse.ditto.protocoladapter.CommandsTopicPathBuilder;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.protocoladapter.UnknownCommandException;
import org.eclipse.ditto.signals.base.Signal;

abstract class AbstractCommandSignalMapper<T extends Signal<?>> extends AbstractSignalMapper<T> {

    @Override
    TopicPath getTopicPath(final T command, final TopicPath.Channel channel) {
        final TopicPathBuilder topicPathBuilder = getTopicPathBuilder(command);
        final CommandsTopicPathBuilder commandsTopicPathBuilder =
                fromTopicPathBuilderWithChannel(topicPathBuilder, channel);
        setTopicPathAction(commandsTopicPathBuilder, command, getSupportedActions());
        return commandsTopicPathBuilder.build();
    }

    /**
     * TODO
     */
    abstract TopicPathBuilder getTopicPathBuilder(final T command);

    /**
     * @return array aof {@link org.eclipse.ditto.protocoladapter.TopicPath.Action}s the implementation supports.
     */
    abstract TopicPath.Action[] getSupportedActions();

    private void setTopicPathAction(final CommandsTopicPathBuilder builder, final T signal,
            final TopicPath.Action... supportedActions) {

        // e.g. message commands have no associated action
        if (supportedActions.length > 0) {
            final String commandName = signal.getClass().getSimpleName().toLowerCase();
            setAction(builder, Stream.of(supportedActions)
                    .filter(action -> commandName.startsWith(action.toString()))
                    .findAny()
                    .orElseThrow(() -> UnknownCommandException.newBuilder(commandName).build()));
        }
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

    private static CommandsTopicPathBuilder fromTopicPathBuilderWithChannel(final TopicPathBuilder topicPathBuilder,
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
