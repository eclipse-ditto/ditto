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
 * Thrown if an AcknowledgementLabel was not allowed to be used (e.g. in ditto-protocol-adapter) because it is an Ditto
 * internal {@link org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel}.
 *
 * @since 1.3.0
 */
@Immutable
@JsonParsableException(errorCode = DittoAcknowledgementLabelExternalUseForbiddenException.ERROR_CODE)
public final class DittoAcknowledgementLabelExternalUseForbiddenException extends DittoRuntimeException
        implements AcknowledgementException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "ditto.acklabel.forbidden";

    private static final String MESSAGE_TEMPLATE = "The Ditto internal Acknowledgement label <{0}> is not allowed " +
            "to be issued externally!";

    private static final String DEFAULT_DESCRIPTION = "Make sure to not issue an Ditto internal Acknowledgement label.";

    private static final long serialVersionUID = 7892643278643243248L;

    private DittoAcknowledgementLabelExternalUseForbiddenException(final DittoHeaders dittoHeaders,
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
     * Constructs a new {@code DittoAcknowledgementLabelExternalUseForbiddenException} object.
     *
     * @param label the label that causes the exception.
     */
    public DittoAcknowledgementLabelExternalUseForbiddenException(final CharSequence label) {
        this(DittoHeaders.empty(),
                MessageFormat.format(MESSAGE_TEMPLATE, label),
                DEFAULT_DESCRIPTION,
                null,
                null);
    }

    /**
     * Constructs a new {@code DittoAcknowledgementLabelExternalUseForbiddenException} object with the exception
     * message extracted from the given JSON object.
     *
     * @param jsonObject the JSON object representation of the returned exception.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new exception.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} misses a required field.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contained an unexpected value type.
     */
    public static DittoAcknowledgementLabelExternalUseForbiddenException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabelExternalUseForbiddenException}.
     */
    @NotThreadSafe
    public static final class Builder
            extends DittoRuntimeExceptionBuilder<DittoAcknowledgementLabelExternalUseForbiddenException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected DittoAcknowledgementLabelExternalUseForbiddenException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new DittoAcknowledgementLabelExternalUseForbiddenException(dittoHeaders, message, description, cause,
                    href);
        }

    }
}
