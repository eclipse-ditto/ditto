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
package org.eclipse.ditto.placeholders;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;

/**
 * This exception indicates that a request contains a placeholder which cannot be resolved.
 */
@Immutable
@JsonParsableException(errorCode = PlaceholderNotResolvableException.ERROR_CODE)
public final class PlaceholderNotResolvableException extends DittoRuntimeException implements PlaceholderException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "placeholder.notresolvable";

    private static final String UNKNOWN_MESSAGE_TEMPLATE = "The placeholder ''{0}'' is unknown.";
    private static final String UNKNOWN_DESCRIPTION_TEMPLATE = "Please use one of the supported placeholders: {0}.";
    private static final String NOT_RESOLVABLE_INPUT_MESSAGE_TEMPLATE = "The input contains not resolvable " +
            "placeholders: ''{0}''.";
    /**
     * Exception description when at least one placeholder could not be resolved (e.g. when braces are in unexpected
     * order).
     */
    public static final String NOT_RESOLVABLE_DESCRIPTION = "Please make sure that placeholders are not " +
            "nested and braces are in the expected order.";

    private static final long serialVersionUID = -8724890154457417912L;

    private PlaceholderNotResolvableException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@link PlaceholderNotResolvableException} for an unknown placeholder.
     *
     * @param unknownPlaceholder the unknown placeholder.
     * @param supportedPlaceholders the supported placeholders.
     *
     * @return the builder.
     */
    public static Builder newUnknownPlaceholderBuilder(final CharSequence unknownPlaceholder,
            final Iterable<CharSequence> supportedPlaceholders) {
        requireNonNull(unknownPlaceholder);
        requireNonNull(supportedPlaceholders);

        final String message = MessageFormat.format(UNKNOWN_MESSAGE_TEMPLATE, requireNonNull(unknownPlaceholder));
        final String supportedPlaceHoldersStr = StreamSupport.stream(supportedPlaceholders.spliterator(), false)
                .map(placeholder -> "'" + placeholder + "'")
                .collect(Collectors.joining(", "));
        final String description = MessageFormat.format(UNKNOWN_DESCRIPTION_TEMPLATE, supportedPlaceHoldersStr);

        return new Builder(message, description);
    }


    /**
     * A mutable builder for a {@link PlaceholderNotResolvableException} for an input with non-resolvable
     * placeholders (e.g. missing placeholder end string: "}}").
     *
     * @param notResolvableInput the input which is not resolvable.
     *
     * @return the builder.
     */
    public static Builder newNotResolvableInputBuilder(final CharSequence notResolvableInput) {
        requireNonNull(notResolvableInput);

        final String message = MessageFormat.format(NOT_RESOLVABLE_INPUT_MESSAGE_TEMPLATE, requireNonNull(notResolvableInput));

        return new Builder(message, NOT_RESOLVABLE_DESCRIPTION);
    }

    /**
     * Constructs a new {@code PlaceholderNotResolvableException} object with the exception message extracted
     * from the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PlaceholderNotResolvableException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static PlaceholderNotResolvableException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link PlaceholderNotResolvableException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PlaceholderNotResolvableException> {

        private Builder() {
            description(UNKNOWN_DESCRIPTION_TEMPLATE);
        }

        private Builder(final String message, final String description) {
            this();
            message(requireNonNull(message));
            description(requireNonNull(description));
        }

        @Override
        protected PlaceholderNotResolvableException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new PlaceholderNotResolvableException(dittoHeaders, message, description, cause, href);
        }
    }
}
