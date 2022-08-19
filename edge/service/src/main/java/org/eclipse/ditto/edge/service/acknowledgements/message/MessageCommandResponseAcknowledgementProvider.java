/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.acknowledgements.message;

import static org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel.LIVE_RESPONSE;

import java.util.Optional;

import org.eclipse.ditto.base.model.acks.CommandResponseAcknowledgementProvider;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.things.model.signals.acks.ThingAcknowledgementFactory;

/**
 * Provides Acknowledgements specific for {@link MessageCommandResponse}s.
 *
 * @since 3.0.0
 */
public final class MessageCommandResponseAcknowledgementProvider
        implements CommandResponseAcknowledgementProvider<MessageCommand<?, ?>> {

    private static final MessageCommandResponseAcknowledgementProvider
            INSTANCE = new MessageCommandResponseAcknowledgementProvider();

    /**
     * Returns an instance of {@code MessageCommandResponseAcknowledgementProvider}.
     *
     * @return the instance.
     */
    public static MessageCommandResponseAcknowledgementProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public Acknowledgement provideAcknowledgement(final MessageCommand<?, ?> originatingSignal,
            final CommandResponse<?> commandResponse) {

        if (commandResponse instanceof MessageCommandResponse<?, ?> messageCommandResponse) {
            final DittoHeaders responseDittoHeaders = messageCommandResponse.getDittoHeaders();
            final Message<?> message = messageCommandResponse.getMessage();
            final DittoHeaders liveResponseAckHeaders = responseDittoHeaders.toBuilder()
                    .putHeaders(message.getHeaders())
                    .build();

            return ThingAcknowledgementFactory.newAcknowledgement(LIVE_RESPONSE,
                    messageCommandResponse.getEntityId(),
                    messageCommandResponse.getHttpStatus(),
                    liveResponseAckHeaders,
                    getPayload(messageCommandResponse).orElse(null));
        } else {
            throw DittoInternalErrorException.newBuilder()
                    .dittoHeaders(originatingSignal.getDittoHeaders())
                    .cause(new IllegalArgumentException("The given commandResponse was not of type" +
                            " MessageCommandResponse. Check by isApplicable before passing a command response to this " +
                            "method."))
                    .build();
        }
    }

    @Override
    public boolean isApplicable(final CommandResponse<?> commandResponse) {
        return commandResponse instanceof MessageCommandResponse<?, ?>;
    }

    @Override
    public Class<?> getCommandClass() {
        return MessageCommand.class;
    }

    private static Optional<JsonValue> getPayload(final MessageCommandResponse<?, ?> messageCommandResponse) {
        final JsonPointer jsonMessagePointer = MessageCommandResponse.JsonFields.JSON_MESSAGE.getPointer();
        final JsonPointer jsonMessagePayloadPointer =
                MessageCommandResponse.JsonFields.JSON_MESSAGE_PAYLOAD.getPointer();
        final JsonPointer messagePayloadPointer = jsonMessagePointer.append(jsonMessagePayloadPointer);
        final JsonObject messageCommandResponseJsonObject = messageCommandResponse.toJson();

        return messageCommandResponseJsonObject.getValue(messagePayloadPointer);
    }
}
