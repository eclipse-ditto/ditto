/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.messages;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.IdValidator;
import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageBuilder;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.ThingIdInvalidException;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;

/**
 * Base class for {@link MessageCommand}s.
 *
 * @param <T> the type of the message's payload.
 * @param <C> the type of the AbstractMessageCommand.
 */
abstract class AbstractMessageCommand<T, C extends AbstractMessageCommand> extends AbstractCommand<C>
        implements MessageCommand<T, C> {

    /**
     * The regex pattern a Thing ID has to conform to. Defined by
     * <a href="http://www.ietf.org/rfc/rfc2396.txt">RFC-2396</a>.
     */
    public static final String THING_ID_NON_NAMESPACE_REGEX =
            "(?:[-\\w:@&=+,.!~*'_;]|%\\p{XDigit}{2})(?:[-\\w:@&=+,.!~*'$_;]|%\\p{XDigit}{2})*";

    /**
     * The regex pattern a Thing Namespace.
     */
    public static final String THING_NAMESPACE_PREFIX_REGEX = "(?<ns>|(?:(?:[a-zA-Z]\\w*)(?:\\.[a-zA-Z]\\w*)*))";

    /**
     * The regex pattern a Thing ID has to conform to. Combines "namespace" pattern (java package notation + a
     * semicolon) and "non namespace" (Defined by <a href="http://www.ietf.org/rfc/rfc2396.txt">RFC-2396</a>) pattern.
     */
    public static final String THING_ID_REGEX = THING_NAMESPACE_PREFIX_REGEX + "\\:" + THING_ID_NON_NAMESPACE_REGEX;

    private final String thingId;
    private final Message<T> message;

    AbstractMessageCommand(final String type, final String thingId, final Message<T> message,
            final DittoHeaders dittoHeaders) {
        super(type, dittoHeaders);
        this.thingId = requireNonNull(thingId, "The thingId cannot be null.");
        this.message = requireNonNull(message, "The message cannot be null.");

        validateThingId();
    }

    private void validateThingId() {
        final Validator thingIdValidator = IdValidator.newInstance(thingId, THING_ID_REGEX);
        if (!thingIdValidator.isValid()) {
            throw new ThingIdInvalidException(thingId);
        }
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public Message<T> getMessage() {
        return message;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(MessageCommand.JsonFields.JSON_THING_ID, getThingId(), predicate);

        final JsonObjectBuilder messageBuilder = JsonFactory.newObjectBuilder();
        final JsonObject headersObject = message.getHeaders().toJson();
        messageBuilder.set(MessageCommand.JsonFields.JSON_MESSAGE_HEADERS, headersObject, predicate);

        MessagePayloadSerializer.serialize(message, messageBuilder, predicate);

        final JsonObject messageObject = messageBuilder.build();
        jsonObjectBuilder.set(MessageCommand.JsonFields.JSON_MESSAGE, messageObject, predicate);
    }

    /**
     * Deserializes the {@link Message} from the JSON representation - the {@code rawPayload} is decoded with Base64.
     *
     * @param <T> the type of the message's payload.
     * @param jsonObject the JsonObjectReader to use for reading the message
     * @return the Message
     */
    protected static <T> Message<T> deserializeMessageFromJson(final JsonObject jsonObject) {
        final JsonObject messageObject = jsonObject.getValueOrThrow(MessageCommand.JsonFields.JSON_MESSAGE);
        final JsonObject messageHeadersObject =
                messageObject.getValueOrThrow(MessageCommand.JsonFields.JSON_MESSAGE_HEADERS);
        final Optional<JsonValue> messagePayloadOptional =
                messageObject.getValue(MessageCommand.JsonFields.JSON_MESSAGE_PAYLOAD);

        final MessageHeaders messageHeaders = MessageHeaders.of(messageHeadersObject);
        final MessageBuilder<T> messageBuilder = Message.newBuilder(messageHeaders);
        MessagePayloadSerializer.deserialize(messagePayloadOptional, messageBuilder,
                messageHeaders.getContentType().orElse(""));
        return messageBuilder.build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, message);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final AbstractMessageCommand<?, ?> other = (AbstractMessageCommand<?, ?>) obj;
        return other.canEqual(this) && Objects.equals(thingId, other.thingId)
                && Objects.equals(message, other.message)
                && super.equals(other);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof AbstractMessageCommand);
    }

    @Override
    public String toString() {
        return "thingId=" + thingId + ", message=" + message;
    }

    @Override
    public abstract C setDittoHeaders(final DittoHeaders dittoHeaders);

}
