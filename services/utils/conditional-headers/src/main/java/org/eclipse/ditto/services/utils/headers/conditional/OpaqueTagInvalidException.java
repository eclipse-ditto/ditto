/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.headers.conditional;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

public class OpaqueTagInvalidException extends DittoRuntimeException {


    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "headers.precondition.opaquetag.invalid";

    private static final String MESSAGE_TEMPLATE =
            "The opaque tag ''{0}'' of a precondition header is invalid. " +
                    "See https://tools.ietf.org/html/rfc7232#section-2.3 for details about allowed syntax.";

    private static final String DEFAULT_DESCRIPTION = "The opaque tag of the precondition header is invalid. " +
            "See https://tools.ietf.org/html/rfc7232#section-2.3 for details about allowed syntax.";

    private OpaqueTagInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.NOT_MODIFIED, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code {@link OpaqueTagInvalidException }}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@code {@link OpaqueTagInvalidException }}.
     *
     * @param opaqueTag the opaque tag value.
     * @return the builder.
     */
    public static Builder newBuilder(final String opaqueTag) {
        return new Builder(opaqueTag);
    }

    /**
     * Constructs a new {@code ConditionalHeadersPreconditionFailedException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConditionalHeadersNotModifiedException.
     */
    public static OpaqueTagInvalidException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {

        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code ConditionalHeadersPreconditionFailedException} object with the exception message extracted from
     * the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConditionalHeadersNotModifiedException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static OpaqueTagInvalidException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link OpaqueTagInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<OpaqueTagInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String opaqueTag) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, opaqueTag));
        }

        @Override
        protected OpaqueTagInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new OpaqueTagInvalidException(dittoHeaders, message, description, cause, href);
        }

    }


}
