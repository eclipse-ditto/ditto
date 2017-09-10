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
package org.eclipse.ditto.signals.commands.things.exceptions;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingException;

/**
 * Thrown if the Thing could not be deleted because the requester had insufficient permissions to delete it.
 */
@Immutable
public final class ThingNotDeletableException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "thing.notdeletable";

    private static final String MESSAGE_TEMPLATE_V1 = "The Thing with ID ''{0}'' could not be deleted as the requester "
            + "had insufficient permissions ( 'WRITE' and 'ADMINISTRATE' are required).";

    private static final String MESSAGE_TEMPLATE = "The Thing with ID ''{0}'' could not be deleted as the requester "
            + "had insufficient permissions ( 'WRITE' on root resource is required).";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the ID of your requested Thing was correct and you have sufficient permissions.";

    private static final long serialVersionUID = -8123337828934144618L;

    private ThingNotDeletableException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.FORBIDDEN, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ThingNotDeletableException}.
     *
     * @param thingId the ID of the thing.
     * @return the builder.
     */
    public static Builder newBuilder(final String thingId) {
        return new Builder(thingId);
    }

    /**
     * Constructs a new {@code ThingNotDeletableException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingNotDeletableException.
     */
    public static ThingNotDeletableException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code ThingNotDeletableException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingNotDeletableException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static ThingNotDeletableException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link ThingNotDeletableException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ThingNotDeletableException> {

        private String thingId;

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String thingId) {
            this();
            this.thingId = checkNotNull(thingId, "Thing identifier");
        }

        @Override
        protected ThingNotDeletableException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            final JsonSchemaVersion schemaVersion =
                    checkNotNull(dittoHeaders, "command headers").getSchemaVersion().orElse(JsonSchemaVersion.LATEST);

            if (message == null) {
                if (schemaVersion.equals(JsonSchemaVersion.V_1)) {
                    return new ThingNotDeletableException(dittoHeaders,
                            MessageFormat.format(MESSAGE_TEMPLATE_V1, thingId),
                            description, cause, href);
                } else {
                    return new ThingNotDeletableException(dittoHeaders,
                            MessageFormat.format(MESSAGE_TEMPLATE, thingId),
                            description, cause, href);
                }
            }

            return new ThingNotDeletableException(dittoHeaders, message, description, cause, href);
        }
    }

}
