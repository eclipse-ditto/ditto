/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyException;

/**
 * Thrown if a {@link org.eclipse.ditto.policies.model.PolicyImport} was either not present or the requester had
 * insufficient permissions to access it.
 *
 * @since 3.1.0
 */
@Immutable
@JsonParsableException(errorCode = PolicyImportNotAccessibleException.ERROR_CODE)
public final class PolicyImportNotAccessibleException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "import.notfound";

    private static final String MESSAGE_TEMPLATE =
            "The Policy import ''{0}'' of Policy ''{1}''" +
                    " could not be found or requester had insufficient permissions to access it.";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the ID of the Policy and the imported Policy was correct and you have sufficient permissions.";

    private static final long serialVersionUID = 3798954052492368034L;

    private PolicyImportNotAccessibleException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyImportNotAccessibleException} in case the requester does not have required
     * permissions on a policy import.
     *
     * @param policyId the identifier of the Policy.
     * @param importedPolicyId the PolicyId of the PolicyImport.
     * @return the builder.
     */
    public static Builder newBuilder(final CharSequence policyId, final CharSequence importedPolicyId) {
        return new Builder(importedPolicyId, policyId);
    }

    /**
     * Constructs a new {@code PolicyImportNotAccessibleException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyImportNotAccessibleException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static PolicyImportNotAccessibleException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code PolicyImportNotAccessibleException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyImportNotAccessibleException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static PolicyImportNotAccessibleException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@code PolicyImportNotAccessibleException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyImportNotAccessibleException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final CharSequence importedPolicyId, final CharSequence policyId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, importedPolicyId, policyId));
        }

        @Override
        protected PolicyImportNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new PolicyImportNotAccessibleException(dittoHeaders, message, description, cause, href);
        }

    }

}
