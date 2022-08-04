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
package org.eclipse.ditto.base.model.exceptions;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Thrown when signal enrichment failed.
 */
@Immutable
@JsonParsableException(errorCode = SignalEnrichmentFailedException.ERROR_CODE)
public final class SignalEnrichmentFailedException extends DittoRuntimeException implements GeneralException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "signal.enrichment.failed";

    private static final HttpStatus DEFAULT_HTTP_STATUS = HttpStatus.INTERNAL_SERVER_ERROR;

    private static final String DEFAULT_MESSAGE = "Signal enrichment failed.";

    private static final String DESCRIPTION_TEMPLATE = "Cause: {0} {1} {2}";

    private static final String DEFAULT_DESCRIPTION = "The cause is unknown. Please try again later.";

    private static final long serialVersionUID = -9012995799489161220L;

    private SignalEnrichmentFailedException(
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, httpStatus, dittoHeaders, message, description, cause, href);
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
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SignalEnrichmentFailedException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected.
     */
    public static SignalEnrichmentFailedException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, newBuilder()
                .status(jsonObject.getValue(JsonFields.STATUS)
                        .flatMap(HttpStatus::tryGetInstance)
                        .orElse(DEFAULT_HTTP_STATUS)));
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
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.base.model.exceptions.SignalEnrichmentFailedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<SignalEnrichmentFailedException> {

        private HttpStatus httpStatus = DEFAULT_HTTP_STATUS;

        private Builder() {
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        private Builder status(final HttpStatus httpStatus) {
            this.httpStatus = httpStatus;
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
            description(MessageFormat.format(DESCRIPTION_TEMPLATE, cause.getErrorCode(), cause.getMessage(),
                    cause.getDescription()));
            httpStatus = cause.getHttpStatus();
            return this;
        }

        @Override
        protected SignalEnrichmentFailedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new SignalEnrichmentFailedException(httpStatus, dittoHeaders, message, description, cause, href);
        }

    }

}
