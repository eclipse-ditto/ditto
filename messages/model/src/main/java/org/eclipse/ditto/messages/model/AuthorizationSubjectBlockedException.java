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
package org.eclipse.ditto.messages.model;

import java.net.URI;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;

/**
 * Thrown if a claim message was sent too many times for a single authorization subject.
 */
@JsonParsableException(errorCode = AuthorizationSubjectBlockedException.ERROR_CODE)
public final class AuthorizationSubjectBlockedException extends DittoRuntimeException implements MessageException {

    private static final long serialVersionUID = -5816231062202863122L;

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "authorization.blocked";

    private static final String MESSAGE_TEMPLATE =
            "Your Authorization Subject is temporarily blocked. Please try again later.";
    private static final String DEFAULT_DESCRIPTION =
            "You have sent too many claim requests at once. Please wait before trying again.";

    private AuthorizationSubjectBlockedException() {
        this(DittoHeaders.empty());
    }

    private AuthorizationSubjectBlockedException(final DittoHeaders dittoHeaders) {
        super(ERROR_CODE, HttpStatus.TOO_MANY_REQUESTS, dittoHeaders, MESSAGE_TEMPLATE, DEFAULT_DESCRIPTION,
                null, null);
    }

    /**
     * Creates an {@code AuthorizationSubjectBlockedException} object.
     *
     * @return The new exception object.
     */
    public static AuthorizationSubjectBlockedException newInstance() {
        return new AuthorizationSubjectBlockedException();
    }

    /**
     * Constructs a new {@code AuthorizationSubjectBlockedException} object with its fields extracted from
     * the given JSON object.
     *
     * @param json the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new exception object.
     */
    @SuppressWarnings("squid:S1172")
    public static AuthorizationSubjectBlockedException fromJson(final JsonObject json,
            final DittoHeaders dittoHeaders) {
        // ignore the JSON object.
        return new AuthorizationSubjectBlockedException(dittoHeaders);
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
     * A mutable builder with a fluent API for a {@link AuthorizationSubjectBlockedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<AuthorizationSubjectBlockedException> {

        private Builder() {
            message(MESSAGE_TEMPLATE);
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected AuthorizationSubjectBlockedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new AuthorizationSubjectBlockedException(dittoHeaders);
        }

    }
}
