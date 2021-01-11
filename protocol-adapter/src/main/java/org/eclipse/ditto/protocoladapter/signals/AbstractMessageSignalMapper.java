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

import java.util.Optional;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.WithThingId;
import org.eclipse.ditto.protocoladapter.MessagesTopicPathBuilder;
import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;

/**
 * Base class of {@link SignalMapper} for query commands.
 *
 * @param <T> the type supported by this {@link SignalMapper}
 */
abstract class AbstractMessageSignalMapper<T extends Signal<?> & WithThingId> extends AbstractSignalMapper<T> {

    @Override
    void validate(final T signal, final TopicPath.Channel channel) {
        if (channel != TopicPath.Channel.LIVE) {
            throw new IllegalArgumentException("Unknown Channel '" + channel + "'");
        }
    }

    @Override
    TopicPath getTopicPath(final T command, final TopicPath.Channel channel) {
        final ThingId thingId = command.getThingEntityId();
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(thingId);
        final MessagesTopicPathBuilder messagesTopicPathBuilder = topicPathBuilder.live().messages();
        messagesTopicPathBuilder.subject(extractSubject(command));
        return messagesTopicPathBuilder.build();
    }

    /**
     * Extract subject from the message (message commands and message command responses do not have a common
     * interface to extract that kind of information).
     *
     * @param command the command that is processed
     * @return the message subject
     */
    abstract String extractSubject(final T command);

    /**
     * Extract status code from the message (message commands and message command responses do not have a common
     * interface to extract that kind of information).
     *
     * @param command the command that is processed
     * @return the status code
     * @deprecated as of 2.0.0 please use {@link #extractHttpStatus(Signal)} instead.
     */
    @Deprecated
    Optional<HttpStatusCode> extractStatusCode(final T command) {
        return extractHttpStatus(command).map(HttpStatus::getCode).flatMap(HttpStatusCode::forInt);
    }

    /**
     * Extract the HTTP status from the message (message commands and message command responses do not have a common
     * interface to extract that kind of information).
     *
     * @param command the command that is processed.
     * @return the HTTP status.
     * @since 2.0.0
     */
    abstract Optional<HttpStatus> extractHttpStatus(T command);

    /**
     * Extract status code from the message (message commands and message command responses do not have a common
     * interface to extract that kind of information).
     *
     * @param command the command that is processed
     * @return the message headers
     */
    abstract MessageHeaders extractMessageHeaders(T command);

    @Override
    void enhancePayloadBuilder(final T command, final PayloadBuilder payloadBuilder) {
        final JsonObject messageCommandJson = command.toJson();
        final JsonPointer messagePointer = MessageCommand.JsonFields.JSON_MESSAGE.getPointer();
        final JsonPointer payloadPointer = MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD.getPointer();
        messageCommandJson.getValue(messagePointer.append(payloadPointer)).ifPresent(payloadBuilder::withValue);
        extractStatusCode(command).ifPresent(payloadBuilder::withStatus);
        messageCommandJson.getValue(CommandResponse.JsonFields.STATUS).ifPresent(payloadBuilder::withStatus);
    }

    @Override
    DittoHeaders enhanceHeaders(final T command) {
        final MessageHeaders messageHeaders = extractMessageHeaders(command);
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        // merge inner message headers and ditto headers (message headers win in case of a conflict)
        return dittoHeaders.toBuilder().putHeaders(messageHeaders).build();
    }
}
