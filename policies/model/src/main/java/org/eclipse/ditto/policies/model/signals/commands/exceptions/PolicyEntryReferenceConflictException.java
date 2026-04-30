/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyException;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Thrown when a policy entry cannot be removed because another entry has a local reference pointing to it.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableException(errorCode = PolicyEntryReferenceConflictException.ERROR_CODE)
public final class PolicyEntryReferenceConflictException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "entry.referenceconflict";

    private static final String MESSAGE_TEMPLATE =
            "The entry ''{0}'' of Policy ''{1}'' cannot be removed because a local reference from entry ''{2}'' " +
                    "still points to it.";

    private static final String DEFAULT_DESCRIPTION =
            "Remove the local entry references first before deleting the referenced entry.";

    private static final long serialVersionUID = 7239485102847561934L;

    private PolicyEntryReferenceConflictException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.CONFLICT, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyEntryReferenceConflictException}.
     *
     * @param policyId the policy ID.
     * @param referencedLabel the label of the entry that is still referenced.
     * @param referencingLabel the label of the entry that holds the local reference.
     * @return the builder.
     */
    public static Builder newBuilder(final PolicyId policyId, final Label referencedLabel,
            final Label referencingLabel) {
        return new Builder(policyId, referencedLabel, referencingLabel);
    }

    /**
     * Constructs a new {@code PolicyEntryReferenceConflictException} from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers.
     * @return the new exception.
     */
    public static PolicyEntryReferenceConflictException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code PolicyEntryReferenceConflictException} from a message.
     *
     * @param message the detail message.
     * @param dittoHeaders the headers.
     * @return the new exception.
     */
    public static PolicyEntryReferenceConflictException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
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
     * A mutable builder for a {@link PolicyEntryReferenceConflictException}.
     */
    @NotThreadSafe
    public static final class Builder
            extends DittoRuntimeExceptionBuilder<PolicyEntryReferenceConflictException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final PolicyId policyId, final Label referencedLabel, final Label referencingLabel) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(referencedLabel),
                    String.valueOf(policyId), String.valueOf(referencingLabel)));
        }

        @Override
        protected PolicyEntryReferenceConflictException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new PolicyEntryReferenceConflictException(dittoHeaders, message, description, cause, href);
        }
    }

}
