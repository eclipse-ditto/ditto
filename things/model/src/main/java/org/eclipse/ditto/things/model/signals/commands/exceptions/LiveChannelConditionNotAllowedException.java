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
 * Thrown when the live channel condition was used for e.g. modify commands where it is not supported.
 *
 * @since 2.3.0
 */
@Immutable
@JsonParsableException(errorCode = LiveChannelConditionNotAllowedException.ERROR_CODE)
public final class LiveChannelConditionNotAllowedException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "live.channelcondition.notallowed";

    private static final String DEFAULT_MESSAGE =
            "The specified 'live-channel-condition' is only allowed to be used for retrieving API calls.";

    private static final String DEFAULT_DESCRIPTION = "For modifying API calls the 'live-channel-condition' cannot " +
            "be used, please remove the condition.";

    private static final long serialVersionUID = 1239673920456383015L;

    private LiveChannelConditionNotAllowedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatus.METHOD_NOT_ALLOWED, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@link LiveChannelConditionNotAllowedException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@link LiveChannelConditionNotAllowedException}
     * object with the exception message extracted from the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new {@link LiveChannelConditionNotAllowedException}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static LiveChannelConditionNotAllowedException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link LiveChannelConditionNotAllowedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<LiveChannelConditionNotAllowedException> {

        private Builder() {
            this(DEFAULT_DESCRIPTION);
        }

        private Builder(final String description) {
            message(DEFAULT_MESSAGE);
            if (!description.equals("")) {
                description(description);
            }
        }

        @Override
        protected LiveChannelConditionNotAllowedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new LiveChannelConditionNotAllowedException(dittoHeaders, message, description, cause, href);
        }

    }

}
