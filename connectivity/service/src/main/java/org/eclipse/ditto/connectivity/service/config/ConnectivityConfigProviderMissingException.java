/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import java.net.URI;
import java.text.MessageFormat;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;

@JsonParsableException(errorCode = ConnectivityConfigProviderMissingException.ERROR_CODE)
public final class ConnectivityConfigProviderMissingException extends DittoRuntimeException {

    public static final String ERROR_CODE = "connectivity.config.provider.missing";
    private static final String MESSAGE_TEMPLATE =
            "Failed to find a suitable ConnectivityConfigProvider implementation in candidates: <{0}>.";

    private ConnectivityConfigProviderMissingException(
            final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.INTERNAL_SERVER_ERROR, dittoHeaders, message, description, cause, href);
    }

    public static Builder newBuilder(final List<Class<? extends ConnectivityConfigProvider>> candidates) {
        return new Builder(MessageFormat.format(MESSAGE_TEMPLATE, candidates));
    }

    public static ConnectivityConfigProviderMissingException fromJson(final JsonObject jsonObject,
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
    public static final class Builder extends DittoRuntimeExceptionBuilder<ConnectivityConfigProviderMissingException> {

        private Builder() { }

        private Builder(final String message) {
            message(message);
        }

        @Override
        protected ConnectivityConfigProviderMissingException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new ConnectivityConfigProviderMissingException(dittoHeaders, message, description, cause, href);
        }

    }
}
