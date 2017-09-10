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
package org.eclipse.ditto.model.messages;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Thrown if a message reached its defined timeout.
 */
@Immutable
public final class MessageTimeoutException extends DittoRuntimeException implements MessageException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "message.timeout";

    private static final String MESSAGE_TEMPLATE = "The Message reached the specified timeout of {0} seconds.";

    private static final String DEFAULT_DESCRIPTION = "Try increasing the Message timeout or ensure that the recipient "
            + "of the Message responds in time.";

    private static final long serialVersionUID = -4258554948967954371L;

    /**
     * Constructs a new {@code MessageTimeoutException} object.
     *
     * @param timeout the timeout in seconds.
     */
    public MessageTimeoutException(final Long timeout) {
        this(DittoHeaders.empty(), MessageFormat.format(MESSAGE_TEMPLATE, timeout), DEFAULT_DESCRIPTION, null, null);
    }

    private MessageTimeoutException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.REQUEST_TIMEOUT, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code MessageTimeoutException}.
     *
     * @param timeout the timeout in seconds.
     * @return the builder.
     */
    public static Builder newBuilder(@Nullable final Long timeout) {
        return new Builder(timeout);
    }

    /**
     * Constructs a new {@code MessageTimeoutException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @return the new MessageTimeoutException.
     */
    public static MessageTimeoutException fromMessage(final String message) {
        return new Builder()
                .message(message)
                .build();
    }

    /**
     * Deserialize from a JSON object.
     *
     * @param jsonObject the JSON object to deserialize.
     * @param dittoHeaders the headers.
     * @return an instance of this class.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static MessageTimeoutException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .loadJson(jsonObject)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link MessageTimeoutException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<MessageTimeoutException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(@Nullable final Long timeout) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, timeout));
        }

        @Override
        protected MessageTimeoutException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new MessageTimeoutException(dittoHeaders, message, description, cause, href);
        }

    }

}
