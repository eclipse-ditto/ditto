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
package org.eclipse.ditto.model.policies.id;

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
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PolicyException;

/**
 * Thrown if the Policy's ID is not valid (for example if it does not comply to the Policy ID REGEX).
 */
@Immutable
@JsonParsableException(errorCode = PolicyIdInvalidException.ERROR_CODE)
public final class PolicyIdInvalidException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "id.invalid";

    private static final String MESSAGE_TEMPLATE = "Policy ID ''{0}'' is not valid!";

    private static final String DEFAULT_DESCRIPTION =
            "It must contain a namespace prefix (java package notation + a colon ':') + a name and must be a valid " +
                    "URI path segment according to RFC-3986";
    private static final String INVALID_NAMESPACE_DESCRIPTION = "The namespace prefix must conform the syntax of " +
            "the java package notation and must end with a colon (':').";
    private static final String INVALID_NAME_DESCRIPTION = "The name of the policy was not valid. It must be a valid " +
            "URI path segment according to RFC-3986";

    private static final long serialVersionUID = 8154256308793903738L;

    /**
     * Constructs a new {@code PolicyIdInvalidException} object.
     *
     * @param policyId the invalid Policy ID.
     */
    public PolicyIdInvalidException(@Nullable final String policyId) {
        this(DittoHeaders.empty(), MessageFormat.format(MESSAGE_TEMPLATE, policyId), DEFAULT_DESCRIPTION, null, null);
    }

    private PolicyIdInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyIdInvalidException}.
     *
     * @param policyId the ID of the policy.
     * @return the builder.
     */
    public static Builder newBuilder(@Nullable final CharSequence policyId) {
        return new Builder(policyId);
    }

    static Builder forInvalidName(final CharSequence policyId) {
        return new Builder(policyId).description(INVALID_NAME_DESCRIPTION);
    }

    static Builder forInvalidNamespace(final CharSequence policyId) {
        return new Builder(policyId).description(INVALID_NAMESPACE_DESCRIPTION);
    }

    /**
     * Constructs a new {@code PolicyIdInvalidException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyIdInvalidException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static PolicyIdInvalidException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * Constructs a new {@code PolicyIdInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyIdInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link JsonFields#MESSAGE} field.
     */
    public static PolicyIdInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * A mutable builder with a fluent API for a {@link PolicyIdInvalidException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyIdInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(@Nullable final CharSequence policyId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, policyId));
        }

        @Override
        public Builder description(@Nullable final String description) {
            return (Builder) super.description(description);
        }

        @Override
        protected PolicyIdInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new PolicyIdInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
