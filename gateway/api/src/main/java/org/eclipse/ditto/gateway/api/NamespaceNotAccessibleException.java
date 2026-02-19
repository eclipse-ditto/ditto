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
package org.eclipse.ditto.gateway.api;

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

/**
 * This exception indicates that access to a specific namespace is not allowed based on the configured
 * namespace access control rules.
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableException(errorCode = NamespaceNotAccessibleException.ERROR_CODE)
public final class NamespaceNotAccessibleException extends DittoRuntimeException implements GatewayException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "namespace.notaccessible";

    private static final String DEFAULT_MESSAGE = "Access to the namespace is not allowed.";

    private static final String MESSAGE_TEMPLATE = "Access to namespace ''{0}'' is not allowed.";

    private static final String DEFAULT_DESCRIPTION =
            "Check if your credentials provide access to the requested namespace.";

    private static final long serialVersionUID = 8238620849172837182L;

    private NamespaceNotAccessibleException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.FORBIDDEN, dittoHeaders, message, description, cause, href);
    }

    /**
     * Constructs a new {@code NamespaceNotAccessibleException} for the given namespace.
     *
     * @param namespace the namespace that is not accessible.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new NamespaceNotAccessibleException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static NamespaceNotAccessibleException forNamespace(final String namespace,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(MessageFormat.format(MESSAGE_TEMPLATE, namespace))
                .description(DEFAULT_DESCRIPTION)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * Constructs a new {@code NamespaceNotAccessibleException} from the given message.
     *
     * @param message detail message.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new NamespaceNotAccessibleException.
     */
    public static NamespaceNotAccessibleException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code NamespaceNotAccessibleException} from the given JSON object.
     *
     * @param jsonObject the JSON to read the error message from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new NamespaceNotAccessibleException.
     */
    public static NamespaceNotAccessibleException fromJson(final JsonObject jsonObject,
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
     * A mutable builder with a fluent API for a {@link NamespaceNotAccessibleException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<NamespaceNotAccessibleException> {

        private Builder() {
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected NamespaceNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new NamespaceNotAccessibleException(dittoHeaders, message, description, cause, href);
        }
    }
}
