/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Thrown if a {@link TopicPath} is not supported.
 */
@JsonParsableException(errorCode = UnknownTopicPathException.ERROR_CODE)
public final class UnknownTopicPathException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "things.protocol.adapter:unknown.topicpath";

    private static final String MESSAGE_TEMPLATE = "The topic path ''{0}'' is not supported.";

    private static final String DEFAULT_DESCRIPTION = "Check if the topic path is correct.";

    private static final long serialVersionUID = 5748920703966374167L;

    private UnknownTopicPathException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code UnknownTopicPathException}.
     *
     * @param topicPath the topic path of the adaptable not supported.
     * @return the builder.
     */
    public static Builder newBuilder(final TopicPath topicPath) {
        return new Builder(topicPath.getPath());
    }

    /**
     * A mutable builder for a {@code UnknownTopicPathException}.
     *
     * @param path the topic path of the adaptable not supported.
     * @return the builder.
     */
    public static Builder newBuilder(final String path) {
        return new Builder(path);
    }

    /**
     * Constructs a new {@code UnknownTopicPathException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new UnknownTopicPathException.
     */
    public static UnknownTopicPathException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code UnknownTopicPathException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new UnknownTopicPathException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static UnknownTopicPathException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link UnknownTopicPathException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<UnknownTopicPathException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String path) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, path));
        }

        @Override
        protected UnknownTopicPathException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new UnknownTopicPathException(dittoHeaders, message, description, cause, href);
        }
    }

}
