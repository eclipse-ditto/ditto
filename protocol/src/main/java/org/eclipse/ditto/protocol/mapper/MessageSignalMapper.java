/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.Optional;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.things.model.WithThingId;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.messages.model.signals.commands.WithMessage;

/**
 * This is a SignalMapper for message signals.
 *
 * @param <T> the type supported by this {@link SignalMapper}
 */
final class MessageSignalMapper<T extends Signal<?> & WithThingId & WithMessage<?>> extends AbstractSignalMapper<T> {

    private MessageSignalMapper() {
        super();
    }

    static <T extends Signal<?> & WithThingId & WithMessage<?>> MessageSignalMapper<T> getInstance() {
        return new MessageSignalMapper<>();
    }

    @Override
    void validate(final T signal, final TopicPath.Channel channel) {
        if (TopicPath.Channel.LIVE != channel) {
            throw new IllegalArgumentException("Unknown Channel '" + channel + "'");
        }
    }

    @Override
    TopicPath getTopicPath(final T signal, final TopicPath.Channel channel) {
        return ProtocolFactory.newTopicPathBuilder(signal.getEntityId())
                .live()
                .messages()
                .subject(signal.getMessage().getSubject())
                .build();
    }

    @Override
    void enhancePayloadBuilder(final T signal, final PayloadBuilder payloadBuilder) {
        final JsonObject signalJson = signal.toJson();
        final JsonPointer payloadPointer;
        if (isCommandResponse(signal)) {
            payloadPointer = getPayloadPointer(MessageCommandResponse.JsonFields.JSON_MESSAGE,
                    MessageCommandResponse.JsonFields.JSON_MESSAGE_PAYLOAD);
            getHttpStatus(signalJson)
                    .orElseGet(() -> getMessageHttpStatus(signal))
                    .ifPresent(payloadBuilder::withStatus);
        } else {
            payloadPointer = getPayloadPointer(MessageCommand.JsonFields.JSON_MESSAGE,
                    MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD);
        }
        signalJson.getValue(payloadPointer).ifPresent(payloadBuilder::withValue);
    }

    private boolean isCommandResponse(final T signal) {
        final String signalType = signal.getType();
        return signalType.startsWith(MessageCommandResponse.TYPE_PREFIX);
    }

    private static JsonPointer getPayloadPointer(final JsonFieldDefinition<?> messageFieldDefinition,
            final JsonFieldDefinition<?> payloadFieldDefinition) {

        final JsonPointer messagePointer = messageFieldDefinition.getPointer();
        return messagePointer.append(payloadFieldDefinition.getPointer());
    }

    private static Optional<Optional<HttpStatus>> getHttpStatus(final JsonObject signalJson) {
        return signalJson.getValue(CommandResponse.JsonFields.STATUS).map(HttpStatus::tryGetInstance);
    }

    private Optional<HttpStatus> getMessageHttpStatus(final T signal) {
        final Message<?> message = signal.getMessage();
        return message.getHttpStatus();
    }

    @Override
    DittoHeaders enhanceHeaders(final T signal) {

        // merge inner message headers and Ditto headers (message headers win in case of a conflict)
        return DittoHeaders.newBuilder(signal.getDittoHeaders())
                .putHeaders(signal.getMessage().getHeaders())
                .build();
    }

}
