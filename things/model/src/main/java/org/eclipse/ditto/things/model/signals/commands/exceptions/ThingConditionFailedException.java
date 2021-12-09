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
 *
 * @since 2.1.0
 */
@Immutable
@JsonParsableException(errorCode = ThingConditionFailedException.ERROR_CODE)
public final class ThingConditionFailedException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "condition.failed";

    private static final String MESSAGE_CONDITION_NOT_MET =
            "The specified condition does not match the state of the requested Thing.";

    private static final String MESSAGE_FOR_INSUFFICIENT_PERMISSION =
            "The specified resource in the condition could not be found " +
                    "or the requester had insufficient permissions to access it.";

    private static final String MESSAGE_FOR_INSUFFICIENT_LIVE_CHANNEL_PERMISSION =
            "The specified resource in the live channel condition could not be found " +
                    "or the requester had insufficient permissions to access it.";

    private static final String DEFAULT_DESCRIPTION = "The provided condition did not match the actual Thing state. " +
            "Please check your provided condition.";

    private static final String DESCRIPTION_FOR_INSUFFICIENT_PERMISSION =
            "Check if you have sufficient permissions to access the specified resource (READ permission is required).";

    private static final long serialVersionUID = -7691746640682353615L;

    private ThingConditionFailedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.PRECONDITION_FAILED, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder.
     *
     * @param dittoHeaders the headers to apply for the request.
     * @return the builder.
     */
    public static DittoRuntimeExceptionBuilder<ThingConditionFailedException> newBuilder(
            final DittoHeaders dittoHeaders) {
        return new Builder().dittoHeaders(dittoHeaders);
    }

    /**
     * A mutable builder.
     *
     * @param dittoHeaders the headers to apply for the request.
     * @return the builder.
     */
    public static DittoRuntimeExceptionBuilder<ThingConditionFailedException> newBuilderForInsufficientPermission(
            final DittoHeaders dittoHeaders) {
        return newBuilder(dittoHeaders)
                .message(MESSAGE_FOR_INSUFFICIENT_PERMISSION)
                .description(DESCRIPTION_FOR_INSUFFICIENT_PERMISSION);
    }

    /**
     * A mutable builder for when the live channel condition cannot be evaluated due to a lack of permission.
     *
     * @param dittoHeaders the headers to apply for the exception.
     * @return the builder.
     * @since 2.3.0
     */
    public static DittoRuntimeExceptionBuilder<ThingConditionFailedException>
    newBuilderForInsufficientLiveChannelPermission(final DittoHeaders dittoHeaders) {
        return newBuilder(dittoHeaders)
                .message(MESSAGE_FOR_INSUFFICIENT_LIVE_CHANNEL_PERMISSION)
                .description(DESCRIPTION_FOR_INSUFFICIENT_PERMISSION);
    }

    /**
     * Constructs a new {@code ThingConditionFailedException}
     * object with the exception message extracted from the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new {@code ThingConditionFailedException}.
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
            message(MESSAGE_CONDITION_NOT_MET);
            description(DEFAULT_DESCRIPTION);
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
