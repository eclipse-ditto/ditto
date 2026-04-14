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
 * Thrown when an imports alias is not found.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableException(errorCode = ImportsAliasNotAccessibleException.ERROR_CODE)
public final class ImportsAliasNotAccessibleException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "importsalias.notfound";

    private static final String MESSAGE_TEMPLATE =
            "The imports alias ''{0}'' on the Policy with ID ''{1}'' could not be found.";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the ID of the Policy and the label of the imports alias are correct.";

    private static final long serialVersionUID = 1927340825971467L;

    private ImportsAliasNotAccessibleException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ImportsAliasNotAccessibleException}.
     *
     * @param policyId the ID of the policy.
     * @param label the label of the not found alias.
     * @return the builder.
     */
    public static Builder newBuilder(final PolicyId policyId, final Label label) {
        return new Builder(policyId, label);
    }

    /**
     * Constructs a new {@code ImportsAliasNotAccessibleException} object with given message.
     *
     * @param message detail message.
     * @param dittoHeaders the headers.
     * @return the new exception.
     */
    public static ImportsAliasNotAccessibleException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code ImportsAliasNotAccessibleException} from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers.
     * @return the new exception.
     */
    public static ImportsAliasNotAccessibleException fromJson(final JsonObject jsonObject,
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
     * A mutable builder for a {@link ImportsAliasNotAccessibleException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ImportsAliasNotAccessibleException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final PolicyId policyId, final Label label) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(label), String.valueOf(policyId)));
        }

        @Override
        protected ImportsAliasNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ImportsAliasNotAccessibleException(dittoHeaders, message, description, cause, href);
        }
    }

}
