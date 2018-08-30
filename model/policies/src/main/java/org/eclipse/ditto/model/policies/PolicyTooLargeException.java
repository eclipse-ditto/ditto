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
package org.eclipse.ditto.model.policies;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * This exception indicates the content of a Policy is too large to be processed by a backend.
 */
@Immutable
public final class PolicyTooLargeException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = PolicyException.ERROR_CODE_PREFIX + "policy.toolarge";

    private static final String MESSAGE_TEMPLATE =
            "The size of ''{0}'' kB exceeds the maximal allowed Policy size of ''{1}'' kB.";
    private static final String DEFAULT_DESCRIPTION = "Reduce the Policy size in the bounds of the specified limit";

    private static final long serialVersionUID = 2434234324327234489L;

    private PolicyTooLargeException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.REQUEST_ENTITY_TOO_LARGE, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyTooLargeException}.
     *
     * @param actualBytes the actual amount of bytes which were too much (gt {@code maxBytes})
     * @param maxBytes the maximal allowed amount of bytes
     * @return the builder.
     */
    public static Builder newBuilder(final long actualBytes, final long maxBytes) {
        return new Builder(actualBytes, maxBytes);
    }

    /**
     * Deserialize from a JSON object.
     *
     * @param jsonObject the JSON object to deserialize.
     * @param dittoHeaders the headers.
     * @return an instance of this class.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PolicyTooLargeException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new PolicyTooLargeException.Builder()
                .loadJson(jsonObject)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link PolicyTooLargeException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyTooLargeException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final long actualBytes, final long maxBytes) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, actualBytes / 1024.0, maxBytes / 1024.0));
        }

        @Override
        protected PolicyTooLargeException doBuild(final DittoHeaders dittoHeaders, @Nullable final String message,
                @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
            return new PolicyTooLargeException(dittoHeaders, message, description, cause, href);
        }
    }
}
