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
package org.eclipse.ditto.messages.model;

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
 * Thrown if the Subject is not valid (for example if it does not comply to the Subject REGEX).
 */
@Immutable
@JsonParsableException(errorCode = SubjectInvalidException.ERROR_CODE)
public final class SubjectInvalidException extends DittoRuntimeException implements MessageException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "subject.invalid";

    private static final String MESSAGE_TEMPLATE = "Subject ''{0}'' is not valid!";

    private static final String DEFAULT_DESCRIPTION =
            "It must not be empty and conform to RFC-3986 (URI) - check here if it does (select 'path' in the list): "
                    + "http://www.websitedev.de/temp/rfc3986-check.html.gz";

    private static final long serialVersionUID = 8764688424815362756L;

    /**
     * Constructs a new {@code SubjectInvalidException} object.
     *
     * @param subject the invalid Subject.
     */
    public SubjectInvalidException(final String subject) {
        this(DittoHeaders.empty(), MessageFormat.format(MESSAGE_TEMPLATE, subject), DEFAULT_DESCRIPTION, null, null);
    }

    private SubjectInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code SubjectInvalidException}.
     *
     * @param subject the subject.
     * @return the builder.
     */
    public static Builder newBuilder(final String subject) {
        return new Builder(subject);
    }

    /**
     * Constructs a new {@code SubjectInvalidException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubjectInvalidException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static SubjectInvalidException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Deserialize from a JSON object.
     *
     * @param jsonObject the JSON object to deserialize.
     * @param dittoHeaders the headers.
     * @return an instance of this class.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SubjectInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link SubjectInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<SubjectInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String subject) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, subject));
        }

        @Override
        protected SubjectInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new SubjectInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
