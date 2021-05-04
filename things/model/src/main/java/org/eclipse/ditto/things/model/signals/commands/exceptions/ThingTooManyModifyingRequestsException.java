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
package org.eclipse.ditto.things.model.signals.commands.exceptions;

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
import org.eclipse.ditto.things.model.ThingException;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Thrown if to a single Thing too many requests were done in a short time so that persisting those requests could no
 * longer catch up with the amount of requests.
 */
@Immutable
@JsonParsableException(errorCode = ThingTooManyModifyingRequestsException.ERROR_CODE)
public final class ThingTooManyModifyingRequestsException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "thing.toomanymodifyingrequests";

    private static final String MESSAGE_TEMPLATE =
            "Too many modifying requests are already outstanding to the Thing " + "with ID ''{0}''.";

    private static final String DEFAULT_DESCRIPTION = "Throttle your modifying requests to the Thing or re-structure "
            + "your Thing in multiple Things if you really need so many concurrent modifications.";

    private static final long serialVersionUID = 5780041246404245765L;


    private ThingTooManyModifyingRequestsException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.TOO_MANY_REQUESTS, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ThingTooManyModifyingRequestsException}.
     *
     * @param thingId the ID of the thing.
     * @return the builder.
     */
    public static Builder newBuilder(final ThingId thingId) {
        return new Builder(thingId);
    }

    /**
     * Constructs a new {@code ThingTooManyModifyingRequestsException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingTooManyModifyingRequestsException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ThingTooManyModifyingRequestsException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code ThingTooManyModifyingRequestsException} object with the exception message extracted from
     * the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingTooManyModifyingRequestsException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ThingTooManyModifyingRequestsException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link ThingTooManyModifyingRequestsException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ThingTooManyModifyingRequestsException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final ThingId thingId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(thingId)));
        }

        @Override
        protected ThingTooManyModifyingRequestsException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ThingTooManyModifyingRequestsException(dittoHeaders, message, description, cause, href);
        }
    }

}
