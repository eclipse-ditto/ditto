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
package org.eclipse.ditto.model.base.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Thrown when a namespace is blocked.
 */
public final class NamespaceBlockedException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "namespace.blocked";

    /**
     * Status code of this exception.
     */
    private static final HttpStatusCode STATUS_CODE = HttpStatusCode.CONFLICT;

    /**
     * Default message template.
     */
    private static final String MESSAGE_TEMPLATE = "Namespace ''{0}'' is not available due to an ongoing operation.";

    private NamespaceBlockedException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
    }

    @Override
    protected DittoRuntimeExceptionBuilder<NamespaceBlockedException> getEmptyBuilder() {
        return new Builder();
    }

    /**
     * Create a builder of this exception.
     *
     * @param namespace the namespace being blocked.
     * @return a builder of this exception with default message.
     */
    public static DittoRuntimeExceptionBuilder<NamespaceBlockedException> newBuilder(final String namespace) {
        return new Builder().message(MessageFormat.format(MESSAGE_TEMPLATE, namespace));
    }

    /**
     * Deserialize from JSON.
     *
     * @param jsonObject This exception in JSON format.
     * @param dittoHeaders Ditto headers.
     * @return Deserialized exception.
     */
    public static NamespaceBlockedException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        // deserialize message and description for delivery to client.
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(jsonObject.getValue(JsonFields.MESSAGE).orElse(null))
                .description(jsonObject.getValue(JsonFields.DESCRIPTION).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API.
     */
    @NotThreadSafe
    private static final class Builder extends DittoRuntimeExceptionBuilder<NamespaceBlockedException> {

        private Builder() {}

        @Override
        protected NamespaceBlockedException doBuild(final DittoHeaders dittoHeaders, @Nullable final String message,
                @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {

            return new NamespaceBlockedException(dittoHeaders, message, description, cause, href);
        }
    }
}
