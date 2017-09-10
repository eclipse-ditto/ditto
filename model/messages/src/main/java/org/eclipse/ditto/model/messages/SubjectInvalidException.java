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
 * Thrown if the Subject is not valid (for example if it does not comply to the Subject REGEX).
 */
@Immutable
public final class SubjectInvalidException extends DittoRuntimeException implements MessageException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "subject.invalid";

    private static final String MESSAGE_TEMPLATE = "Subject ''{0}'' is not valid!";

    private static final String DEFAULT_DESCRIPTION =
            "It must not be empty and conform to RFC-2396 (URI) - check here if it does (select 'path' in the list): "
                    + "http://www.websitedev.de/temp/rfc2396-check.html.gz";

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

        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
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
     * Constructs a new {@code SubjectInvalidException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @return the new SubjectInvalidException.
     */
    public static SubjectInvalidException fromMessage(final String message) {
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
    public static SubjectInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final Builder builder = new Builder();
        builder.loadJson(jsonObject);
        builder.dittoHeaders(dittoHeaders);
        return builder.build();
    }

    /**
     * A mutable builder with a fluent API for a {@link SubjectInvalidException}.
     *
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
