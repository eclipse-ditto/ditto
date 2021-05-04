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
package org.eclipse.ditto.policies.model.signals.commands.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.policies.model.PolicyException;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Thrown if a {@link org.eclipse.ditto.policies.model.PolicyEntry} could not be modified as the resulting {@link
 * org.eclipse.ditto.policies.model.Policy} would be invalid. The cause of this exception is documented in the message.
 */
@Immutable
@JsonParsableException(errorCode = PolicyEntryModificationInvalidException.ERROR_CODE)
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
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.FORBIDDEN, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyEntryModificationInvalidException}.
     *
     * @param policyId the identifier of the Policy.
     * @param label the label of the PolicyEntry.
     * @return the builder.
     */
    public static Builder newBuilder(final PolicyId policyId, final CharSequence label) {
        return new Builder(policyId, label);
    }

    /**
     * Constructs a new {@code PolicyEntryModificationInvalidException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyEntryModificationInvalidException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static PolicyEntryModificationInvalidException fromMessage(@Nullable final String message,
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
     * @throws NullPointerException if {@code message} or {@code dittoHeaders} is {@code null}.
     */
    public static PolicyEntryModificationInvalidException fromMessage(final String message,
            @Nullable final String description, final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders,
                new Builder().description(() -> description != null ? description : DEFAULT_DESCRIPTION));
    }

    /**
     * Constructs a new {@code PolicyEntryModificationInvalidException} object with the exception message extracted from
     * the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyEntryModificationInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static PolicyEntryModificationInvalidException fromJson(final JsonObject jsonObject,
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

    /**
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyEntryModificationInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyEntryModificationInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final PolicyId policyId, final CharSequence label) {
            description(DEFAULT_DESCRIPTION);
            message(MessageFormat.format(MESSAGE_TEMPLATE, label, String.valueOf(policyId)));
        }

        @Override
        protected PolicyEntryModificationInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new PolicyEntryModificationInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
