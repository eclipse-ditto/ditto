/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.policies.exceptions;

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
import org.eclipse.ditto.model.policies.PolicyException;


/**
 * Thrown when validating a precondition header on a Policy or one of its sub-entities leads to status
 * {@link HttpStatusCode#NOT_MODIFIED}.
 */
@Immutable
public final class PolicyPreconditionNotModifiedException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "precondition.notmodified";

    private static final String MESSAGE_TEMPLATE =
            "The comparison of precondition header ''if-none-match'' for the requested Policy resource evaluated to " +
                    "false. Expected: ''{0}'' not to match actual: ''{1}''.";

    private static final String DEFAULT_DESCRIPTION =
            "The comparison of the provided precondition header ''if-none-match'' with the current ETag value of the " +
                    "requested Policy resource evaluated to false. Check the value of your conditional header value.";

    private PolicyPreconditionNotModifiedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.NOT_MODIFIED, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@link PolicyPreconditionNotModifiedException}.
     *
     * @param expectedNotToMatch the value which was expected not to match {@code matched} value.
     * @param matched the matched value.
     * @return the builder.
     */
    public static Builder newBuilder(final String expectedNotToMatch, final String matched) {
        return new Builder(expectedNotToMatch, matched);
    }

    /**
     * Constructs a new {@link PolicyPreconditionNotModifiedException} object with the exception message extracted from
     * the given JSON object.
     *
     * @param jsonObject the JSON to read the
     * {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ConditionalHeadersNotModifiedException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static PolicyPreconditionNotModifiedException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    private static PolicyPreconditionNotModifiedException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {

        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link PolicyPreconditionNotModifiedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyPreconditionNotModifiedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String expectedNotToMatch, final String matched) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, expectedNotToMatch, matched));
        }

        @Override
        protected PolicyPreconditionNotModifiedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new PolicyPreconditionNotModifiedException(dittoHeaders, message, description, cause, href);
        }

    }
}
