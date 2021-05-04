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

/**
 * Thrown if a WS/connectivity connection sends an acknowledgement not declared for the session/connection.
 *
 * @since 1.4.0
 */
@Immutable
@JsonParsableException(errorCode = AcknowledgementLabelNotDeclaredException.ERROR_CODE)
public final class AcknowledgementLabelNotDeclaredException extends DittoRuntimeException
        implements AcknowledgementException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "label.not.declared";

    private static final String MESSAGE_TEMPLATE =
            "Cannot send acknowledgement with label <{0}>, which is not declared.";

    private static final String DEFAULT_DESCRIPTION =
            "Each connection may only send acknowledgements whose label matches one declared for the connection.";

    private AcknowledgementLabelNotDeclaredException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE,
                HttpStatus.BAD_REQUEST,
                dittoHeaders,
                message,
                description,
                cause,
                href);
    }

    /**
     * Create an {@code AcknowledgementLabelNotDeclaredException} object.
     *
     * @param label the invalid label.
     * @param dittoHeaders the headers of the exception.
     * @return the exception.
     */
    public static AcknowledgementLabelNotDeclaredException of(final CharSequence label,
            final DittoHeaders dittoHeaders) {

        return new AcknowledgementLabelNotDeclaredException(dittoHeaders, MessageFormat.format(MESSAGE_TEMPLATE, label),
                DEFAULT_DESCRIPTION, null, null);
    }

    /**
     * Constructs a new {@code AcknowledgementLabelNotDeclaredException} object with the exception message extracted
     * from the given JSON object.
     *
     * @param jsonObject the JSON object representation of the returned exception.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new exception.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} misses a required field.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contained an unexpected value type.
     */
    public static AcknowledgementLabelNotDeclaredException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotDeclaredException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<AcknowledgementLabelNotDeclaredException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected AcknowledgementLabelNotDeclaredException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new AcknowledgementLabelNotDeclaredException(dittoHeaders, message, description, cause, href);
        }

    }

}
