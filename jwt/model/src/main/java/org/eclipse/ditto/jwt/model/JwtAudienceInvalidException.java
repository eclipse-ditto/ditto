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
package org.eclipse.ditto.jwt.model;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;

@JsonParsableException(errorCode = JwtAudienceInvalidException.ERROR_CODE)
public final class JwtAudienceInvalidException extends DittoRuntimeException implements JwtException {

    /**
     * Error code of this exception.
     */
    static final String ERROR_CODE = ERROR_CODE_PREFIX + "audience.invalid";

    private static final String MESSAGE = "The value for the <aud> field is not valid.";
    private static final String DESCRIPTION_TEMPLATE = "The <aud> field was expected to be either a string or an " +
            "array of strings. The actual value was <{0}>.";

    private static final long serialVersionUID = -7613054868240460649L;

    private JwtAudienceInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for this exception.
     *
     * @param audValue the value the aud key.
     * @return the builder.
     */
    public static Builder newBuilder(final JsonValue audValue) {
        return new Builder(audValue);
    }

    /**
     * A mutable builder for this exception.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code JwtAudienceInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new JwtAudienceInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static JwtAudienceInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link JwtAudienceInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<JwtAudienceInvalidException> {

        private Builder() {
            message(MESSAGE).description(DESCRIPTION_TEMPLATE);
        }

        private Builder(final JsonValue audValue) {
            this();
            description(MessageFormat.format(DESCRIPTION_TEMPLATE, audValue));
        }

        @Override
        protected JwtAudienceInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new JwtAudienceInvalidException(dittoHeaders, message, description, cause, href);
        }
    }
}
