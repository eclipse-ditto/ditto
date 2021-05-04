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
package org.eclipse.ditto.connectivity.model;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;

/**
 * Thrown if a {@link FilteredTopic} could not be parsed from a String representation.
 */
@Immutable
@JsonParsableException(errorCode = TopicParseException.ERROR_CODE)
public final class TopicParseException extends DittoRuntimeException implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "topic.invalid";

    private static final String MESSAGE_TEMPLATE = "The topic ''{0}'' could not be parsed.";

    private static final String DEFAULT_DESCRIPTION = "Check if the format is the expected one.";
    private static final String DESCRIPTION_WITH_HINT = "''{0}'' - check if the format is the expected one.";

    private static final long serialVersionUID = -2896189723489733822L;

    private TopicParseException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code TopicParseException}.
     *
     * @param parsedFilterString the filter String which was tried to parse.
     * @return the builder.
     */
    public static Builder newBuilder(final String parsedFilterString) {
        return new Builder(parsedFilterString, null);
    }

    /**
     * A mutable builder for a {@code TopicParseException}.
     *
     * @param parsedFilterString the filter String which was tried to parse.
     * @param descriptionHint another hint which should be added to the description about what was wrong.
     * @return the builder.
     */
    public static Builder newBuilder(final String parsedFilterString, @Nullable final String descriptionHint) {
        return new Builder(parsedFilterString, descriptionHint);
    }

    /**
     * Constructs a new {@code TopicParseException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new TopicParseException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static TopicParseException fromMessage(@Nullable final String message, final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code TopicParseException} object with the exception message extracted from the given JSON
     * object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new TopicParseException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static TopicParseException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link TopicParseException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<TopicParseException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String parsedFilterString, @Nullable final String descriptionHint) {
            this();
            if (descriptionHint != null) {
                description(MessageFormat.format(DESCRIPTION_WITH_HINT, descriptionHint));
            }
            message(MessageFormat.format(MESSAGE_TEMPLATE, parsedFilterString));
        }

        @Override
        protected TopicParseException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new TopicParseException(dittoHeaders, message, description, cause, href);
        }

    }

}
