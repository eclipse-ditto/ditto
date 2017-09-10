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
package org.eclipse.ditto.signals.commands.batch.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.base.WithId;

/**
 * Thrown if a batch is already being executed by the coordinator.
 */
@Immutable
public final class BatchAlreadyExecutingException extends DittoRuntimeException implements BatchException, WithId {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "batch.executing";

    private static final String MESSAGE_TEMPLATE =
            "The batch with ID ''{0}'' is already being executed.";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the requested batch has timed out and try again in a while.";

    private static final long serialVersionUID = 7202828974424543635L;

    private BatchAlreadyExecutingException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    @Override
    public String getId() {
        return getDittoHeaders().getCorrelationId().orElse(null);
    }

    /**
     * A mutable builder for a {@code BatchAlreadyExecutingException}.
     *
     * @param batchId the ID of the batch.
     * @return the builder.
     */
    public static Builder newBuilder(final String batchId) {
        return new Builder(batchId);
    }

    /**
     * Constructs a new {@code BatchAlreadyExecutingException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new BatchAlreadyExecutingException.
     */
    public static BatchAlreadyExecutingException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code BatchAlreadyExecutingException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new BatchAlreadyExecutingException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link JsonFields#MESSAGE} field.
     */
    public static BatchAlreadyExecutingException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link BatchAlreadyExecutingException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<BatchAlreadyExecutingException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String batchId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, batchId));
        }

        @Override
        protected BatchAlreadyExecutingException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new BatchAlreadyExecutingException(dittoHeaders, message, description, cause, href);
        }
    }

}
