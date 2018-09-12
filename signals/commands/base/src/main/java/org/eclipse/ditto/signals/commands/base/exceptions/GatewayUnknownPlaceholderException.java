/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.base.exceptions;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * This exception indicates that a request contains a placeholder which cannot be resolved.
 */
@Immutable
public final class GatewayUnknownPlaceholderException extends DittoRuntimeException implements GatewayException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "placeholder.unknown";

    private static final String MESSAGE_TEMPLATE = "The placeholder ''{0}'' is unknown.";
    private static final String DESCRIPTION_TEMPLATE = "Please use one of the supported placeholders: {0}.";

    private static final long serialVersionUID = -8724890154457417912L;

    private GatewayUnknownPlaceholderException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@link GatewayUnknownPlaceholderException}.
     *
     * @param unknownPlaceholder the unknown placeholder.
     * @param supportedPlaceholders the supported placeholders.
     *
     * @return the builder.
     */
    public static Builder newBuilder(final CharSequence unknownPlaceholder,
            final List<CharSequence> supportedPlaceholders) {
        return new Builder(requireNonNull(unknownPlaceholder), requireNonNull(supportedPlaceholders));
    }

    /**
     * Constructs a new {@link GatewayUnknownPlaceholderException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new {@link GatewayUnknownPlaceholderException}.
     */
    public static GatewayUnknownPlaceholderException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link GatewayUnknownPlaceholderException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<GatewayUnknownPlaceholderException> {

        private Builder() {}

        private Builder(final CharSequence issuer, final List<CharSequence> supportedPlaceHolders) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, requireNonNull(issuer)));
            requireNonNull(supportedPlaceHolders);
            final String supportedPlaceHoldersStr = supportedPlaceHolders.stream()
                    .map(placeholder -> "'" + placeholder + "'")
                    .collect(Collectors.joining(", "));
            description(MessageFormat.format(DESCRIPTION_TEMPLATE, supportedPlaceHoldersStr));
        }

        @Override
        protected GatewayUnknownPlaceholderException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new GatewayUnknownPlaceholderException(dittoHeaders, message, description, cause, href);
        }
    }
}
