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
 * Thrown if an AcknowledgementLabel is not valid, for example because it did not comply to the AcknowledgmentLabel
 * regex.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableException(errorCode = AcknowledgementLabelInvalidException.ERROR_CODE)
public final class AcknowledgementLabelInvalidException extends DittoRuntimeException
        implements AcknowledgementException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "label.invalid";

    private static final String MESSAGE_TEMPLATE = "Acknowledgement label <{0}> is invalid.";

    private static final String DEFAULT_DESCRIPTION =
            "An acknowledgement label must conform to the regular expression of Ditto documentation.";

    private static final URI DEFAULT_HREF = URI.create(
            "https://www.eclipse.org/ditto/protocol-specification-topic.html#acknowledgement-criterion-actions");

    private static final long serialVersionUID = -2385649293006205966L;

    private AcknowledgementLabelInvalidException(final DittoHeaders dittoHeaders,
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
     * Constructs a new {@code AcknowledgementLabelInvalidException} object.
     *
     * @param label the label that causes the exception.
     */
    public AcknowledgementLabelInvalidException(final CharSequence label) {
        this(DittoHeaders.empty(),
                MessageFormat.format(MESSAGE_TEMPLATE, label),
                DEFAULT_DESCRIPTION,
                null,
                DEFAULT_HREF);
    }

    /**
     * Create an {@code AcknowledgementLabelInvalidException} with custom description, hyperlink and headers.
     *
     * @param label the invalid label.
     * @param description the custom description.
     * @param href hyperlink in the exception.
     * @param dittoHeaders the headers of the exception.
     * @return the exception.
     * @since 1.4.0
     */
    public static AcknowledgementLabelInvalidException of(final CharSequence label,
            @Nullable final String description,
            @Nullable final URI href,
            final DittoHeaders dittoHeaders) {

        return new AcknowledgementLabelInvalidException(dittoHeaders, MessageFormat.format(MESSAGE_TEMPLATE, label),
                description, null, href);
    }

    /**
     * Constructs a new {@code AcknowledgementLabelInvalidException} object with the exception message extracted from
     * the given JSON object.
     *
     * @param jsonObject the JSON object representation of the returned exception.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new exception.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} misses a required field.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contained an unexpected value type.
     */
    public static AcknowledgementLabelInvalidException fromJson(final JsonObject jsonObject,
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

    @NotThreadSafe
    private static class Builder extends DittoRuntimeExceptionBuilder<AcknowledgementLabelInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
            href(DEFAULT_HREF);
        }

        @Override
        protected AcknowledgementLabelInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message, @Nullable final String description, @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new AcknowledgementLabelInvalidException(dittoHeaders, message, description, cause, href);
        }

    }
}
