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
package org.eclipse.ditto.protocol;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Thrown if a {@link org.eclipse.ditto.protocol.TopicPath.Channel} is not supported in combination with the
 * provided {@link org.eclipse.ditto.base.model.signals.Signal}.
 * @since 1.1.0
 */
@JsonParsableException(errorCode = UnknownChannelException.ERROR_CODE)
public final class UnknownChannelException extends DittoRuntimeException implements ProtocolAdapterException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "unknown.channel";

    private static final String MESSAGE_TEMPLATE =
            "The channel ''{0}'' is not supported in combination with signal type ''{1}''.";

    private static final String DEFAULT_DESCRIPTION = "Check if the channel is correct.";

    private static final long serialVersionUID = 2901292545115510591L;

    private UnknownChannelException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code UnknownChannelException}.
     *
     * @param channel the channel not supported.
     * @param type the type of the signal for which the channel is not supported.
     * @return the builder.
     */
    public static Builder newBuilder(final TopicPath.Channel channel, final String type) {
        return new Builder(channel, type);
    }

    /**
     * Constructs a new {@code UnknownChannelException} object with the exception message extracted from the given JSON
     * object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new UnknownChannelException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static UnknownChannelException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(getMessage())
                .description(getDescription().orElse(null))
                .cause(getCause())
                .href(getHref().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link UnknownChannelException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<UnknownChannelException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final TopicPath.Channel channel, final String type) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, channel.getName(), type));
        }

        @Override
        protected UnknownChannelException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new UnknownChannelException(dittoHeaders, message, description, cause, href);
        }
    }

}
