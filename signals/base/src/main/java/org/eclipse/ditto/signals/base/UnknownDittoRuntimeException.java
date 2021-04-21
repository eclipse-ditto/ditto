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
package org.eclipse.ditto.signals.base;

import java.net.URI;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;

/**
 * This exception can be used to deserialize DittoRuntimeExceptions which are not on the classpath.
 * It's a fallback and should only be used at the edges of Ditto.
 * Do not make this exception public.
 */
@JsonParsableException(errorCode = UnknownDittoRuntimeException.FALLBACK_ERROR_CODE)
final class UnknownDittoRuntimeException extends DittoRuntimeException {

    public static final String FALLBACK_ERROR_CODE = "unknown:unknown";

    protected UnknownDittoRuntimeException(final String errorCode,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(errorCode, httpStatus, dittoHeaders, message, description, cause, href);
    }

    public static UnknownDittoRuntimeException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final HttpStatus httpStatus = getHttpStatus(jsonObject).orElse(HttpStatus.INTERNAL_SERVER_ERROR);
        final String errorCode =
                jsonObject.getValue(DittoRuntimeException.JsonFields.ERROR_CODE).orElse(FALLBACK_ERROR_CODE);
        final String errorMessage =
                jsonObject.getValue(DittoRuntimeException.JsonFields.MESSAGE).orElse("An unknown error occurred");
        final String errorDescription = jsonObject.getValue(DittoRuntimeException.JsonFields.DESCRIPTION).orElse("");
        @Nullable final URI href = DittoRuntimeException.getHref(jsonObject).orElse(null);
        return new UnknownDittoRuntimeException(errorCode, httpStatus, dittoHeaders, errorMessage, errorDescription,
                null, href);
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Builder(getErrorCode(), getHttpStatus())
                .message(getMessage())
                .description(getDescription().orElse(null))
                .cause(getCause())
                .href(getHref().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    public static class Builder extends DittoRuntimeExceptionBuilder<UnknownDittoRuntimeException> {

        private final String errorCode;
        private final HttpStatus httpStatus;

        private Builder(final String errorCode, final HttpStatus httpStatus) {
            this.errorCode = errorCode;
            this.httpStatus = httpStatus;
        }

        @Override
        protected UnknownDittoRuntimeException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message, @Nullable final String description, @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new UnknownDittoRuntimeException(errorCode, httpStatus, dittoHeaders, message, description,
                    cause, href);
        }
    }

}
