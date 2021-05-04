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
package org.eclipse.ditto.base.model.signals.acks;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.acks.AcknowledgementException;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;

/**
 * Thrown if an {@code AcknowledgementRequest} used a {@code correlation-id} as part of its {@code DittoHeaders} which
 * was already used for a previous very recent (minutes) AcknowledgementRequest.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableException(errorCode = AcknowledgementRequestDuplicateCorrelationIdException.ERROR_CODE)
public final class AcknowledgementRequestDuplicateCorrelationIdException extends DittoRuntimeException
        implements AcknowledgementException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "request.duplicate.correlation-id";

    static final String MESSAGE_TEMPLATE = "Correlation-id <{0}> for acknowledgement request was already used very " +
            "recently.";

    static final String DEFAULT_DESCRIPTION =
            "Please provide unique correlation-ids when requesting acknowledgements, at least unique within at least " +
                    "several minutes.";

    private static final long serialVersionUID = -8902347821367123893L;

    private AcknowledgementRequestDuplicateCorrelationIdException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE,
                HttpStatus.CONFLICT,
                dittoHeaders,
                message,
                description,
                cause,
                href);
    }

    /**
     * A mutable builder for a {@code AcknowledgementRequestDuplicateCorrelationIdException}.
     *
     * @param correlationId the duplicate correlation-id which was already used for another AcknowledgementRequest.
     * @return the builder.
     */
    public static Builder newBuilder(final String correlationId) {
        return new Builder(correlationId);
    }

    /**
     * Constructs a new {@code AcknowledgementRequestDuplicateCorrelationIdException} object with the exception message
     * extracted from the given JSON object.
     *
     * @param jsonObject the JSON object representation of the returned exception.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new exception.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static AcknowledgementRequestDuplicateCorrelationIdException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link AcknowledgementRequestDuplicateCorrelationIdException}.
     */
    @NotThreadSafe
    public static final class Builder
            extends DittoRuntimeExceptionBuilder<AcknowledgementRequestDuplicateCorrelationIdException> {

        private Builder(final String correlationId) {
            message(MessageFormat.format(MESSAGE_TEMPLATE, correlationId));
            description(DEFAULT_DESCRIPTION);
        }

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected AcknowledgementRequestDuplicateCorrelationIdException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new AcknowledgementRequestDuplicateCorrelationIdException(dittoHeaders, message, description, cause,
                    href);
        }

    }

}
