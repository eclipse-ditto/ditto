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
package org.eclipse.ditto.signals.commands.things.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingException;

public class ThingPreconditionFailed extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "precondition.failed";

    private static final String MESSAGE_TEMPLATE =
            "The comparison of precondition header ''{0}'' for this thing resource evaluated to false." +
                    " Expected: ''{1}'', Actual: ''{2}''.";

    private static final String DEFAULT_DESCRIPTION = "The comparison of the provided precondition header with the " +
            "current ETag value of this thing resource evaluated to false. Check the value of your conditional header" +
            " value.";

    private ThingPreconditionFailed(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.PRECONDITION_FAILED, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code {@link ThingPreconditionFailed }}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@code {@link ThingPreconditionFailed }}.
     *
     * @param conditionalHeaderName the name of the conditional header.
     * @param expected the expected value.
     * @param actual the actual ETag value.
     * @return the builder.
     */
    public static Builder newBuilder(final String conditionalHeaderName, final String expected, final String actual) {
        return new Builder(conditionalHeaderName, expected, actual);
    }

    /**
     * Constructs a new {@code ConditionalHeadersPreconditionFailedException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConditionalHeadersPreconditionFailedException.
     */
    public static ThingPreconditionFailed fromMessage(final String message,
            final DittoHeaders dittoHeaders) {

        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code ConditionalHeadersPreconditionFailedException} object with the exception message extracted from
     * the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConditionalHeadersPreconditionFailedException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static ThingPreconditionFailed fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link ThingPreconditionFailed}.
     */
    @NotThreadSafe
    public static final class Builder
            extends DittoRuntimeExceptionBuilder<ThingPreconditionFailed> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String conditionalHeaderName, final String expected, final String actual) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, conditionalHeaderName, expected, actual));
        }

        @Override
        protected ThingPreconditionFailed doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new ThingPreconditionFailed(dittoHeaders, message, description, cause, href);
        }

    }
}
