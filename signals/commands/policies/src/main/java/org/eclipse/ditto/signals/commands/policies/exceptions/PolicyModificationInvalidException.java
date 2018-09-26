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
 * Thrown if a {@link org.eclipse.ditto.model.policies.Policy} could not be modified as the resulting {@link
 * org.eclipse.ditto.model.policies.Policy} would be invalid. The cause of this exception is documented in the message.
 */
@Immutable
public final class PolicyModificationInvalidException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "policy.modificationinvalid";

    private static final String MESSAGE_TEMPLATE =
            "The Policy with ID ''{0}'' could not be modified as the resulting Policy would be invalid.";

    private static final String DEFAULT_DESCRIPTION =
            "There must always be at least one PolicyEntry with 'WRITE' permissions on resource 'policy:/'.";

    private static final long serialVersionUID = -3418662685041958745L;

    private PolicyModificationInvalidException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.FORBIDDEN, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyModificationInvalidException}.
     *
     * @param policyId the identifier of the Policy.
     * @return the builder.
     */
    public static Builder newBuilder(final String policyId) {
        return new Builder(policyId);
    }

    /**
     * Constructs a new {@code PolicyModificationInvalidException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyModificationInvalidException.
     */
    public static PolicyModificationInvalidException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {

        return fromMessage(message, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code PolicyModificationInvalidException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param description the detailed description which may be {@code null}.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyModificationInvalidException.
     */
    public static PolicyModificationInvalidException fromMessage(final String message,
            @Nullable final String description, final DittoHeaders dittoHeaders) {

        final DittoRuntimeExceptionBuilder<PolicyModificationInvalidException> builder = new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message);
        if (description != null) {
            return builder.description(description).build();
        } else {
            return builder.build();
        }
    }

    /**
     * Constructs a new {@code PolicyModificationInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyModificationInvalidException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static PolicyModificationInvalidException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), readDescription(jsonObject).orElse(null), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link PolicyModificationInvalidException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyModificationInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String policyId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, policyId));
        }

        @Override
        protected PolicyModificationInvalidException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new PolicyModificationInvalidException(dittoHeaders, message, description, cause, href);
        }
    }

}
