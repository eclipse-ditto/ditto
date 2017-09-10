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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * This exception indicates that an HTTP query is aborted because it took too long on the backend.
 */
@Immutable
public final class GatewayQueryTimeExceededException extends DittoRuntimeException implements GatewayException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "query.time.exceeded";

    private static final HttpStatusCode STATUS_CODE = HttpStatusCode.GATEWAY_TIMEOUT;

    private static final String DEFAULT_MESSAGE = "The request took too long to process.";

    private static final String DEFAULT_DESCRIPTION = "Optimize the request and try again later.";

    private GatewayQueryTimeExceededException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code GatewayQueryTimeExceededException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Deserialize a new {@code GatewayQueryTimeExceededException} object.
     *
     * @param json the json object. It is ignored because this object has no state.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new GatewayQueryTimeExceededException.
     */
    public static GatewayQueryTimeExceededException fromJson(final JsonObject json,
            final DittoHeaders dittoHeaders) {
        return new Builder().dittoHeaders(dittoHeaders).build();
    }

    /**
     * A mutable builder with a fluent API for a {@link GatewayQueryTimeExceededException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<GatewayQueryTimeExceededException> {

        private Builder() {
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected GatewayQueryTimeExceededException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new GatewayQueryTimeExceededException(dittoHeaders, message, description, cause, href);
        }
    }
}
