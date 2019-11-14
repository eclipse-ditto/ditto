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

import java.util.Optional;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.CommandsTopicPathBuilder;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.protocoladapter.UnknownCommandException;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithOptionalEntity;

abstract class ModifyCommandAdaptableConstructor<T extends Signal & WithOptionalEntity>
        implements AdaptableConstructor<T> {

    @Override
    public Adaptable construct(final T command, final TopicPath.Channel channel) {

        validate(command);

        final TopicPathBuilder topicPathBuilder = getTopicPathBuilder(command);

        final CommandsTopicPathBuilder commandsTopicPathBuilder =
                fromTopicPathBuilderWithChannel(topicPathBuilder, channel);

        final String commandName = command.getClass().getSimpleName().toLowerCase();
        if (commandName.startsWith(TopicPath.Action.CREATE.toString())) {
            commandsTopicPathBuilder.create();
        } else if (commandName.startsWith(TopicPath.Action.MODIFY.toString())) {
            commandsTopicPathBuilder.modify();
        } else if (commandName.startsWith(TopicPath.Action.DELETE.toString())) {
            commandsTopicPathBuilder.delete();
        } else {
            throw UnknownCommandException.newBuilder(commandName).build();
        }

        final PayloadBuilder payloadBuilder = Payload.newBuilder(command.getResourcePath());

        final Optional<JsonValue> value = command.getEntity(command.getImplementedSchemaVersion());
        value.ifPresent(payloadBuilder::withValue);

        return Adaptable.newBuilder(commandsTopicPathBuilder.build())
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(command.getDittoHeaders()))
                .build();
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
