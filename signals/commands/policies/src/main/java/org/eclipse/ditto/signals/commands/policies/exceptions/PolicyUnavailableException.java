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
import org.eclipse.ditto.model.base.json.JsonParsableException;
import org.eclipse.ditto.model.policies.PolicyException;
import org.eclipse.ditto.model.policies.PolicyId;

/**
 * Thrown if the Policy exists but is not available at the moment.
 */
@Immutable
@JsonParsableException(errorCode = PolicyUnavailableException.ERROR_CODE)
public final class PolicyUnavailableException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "policy.unavailable";

    private static final String MESSAGE_TEMPLATE =
            "The Policy with ID ''{0}'' is not available, please try again later.";

    private static final String DEFAULT_DESCRIPTION = "The requested Policy is temporarily not available.";

    private static final long serialVersionUID = 1987286804137290070L;


    private PolicyUnavailableException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.SERVICE_UNAVAILABLE, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyUnavailableException}.
     *
     * @param policyId the ID of the policy.
     * @return the builder.
     */
    public static Builder newBuilder(final PolicyId policyId) {
        return new Builder(policyId);
    }

    /**
     * Constructs a new {@code PolicyUnavailableException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyUnavailableException.
     */
    public static PolicyUnavailableException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code PolicyUnavailableException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyUnavailableException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static PolicyUnavailableException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link PolicyUnavailableException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyUnavailableException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final PolicyId policyId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(policyId)));
        }

        @Override
        protected PolicyUnavailableException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new PolicyUnavailableException(dittoHeaders, message, description, cause, href);
        }
    }

}
