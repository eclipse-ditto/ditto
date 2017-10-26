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
package org.eclipse.ditto.signals.commands.base.exceptions;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * This exception indicates that the sent JWT cannot be validated because the issuer is not supported.
 */
@Immutable
public final class GatewayJwtIssuerNotSupportedException extends DittoRuntimeException implements GatewayException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "jwt.issuer.notsupported";

    private static final String MESSAGE_TEMPLATE = "The JWT issuer ''{0}'' is not supported.";
    private static final String DEFAULT_DESCRIPTION = "Check if your JWT is correct.";

    private static final long serialVersionUID = -4550508438934221451L;

    private GatewayJwtIssuerNotSupportedException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code GatewayJwtIssuerNotSupportedException}.
     * @param issuer the JWT issuer which is not supported
     *
     * @return the builder.
     */
    public static Builder newBuilder(final CharSequence issuer) {
        return new Builder(requireNonNull(issuer));
    }

    /**
     * Constructs a new {@code GatewayJwtIssuerNotSupportedException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new GatewayJwtIssuerNotSupportedException.
     */
    public static GatewayJwtIssuerNotSupportedException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link GatewayJwtIssuerNotSupportedException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<GatewayJwtIssuerNotSupportedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final CharSequence issuer) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, requireNonNull(issuer)));
        }

        @Override
        protected GatewayJwtIssuerNotSupportedException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new GatewayJwtIssuerNotSupportedException(dittoHeaders, message, description, cause, href);
        }
    }
}
