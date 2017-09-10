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
 * This exception indicates that a Solution or user has sent send too many requests in a defined timeframe.
 */
@Immutable
public class GatewayServiceTooManyRequestsException extends DittoRuntimeException implements GatewayException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "too.many.requests";

    private static final String DEFAULT_MESSAGE =
            "Your Solution is temporarily blocked. Please try again in a few minutes.";

    private static final String DEFAULT_DESCRIPTION =
            "You have sent too many requests at once. Please wait before trying again.";

    private static final long serialVersionUID = 1164235483383640723L;

    private GatewayServiceTooManyRequestsException(DittoHeaders dittoHeaders, String message, String description,
            Throwable cause, URI href) {
        super(ERROR_CODE, HttpStatusCode.TOO_MANY_REQUESTS, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code GatewayServiceTooManyRequestsException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() { return new Builder(); }

    /**
     * Constructs a new {@code GatewayServiceTooManyRequestsException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new GatewayServiceTooManyRequestsException.
     */
    public static GatewayServiceTooManyRequestsException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link GatewayServiceTooManyRequestsException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<GatewayServiceTooManyRequestsException> {

        private Builder() {
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        protected GatewayServiceTooManyRequestsException doBuild(DittoHeaders dittoHeaders, String message,
                String description, Throwable cause, URI href) {
            return new GatewayServiceTooManyRequestsException(dittoHeaders, message, description, cause, href);
        }
    }
}
