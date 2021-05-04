/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.acks;

import java.net.URI;

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
 * Thrown if any declared acknowledgement label was taken by another subscriber.
 *
 * @since 1.4.0
 */
@Immutable
@JsonParsableException(errorCode = AcknowledgementLabelNotUniqueException.ERROR_CODE)
public final class AcknowledgementLabelNotUniqueException extends DittoRuntimeException
        implements AcknowledgementException, FatalPubSubException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "label.not.unique";

    private static final String DEFAULT_MESSAGE =
            "One or more declared acknowledgement labels are taken by other subscribers.";

    private static final String DEFAULT_DESCRIPTION =
            "Please ensure all other subscribers with the declared acknowledgement labels are offline.";

    private static final AcknowledgementLabelNotUniqueException INSTANCE =
            AcknowledgementLabelNotUniqueException.newBuilder().build();

    private AcknowledgementLabelNotUniqueException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatus.CONFLICT, dittoHeaders, message, description, cause, href);
    }

    /**
     * Get a static instance of this exception with empty Ditto headers.
     *
     * @return the instance.
     */
    public static AcknowledgementLabelNotUniqueException getInstance() {
        return INSTANCE;
    }

    /**
     * A mutable builder for a {@code AcknowledgementLabelNotUniqueException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Deserialize from a JSON object.
     *
     * @param jsonObject the JSON object to deserialize.
     * @param dittoHeaders the headers.
     * @return an instance of this class.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} is missing required JSON fields.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contains unexpected value types.
     */
    public static AcknowledgementLabelNotUniqueException fromJson(final JsonObject jsonObject,
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

    @Override
    public DittoRuntimeException asDittoRuntimeException() {
        return this;
    }

    /**
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotUniqueException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<AcknowledgementLabelNotUniqueException> {

        private Builder() {
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected AcknowledgementLabelNotUniqueException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new AcknowledgementLabelNotUniqueException(dittoHeaders, message, description, cause, href);
        }

    }
}
