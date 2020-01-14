/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.base.exceptions;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * Thrown when signal enrichment failed.
 */
@Immutable
@JsonParsableException(errorCode = SignalEnrichmentFailedException.ERROR_CODE)
public final class SignalEnrichmentFailedException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "signal.enrichment.failed";

    private static final HttpStatusCode DEFAULT_STATUS_CODE = HttpStatusCode.INTERNAL_SERVER_ERROR;

    private static final String DEFAULT_MESSAGE = "Signal enrichment failed.";

    private static final String DESCRIPTION_TEMPLATE = "Cause: {0} {1} {2}";

    private static final String DEFAULT_DESCRIPTION = "The cause is unknown. Please try again later.";


    /**
     * Constructs a new {@code SignalEnrichmentFailedException} object.
     *
     * @param dittoHeaders the headers with which this Exception should be reported back to the user.
     * @param message the detail message for later retrieval with {@link #getMessage()}.
     * @param description a description with further information about the exception.
     * @param cause the cause of the exception for later retrieval with {@link #getCause()}.
     * @param href a link to a resource which provides further information about the exception.
     * @throws NullPointerException if {@code errorCode}, {@code statusCode} or {@code dittoHeaders} is {@code null}.
     */
    private SignalEnrichmentFailedException(
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, statusCode, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code SignalEnrichmentFailedException} in case of an invalid type.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {

        return new Builder();
    }

    /**
     * Constructs a new {@code SignalEnrichmentFailedException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SignalEnrichmentFailedException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static SignalEnrichmentFailedException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .status(jsonObject.getValue(JsonFields.STATUS)
                        .flatMap(HttpStatusCode::forInt)
                        .orElse(DEFAULT_STATUS_CODE))
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * Construct a SignalEnrichmentFailedException due to a DittoRuntimeException retaining the latter's headers.
     *
     * @param cause the cause.
     * @return a SignalEnrichmentFailedException due to the cause.
     * @throws NullPointerException if the cause is {@code null}.
     */
    public static SignalEnrichmentFailedException dueTo(final DittoRuntimeException cause) {
        return newBuilder()
                .dueTo(cause)
                .dittoHeaders(cause.getDittoHeaders())
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link SignalEnrichmentFailedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<SignalEnrichmentFailedException> {

        private HttpStatusCode statusCode = DEFAULT_STATUS_CODE;

        private Builder() {
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        private Builder status(final HttpStatusCode statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        /**
         * Set the cause of the SignalEnrichmentFailedException. The status code is changed to the status code of
         * the cause. A textual representation of the cause is put in the description.
         *
         * @param cause the cause.
         * @return this builder with the cause as description.
         * @throws NullPointerException if the cause is {@code null}.
         */
        public Builder dueTo(final DittoRuntimeException cause) {
            cause(checkNotNull(cause, "cause"));
            description(MessageFormat.format(DESCRIPTION_TEMPLATE,
                    cause.getErrorCode(), cause.getMessage(), cause.getDescription()));
            statusCode = cause.getStatusCode();
            return this;
        }

        @Override
        protected SignalEnrichmentFailedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new SignalEnrichmentFailedException(statusCode, dittoHeaders, message, description, cause, href);
        }

    }

}
