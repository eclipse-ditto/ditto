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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.gateway.api.GatewayException;
import org.eclipse.ditto.json.JsonObject;

@Immutable
@JsonParsableException(errorCode = PublicKeyProviderUnavailableException.ERROR_CODE)
public final class PublicKeyProviderUnavailableException extends DittoRuntimeException implements GatewayException {

    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "publickey.provider.unavailable";

    private static final String DEFAULT_MESSAGE = "The public key provider is not available.";

    private static final String DEFAULT_DESCRIPTION =
            "If after retry it is still unavailable, please contact the service team.";

    private static final long serialVersionUID = 1875913424069407158L;

    private PublicKeyProviderUnavailableException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatus.SERVICE_UNAVAILABLE, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PublicKeyProviderUnavailableException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code PublicKeyProviderUnavailableException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PublicKeyProviderUnavailableException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static PublicKeyProviderUnavailableException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link PublicKeyProviderUnavailableException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PublicKeyProviderUnavailableException> {

        private Builder() {
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected PublicKeyProviderUnavailableException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new PublicKeyProviderUnavailableException(dittoHeaders, message, description, cause, href);
        }

    }

}
