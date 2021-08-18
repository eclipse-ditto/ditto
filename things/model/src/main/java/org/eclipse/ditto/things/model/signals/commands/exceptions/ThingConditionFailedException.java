/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.signals.commands.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingException;

/**
 * Thrown when validating a condition on a Thing or one of its sub-entities is failing.
 */
@Immutable
@JsonParsableException(errorCode = ThingConditionFailedException.ERROR_CODE)
public final class ThingConditionFailedException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "condition.failed";

    private static final String MESSAGE_TEMPLATE =
            "The specified condition ''{0}'' does not match the requested Thing.";

    private static final String DEFAULT_DESCRIPTION = "The condition provided in the condition header " +
            "evaluated to false for the requested Thing. Please check the value of your condition header value.";

    private ThingConditionFailedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.PRECONDITION_FAILED, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@link org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException}.
     *
     * @param condition the condition to apply for the request.
     * @return the builder.
     */
    public static Builder newBuilder(final String condition) {
        return new Builder(condition);
    }

    /**
     * Constructs a new {@link org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException}
     * object with the exception message extracted from the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new {@link org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ThingConditionFailedException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException}.
     */
    @NotThreadSafe
    public static final class Builder
            extends DittoRuntimeExceptionBuilder<ThingConditionFailedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String condition) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, condition));
        }

        @Override
        protected ThingConditionFailedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ThingConditionFailedException(dittoHeaders, message, description, cause, href);
        }
    }

}
