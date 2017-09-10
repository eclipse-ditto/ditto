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
package org.eclipse.ditto.signals.commands.policies.exceptions;

import java.net.URI;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PolicyException;

/**
 * Thrown if either for a REST PUT request for creating a Policy it was tried to set an explicit {@code policyId}
 * in the JSON body.
 */
@Immutable
public final class PolicyIdNotExplicitlySettableException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "id.notsettable";

    private static final String MESSAGE_TEMPLATE_PUT =
            "The Policy ID in the request body is not equal to the Policy ID in the request URL.";

    private static final String DEFAULT_DESCRIPTION_PUT =
            "Either delete the Policy ID from the request body or use the same Policy ID as in the request URL.";


    private PolicyIdNotExplicitlySettableException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    private static final long serialVersionUID = 2497658333219185859L;

    /**
     * A mutable builder for a {@code PolicyIdNotExplicitlySettableException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code PolicyIdNotExplicitlySettableException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyIdNotExplicitlySettableException.
     */
    public static PolicyIdNotExplicitlySettableException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * Constructs a new {@code PolicyIdNotExplicitlySettableException} object with the exception message extracted from
     * the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyIdNotExplicitlySettableException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link JsonFields#MESSAGE} field.
     */
    public static PolicyIdNotExplicitlySettableException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link PolicyIdNotExplicitlySettableException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyIdNotExplicitlySettableException> {

        private Builder() {
            message(MESSAGE_TEMPLATE_PUT);
            description(DEFAULT_DESCRIPTION_PUT);
        }

        @Override
        protected PolicyIdNotExplicitlySettableException doBuild(final DittoHeaders dittoHeaders,
                final String message,
                final String description, final Throwable cause, final URI href) {
            return new PolicyIdNotExplicitlySettableException(dittoHeaders, message, description, cause, href);
        }
    }


}
