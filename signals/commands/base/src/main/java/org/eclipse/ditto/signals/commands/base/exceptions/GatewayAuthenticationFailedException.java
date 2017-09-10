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

import java.net.URI;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * This exception indicates that the HTTP request could not be authenticated correctly.
 */
@Immutable
public final class GatewayAuthenticationFailedException extends DittoRuntimeException implements GatewayException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "authentication.failed";

    private static final String DEFAULT_DESCRIPTION = "Check if your credentials were correct.";

    private static final long serialVersionUID = 2120928636274583181L;

    private GatewayAuthenticationFailedException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.UNAUTHORIZED, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code GatewayAuthenticationFailedException}.
     *
     * @param message the message why the authentication failed.
     * @return the builder.
     */
    public static Builder newBuilder(final String message) {
        return new Builder(message);
    }

    /**
     * Constructs a new {@code GatewayAuthenticationFailedException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new GatewayAuthenticationFailedException.
     */
    public static GatewayAuthenticationFailedException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link GatewayAuthenticationFailedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<GatewayAuthenticationFailedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String message) {
            this();
            message(message);
        }

        @Override
        protected GatewayAuthenticationFailedException doBuild(final DittoHeaders dittoHeaders,
                final String message,
                final String description, final Throwable cause, final URI href) {
            return new GatewayAuthenticationFailedException(dittoHeaders, message, description, cause, href);
        }
    }
}
