/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.time.Instant;

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
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Thrown if historical data of the Policy was either not present in Ditto at all or if the requester had insufficient
 * permissions to access it.
 *
 * @since 3.2.0
 */
@Immutable
@JsonParsableException(errorCode = PolicyHistoryNotAccessibleException.ERROR_CODE)
public final class PolicyHistoryNotAccessibleException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "policy.history.notfound";

    private static final String MESSAGE_TEMPLATE =
            "The Policy with ID ''{0}'' at revision ''{1}'' could not be found or requester had insufficient " +
                    "permissions to access it.";

    private static final String MESSAGE_TEMPLATE_TS =
            "The Policy with ID ''{0}'' at timestamp ''{1}'' could not be found or requester had insufficient " +
                    "permissions to access it.";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the ID of your requested Policy was correct, you have sufficient permissions and ensure that the " +
                    "asked for revision/timestamp does not exceed the history-retention-duration.";

    private static final long serialVersionUID = 4242422323239998882L;

    private PolicyHistoryNotAccessibleException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    private static String getMessage(final PolicyId policyId, final long revision) {
        checkNotNull(policyId, "policyId");
        return MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(policyId), String.valueOf(revision));
    }

    private static String getMessage(final PolicyId policyId, final Instant timestamp) {
        checkNotNull(policyId, "policyId");
        checkNotNull(timestamp, "timestamp");
        return MessageFormat.format(MESSAGE_TEMPLATE_TS, String.valueOf(policyId), timestamp.toString());
    }

    /**
     * A mutable builder for a {@code PolicyHistoryNotAccessibleException}.
     *
     * @param policyId the ID of the policy.
     * @param revision the asked for revision of the policy.
     * @return the builder.
     * @throws NullPointerException if {@code policyId} is {@code null}.
     */
    public static Builder newBuilder(final PolicyId policyId, final long revision) {
        return new Builder(policyId, revision);
    }

    /**
     * A mutable builder for a {@code PolicyHistoryNotAccessibleException}.
     *
     * @param policyId the ID of the policy.
     * @param timestamp the asked for timestamp of the policy.
     * @return the builder.
     * @throws NullPointerException if {@code policyId} is {@code null}.
     */
    public static Builder newBuilder(final PolicyId policyId, final Instant timestamp) {
        return new Builder(policyId, timestamp);
    }

    /**
     * Constructs a new {@code PolicyHistoryNotAccessibleException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyHistoryNotAccessibleException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static PolicyHistoryNotAccessibleException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code PolicyHistoryNotAccessibleException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyHistoryNotAccessibleException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static PolicyHistoryNotAccessibleException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyHistoryNotAccessibleException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyHistoryNotAccessibleException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final PolicyId policyId, final long revision) {
            this();
            message(PolicyHistoryNotAccessibleException.getMessage(policyId, revision));
        }

        private Builder(final PolicyId policyId, final Instant timestamp) {
            this();
            message(PolicyHistoryNotAccessibleException.getMessage(policyId, timestamp));
        }

        @Override
        protected PolicyHistoryNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new PolicyHistoryNotAccessibleException(dittoHeaders, message, description, cause, href);
        }

    }

}
