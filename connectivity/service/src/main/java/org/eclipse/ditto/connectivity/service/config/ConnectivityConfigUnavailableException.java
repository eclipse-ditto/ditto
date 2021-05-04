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
package org.eclipse.ditto.connectivity.service.config;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.connectivity.model.ConnectivityException;

/**
 * Thrown if a {@link ConnectivityConfig} could not be loaded succesfully.
 */
@Immutable
@JsonParsableException(errorCode = ConnectivityConfigUnavailableException.ERROR_CODE)
public class ConnectivityConfigUnavailableException extends DittoRuntimeException implements ConnectivityException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "connectivity.config.unavailable";

    private static final String MESSAGE_TEMPLATE = "The Connectivity Config for ''{0}'' is unavailable.";

    private static final String DEFAULT_DESCRIPTION = "Please contact the service team.";

    private static final long serialVersionUID = 7347021223383127587L;

    private ConnectivityConfigUnavailableException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ConnectivityConfigUnavailableException}.
     *
     * @param entityId the id of the entity.
     * @return the builder.
     */
    public static Builder newBuilder(final EntityId entityId) {
        return new Builder(entityId);
    }

    /**
     * A mutable builder for a {@code ConnectivityConfigUnavailableException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code ConnectivityConfigUnavailableException} object with the exception message extracted from
     * the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE}
     * field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConnectivityConfigUnavailableException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static ConnectivityConfigUnavailableException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(getMessage())
                .description(getDescription().orElse(DEFAULT_DESCRIPTION))
                .cause(getCause())
                .href(getHref().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link ConnectivityConfigUnavailableException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ConnectivityConfigUnavailableException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final EntityId entityId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(entityId)));
        }

        @Override
        protected ConnectivityConfigUnavailableException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ConnectivityConfigUnavailableException(dittoHeaders, message, description, cause, href);
        }
    }

}
