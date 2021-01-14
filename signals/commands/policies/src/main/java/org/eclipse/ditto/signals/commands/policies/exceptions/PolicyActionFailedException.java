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
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PolicyException;
import org.eclipse.ditto.signals.commands.policies.actions.ActivateTokenIntegration;
import org.eclipse.ditto.signals.commands.policies.actions.DeactivateTokenIntegration;

/**
 * Thrown if an action on a policy failed.
 */
@Immutable
@JsonParsableException(errorCode = PolicyActionFailedException.ERROR_CODE)
public final class PolicyActionFailedException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "action.failed";

    private static final HttpStatusCode DEFAULT_STATUS = HttpStatusCode.INTERNAL_SERVER_ERROR;

    private static final String MESSAGE_TEMPLATE = "Failed to execute action ''{0}''.";

    private static final String DEFAULT_DESCRIPTION = "Please contact the service team.";

    private static final long serialVersionUID = 989346771203701232L;

    private PolicyActionFailedException(final DittoHeaders dittoHeaders,
            final HttpStatusCode status,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, status, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyActionFailedException}.
     *
     * @return the builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@code PolicyActionFailedException} for the action {@code activateTokenIntegration}.
     *
     * @return the builder.
     */
    public static Builder newBuilderForActivateTokenIntegration() {
        return new Builder().action(ActivateTokenIntegration.NAME);
    }

    /**
     * A mutable builder for a {@code PolicyActionFailedException} for the action {@code deactivateTokenIntegration}.
     *
     * @return the builder.
     */
    public static Builder newBuilderForDeactivateTokenIntegration() {
        return new Builder().action(DeactivateTokenIntegration.NAME);
    }

    /**
     * A mutable builder for a {@code PolicyActionFailedException} due to inappropriate authentication method.
     *
     * @param action the failed action.
     * @return the exception builder.
     */
    public static DittoRuntimeExceptionBuilder<PolicyActionFailedException>
    newBuilderForInappropriateAuthenticationMethod(final String action) {
        return new Builder().action(action)
                .status(HttpStatusCode.BAD_REQUEST)
                .description("Policy action is only possible with JWT authentication.");
    }

    /**
     * A mutable builder for when a deactivation failed due to matching permanent subjects.
     *
     * @return the builder.
     */
    public static DittoRuntimeExceptionBuilder<PolicyActionFailedException>
    newBuilderForDeactivatingPermanentSubjects() {
        return new Builder()
                .action(DeactivateTokenIntegration.NAME)
                .status(HttpStatusCode.BAD_REQUEST)
                .description("Some matched subjects are permanent.");
    }

    /**
     * Constructs a new {@code PolicyActionFailedException} object with the exception content extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the exception content from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyActionFailedException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static PolicyActionFailedException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final HttpStatusCode status = HttpStatusCode.forInt(jsonObject.getValueOrThrow(JsonFields.STATUS))
                .orElseThrow(() -> new JsonParseException("Unsupported status"));
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder().status(status));
    }

    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
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
     * A mutable builder with a fluent API for a {@link PolicyActionFailedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyActionFailedException> {

        private HttpStatusCode status = DEFAULT_STATUS;

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        /**
         * Set the error message to mention the failed action.
         *
         * @param action the failed action.
         * @return this builder.
         */
        public Builder action(final String action) {
            message(MessageFormat.format(MESSAGE_TEMPLATE, action));
            return this;
        }

        /**
         * Set the status code.
         *
         * @param status the status code.
         * @return this builder.
         */
        public Builder status(final HttpStatusCode status) {
            this.status = status;
            return this;
        }

        @Override
        protected PolicyActionFailedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new PolicyActionFailedException(dittoHeaders, status, message, description, cause, href);
        }

    }

}
