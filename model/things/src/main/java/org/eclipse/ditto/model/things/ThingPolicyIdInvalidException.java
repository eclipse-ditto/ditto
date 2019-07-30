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
package org.eclipse.ditto.model.things;

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

/**
 * Thrown if the ID set as policyId of the {@link Thing} is not valid
 * (for example if it does not comply to the Entity ID REGEX).
 */
@Immutable
@JsonParsableException(errorCode = ThingPolicyIdInvalidException.ERROR_CODE)
public final class ThingPolicyIdInvalidException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "policy.id.invalid";

    private static final String MESSAGE_TEMPLATE = "Policy ID ''{0}'' is not valid!";

    private static final String DEFAULT_DESCRIPTION =
            "It must contain a namespace prefix (java package notation + a colon ':') + ID and must be a valid URI " +
                    "path segment according to RFC-3986";

    private static final long serialVersionUID = 8494286958733203132L;

    /**
     * Constructs a new {@code ThingPolicyIdInvalidException} object.
     *
     * @param policyId the invalid Policy ID.
     */
    public ThingPolicyIdInvalidException(@Nullable final String policyId) {
        this(DittoHeaders.empty(), MessageFormat.format(MESSAGE_TEMPLATE, policyId), DEFAULT_DESCRIPTION, null, null);
    }

    private ThingPolicyIdInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ThingPolicyIdInvalidException}.
     *
     * @param policyId the ID of the policy.
     * @return the builder.
     */
    public static Builder newBuilder(@Nullable final CharSequence policyId) {
        return new Builder(policyId);
    }

    /**
     * Constructs a new {@code ThingPolicyIdInvalidException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingPolicyIdInvalidException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ThingPolicyIdInvalidException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * Constructs a new {@code ThingPolicyIdInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingPolicyIdInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link JsonFields#MESSAGE} field.
     */
    public static ThingPolicyIdInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link ThingPolicyIdInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ThingPolicyIdInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(@Nullable final CharSequence policyId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, policyId));
        }

        @Override
        protected ThingPolicyIdInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ThingPolicyIdInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
