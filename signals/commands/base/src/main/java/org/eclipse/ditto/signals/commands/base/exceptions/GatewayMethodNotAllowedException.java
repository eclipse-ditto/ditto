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
import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * This exception indicates a requested HTTP method was not allowed on a API Gateway resource.
 */
@Immutable
public final class GatewayMethodNotAllowedException extends DittoRuntimeException implements GatewayException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "method.notallowed";

    private static final String MESSAGE_TEMPLATE = "The provided HTTP method ''{0}'' is not allowed on this resource.";
    private static final String DEFAULT_DESCRIPTION = "Check if you used the correct resource and method combination.";

    private static final long serialVersionUID = -4940757644888672775L;

    private GatewayMethodNotAllowedException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.METHOD_NOT_ALLOWED, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code GatewayMethodNotAllowedException}.
     *
     * @param httpMethod the HTTP method which was used but not allowed.
     * @return the builder.
     */
    public static Builder newBuilder(final String httpMethod) {
        return new Builder(httpMethod);
    }

    /**
     * Constructs a new {@code GatewayMethodNotAllowedException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new GatewayMethodNotAllowedException.
     */
    public static GatewayMethodNotAllowedException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link GatewayMethodNotAllowedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<GatewayMethodNotAllowedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String httpMethod) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, httpMethod));
        }

        @Override
        protected GatewayMethodNotAllowedException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new GatewayMethodNotAllowedException(dittoHeaders, message, description, cause, href);
        }
    }
}
