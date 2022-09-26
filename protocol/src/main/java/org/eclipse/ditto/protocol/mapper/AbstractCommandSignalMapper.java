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
package org.eclipse.ditto.protocol.mapper;

import java.util.stream.Stream;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.CommandsTopicPathBuilder;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.protocol.UnknownCommandException;
import org.eclipse.ditto.protocol.UnknownCommandResponseException;

/**
 * Base class of {@link SignalMapper}s for commands (e.g. query, modify commands).
 *
 * @param <T> the type of the command
 */
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
     * @param command the command that is processed
     * @return a {@link TopicPathBuilder} for the given command.
     */
    abstract TopicPathBuilder getTopicPathBuilder(final T command);

    /**
     * @return array of {@link TopicPath.Action}s the implementation supports.
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
                    .orElseThrow(() -> unknownCommandException(commandName)));
        }
    }

    DittoRuntimeException unknownCommandException(final String commandName) {
        if (this instanceof ResponseSignalMapper) {
            return UnknownCommandResponseException.newBuilder(commandName).build();
        } else {
            return UnknownCommandException.newBuilder(commandName).build();
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
            case MERGE:
                builder.merge();
                break;
            case DELETE:
                builder.delete();
                break;
            default:
                throw unknownCommandException(action.getName());
        }
    }

    private static CommandsTopicPathBuilder fromTopicPathBuilderWithChannel(final TopicPathBuilder topicPathBuilder,
            final TopicPath.Channel channel) {
        final CommandsTopicPathBuilder commandsTopicPathBuilder;
        switch (channel) {
            case TWIN:
                commandsTopicPathBuilder = topicPathBuilder.twin().commands();
                break;
            case LIVE:
                commandsTopicPathBuilder = topicPathBuilder.live().commands();
                break;
            case NONE:
                commandsTopicPathBuilder = topicPathBuilder.none().commands();
                break;
            default:
                throw new IllegalArgumentException("Unknown Channel '" + channel + "'");
        }
        return commandsTopicPathBuilder;
    }

}
