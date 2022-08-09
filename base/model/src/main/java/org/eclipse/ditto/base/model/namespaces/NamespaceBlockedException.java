/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.namespaces;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.exceptions.GeneralException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Thrown when a namespace is blocked.
 */
@Immutable
@JsonParsableException(errorCode = NamespaceBlockedException.ERROR_CODE)
public final class NamespaceBlockedException extends DittoRuntimeException implements GeneralException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "namespace.blocked";

    private static final String DEFAULT_MESSAGE = "Namespace is not available due to an ongoing operation.";

    private static final String MESSAGE_TEMPLATE = "Namespace ''{0}'' is not available due to an ongoing operation.";

    private static final String DEFAULT_DESCRIPTION = "Please try again later.";

    private static final long serialVersionUID = -778531563964056275L;

    private NamespaceBlockedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatus.CONFLICT, dittoHeaders, message, description, cause, href);
    }

    /**
     * Create a builder of this exception.
     *
     * @param namespace the namespace being blocked.
     * @return a builder of this exception with default message.
     */
    public static DittoRuntimeExceptionBuilder<NamespaceBlockedException> newBuilder(final String namespace) {
        return new Builder()
                .message(MessageFormat.format(MESSAGE_TEMPLATE, namespace))
                .description(DEFAULT_DESCRIPTION);
    }

    /**
     * Deserialize from JSON.
     *
     * @param jsonObject This exception in JSON format.
     * @param dittoHeaders Ditto headers.
     * @return Deserialized exception.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static NamespaceBlockedException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        // deserialize message and description for delivery to client.
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
    }

    /**
     * Returns a mutable builder for this exception.
     *
     * @return the builder.
     */
    public DittoRuntimeExceptionBuilder<NamespaceBlockedException> toBuilder() {
        return new Builder()
                .dittoHeaders(getDittoHeaders())
                .message(getMessage())
                .description(getDescription().orElse(DEFAULT_DESCRIPTION))
                .cause(getCause())
                .href(getHref().orElse(null));
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
     * A mutable builder with a fluent API.
     */
    @NotThreadSafe
    private static final class Builder extends DittoRuntimeExceptionBuilder<NamespaceBlockedException> {

        private Builder() {message(DEFAULT_MESSAGE).description(DEFAULT_DESCRIPTION);}

        @Override
        protected NamespaceBlockedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new NamespaceBlockedException(dittoHeaders, message, description, cause, href);
        }

    }

}
