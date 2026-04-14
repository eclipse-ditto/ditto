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

/**
 * Thrown when a label is used as both an imports alias and a policy entry, which is not allowed.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableException(errorCode = ImportsAliasConflictException.ERROR_CODE)
public final class ImportsAliasConflictException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "importsalias.conflict";

    private static final String MESSAGE_TEMPLATE =
            "The label ''{0}'' cannot be used as both an imports alias and a policy entry.";

    private static final String DEFAULT_DESCRIPTION =
            "A label must be used either as an imports alias or as a policy entry, not both. " +
                    "Remove one before creating the other.";

    private static final long serialVersionUID = -2684503262817846830L;

    private ImportsAliasConflictException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.CONFLICT, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ImportsAliasConflictException}.
     *
     * @param label the label that conflicts.
     * @return the builder.
     */
    public static Builder newBuilder(final Label label) {
        return new Builder(label);
    }

    /**
     * Constructs a new {@code ImportsAliasConflictException} object with given message.
     *
     * @param message detail message.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ImportsAliasConflictException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static ImportsAliasConflictException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code ImportsAliasConflictException} from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers.
     * @return the new ImportsAliasConflictException.
     */
    public static ImportsAliasConflictException fromJson(final JsonObject jsonObject,
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
     * A mutable builder for a {@link ImportsAliasConflictException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ImportsAliasConflictException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final Label label) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(label)));
        }

        @Override
        protected ImportsAliasConflictException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ImportsAliasConflictException(dittoHeaders, message, description, cause, href);
        }
    }

}
