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
import org.eclipse.ditto.policies.model.PolicyException;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Thrown when a policy import cannot be removed because one or more policy entries still reference it
 * via entry-level {@code references}.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableException(errorCode = PolicyImportReferenceConflictException.ERROR_CODE)
public final class PolicyImportReferenceConflictException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "import.referenceconflict";

    private static final String MESSAGE_TEMPLATE =
            "The import ''{0}'' of Policy ''{1}'' cannot be removed because entry references still point to it.";

    private static final String DEFAULT_DESCRIPTION =
            "Remove the entry references first before deleting the import.";

    private static final String ALL_IMPORTS_MESSAGE_TEMPLATE =
            "The imports of Policy ''{0}'' cannot be removed because entry references still point to them.";

    private static final long serialVersionUID = 4823791048532659430L;

    private PolicyImportReferenceConflictException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.CONFLICT, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyImportReferenceConflictException} when deleting a single import.
     *
     * @param policyId the policy ID.
     * @param importedPolicyId the imported policy ID that is still referenced.
     * @return the builder.
     */
    public static Builder newBuilder(final PolicyId policyId, final PolicyId importedPolicyId) {
        return new Builder(policyId, importedPolicyId);
    }

    /**
     * A mutable builder for a {@code PolicyImportReferenceConflictException} when deleting all imports.
     *
     * @param policyId the policy ID.
     * @return the builder.
     */
    public static Builder newBuilderForAll(final PolicyId policyId) {
        return new Builder(policyId);
    }

    /**
     * Constructs a new {@code PolicyImportReferenceConflictException} from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers.
     * @return the new exception.
     */
    public static PolicyImportReferenceConflictException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code PolicyImportReferenceConflictException} from a message.
     *
     * @param message the detail message.
     * @param dittoHeaders the headers.
     * @return the new exception.
     */
    public static PolicyImportReferenceConflictException fromMessage(@Nullable final String message,
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
     * A mutable builder for a {@link PolicyImportReferenceConflictException}.
     */
    @NotThreadSafe
    public static final class Builder
            extends DittoRuntimeExceptionBuilder<PolicyImportReferenceConflictException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final PolicyId policyId, final PolicyId importedPolicyId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(importedPolicyId),
                    String.valueOf(policyId)));
        }

        private Builder(final PolicyId policyId) {
            this();
            message(MessageFormat.format(ALL_IMPORTS_MESSAGE_TEMPLATE, String.valueOf(policyId)));
        }

        @Override
        protected PolicyImportReferenceConflictException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new PolicyImportReferenceConflictException(dittoHeaders, message, description, cause, href);
        }
    }

}
