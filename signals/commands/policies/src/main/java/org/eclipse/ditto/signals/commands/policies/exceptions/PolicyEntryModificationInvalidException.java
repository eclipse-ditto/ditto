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
import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PolicyException;

/**
 * Thrown if a {@link org.eclipse.ditto.model.policies.PolicyEntry} could not be modified as the resulting {@link
 * org.eclipse.ditto.model.policies.Policy} would be invalid. The cause of this exception is documented in the message.
 */
@Immutable
public final class PolicyEntryModificationInvalidException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "entry.modificationinvalid";

    private static final String MESSAGE_TEMPLATE = "The PolicyEntry with Label ''{0}'' on the Policy with ID ''{1}''" +
            " could not be modified as the resulting Policy would be invalid.";

    private static final String DEFAULT_DESCRIPTION =
            "There must always be at least one PolicyEntry with 'WRITE' permissions on resource 'policy:/'.";

    private static final long serialVersionUID = -3234448123780175035L;

    private PolicyEntryModificationInvalidException(final DittoHeaders dittoHeaders,
            final String message,
            final String description,
            final Throwable cause,
            final URI href) {

        super(ERROR_CODE, HttpStatusCode.FORBIDDEN, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyEntryModificationInvalidException}.
     *
     * @param policyId the identifier of the Policy.
     * @param label the label of the PolicyEntry.
     * @return the builder.
     */
    public static Builder newBuilder(final String policyId, final CharSequence label) {
        return new Builder(policyId, label);
    }

    /**
     * Constructs a new {@code PolicyEntryModificationInvalidException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyEntryModificationInvalidException.
     */
    public static PolicyEntryModificationInvalidException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {

        return fromMessage(message, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code PolicyEntryModificationInvalidException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param description the detailed description which may be null.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyEntryModificationInvalidException.
     */
    public static PolicyEntryModificationInvalidException fromMessage(final String message, final String description,
            final DittoHeaders dittoHeaders) {

        final DittoRuntimeExceptionBuilder<PolicyEntryModificationInvalidException> builder =
                new PolicyEntryModificationInvalidException.Builder()
                        .dittoHeaders(dittoHeaders)
                        .message(message);
        if (description != null) {
            return builder.description(description).build();
        } else {
            return builder.build();
        }
    }

    /**
     * Constructs a new {@code PolicyEntryModificationInvalidException} object with the exception message extracted from
     * the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyEntryModificationInvalidException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link JsonFields#MESSAGE} field.
     */
    public static PolicyEntryModificationInvalidException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return fromMessage(readMessage(jsonObject), readDescription(jsonObject).orElse(null), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link PolicyEntryModificationInvalidException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyEntryModificationInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String policyId, final CharSequence label) {
            description(DEFAULT_DESCRIPTION);
            message(MessageFormat.format(MESSAGE_TEMPLATE, label, policyId));
        }

        @Override
        protected PolicyEntryModificationInvalidException doBuild(final DittoHeaders dittoHeaders,
                final String message,
                final String description,
                final Throwable cause,
                final URI href) {

            return new PolicyEntryModificationInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
