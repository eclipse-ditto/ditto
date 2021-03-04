/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.model.policies.PolicyException;

/**
 * Thrown if a {@link org.eclipse.ditto.model.policies.PolicyImport} should be modified which was {@code protected}
 * and therefore can never be modified/deleted.
 */
@Immutable
public final class PolicyImportProtectedException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "import.protected";

    private static final String MESSAGE_TEMPLATE =
            "The imported Policy ''{0}'' of Policy with ID ''{1}'' is protected and can not be modified.";

    private static final String DEFAULT_DESCRIPTION = "If you want to modify only parts of the Policy, use a more " +
            "specific path to update.";

    private static final long serialVersionUID = -6327947924178290474L;


    private PolicyImportProtectedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyImportProtectedException}.
     *
     * @param importedPolicyId the ID of the imported Policy.
     * @param policyId the ID of the Policy.
     * @return the builder.
     */
    public static Builder newBuilder(final String importedPolicyId, final String policyId) {
        return new Builder(importedPolicyId, policyId);
    }

    /**
     * Constructs a new {@code PolicyImportProtectedException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyImportProtectedException.
     */
    public static PolicyImportProtectedException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code PolicyImportProtectedException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyImportProtectedException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static PolicyImportProtectedException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link PolicyImportProtectedException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyImportProtectedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String importedPolicyId, final String policyId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, importedPolicyId, policyId));
        }

        @Override
        protected PolicyImportProtectedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new PolicyImportProtectedException(dittoHeaders, message, description, cause, href);
        }
    }

}
