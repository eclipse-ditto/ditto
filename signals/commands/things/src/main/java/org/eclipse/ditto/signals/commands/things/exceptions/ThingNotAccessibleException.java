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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingException;

/**
 * Thrown if the Thing was either not present in Ditto at all or if the requester had insufficient permissions to access
 * it.
 */
@Immutable
public final class ThingNotAccessibleException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "thing.notfound";

    private static final String MESSAGE_TEMPLATE =
            "The Thing with ID ''{0}'' could not be found or requester had " + "insufficient permissions to access it.";

    private static final String DEFAULT_DESCRIPTION =
            "Check if the ID of your requested Thing was correct and you have sufficient permissions.";

    private static final long serialVersionUID = -623037881914361095L;

    private ThingNotAccessibleException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    /**
     * Constructs a new {@code ThingNotAccessibleException} object.
     *
     * @param thingId the ID of the Thing which is not accessible.
     * @param dittoHeaders the headers with which this Exception should be reported back to the user.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public ThingNotAccessibleException(final CharSequence thingId, final DittoHeaders dittoHeaders) {
        this(dittoHeaders, getMessage(thingId), DEFAULT_DESCRIPTION, null, null);
    }

    private static String getMessage(final CharSequence thingId) {
        checkNotNull("ID of the inaccessible Thing");
        return MessageFormat.format(MESSAGE_TEMPLATE, thingId);
    }

    /**
     * A mutable builder for a {@code ThingNotAccessibleException}.
     *
     * @param thingId the ID of the thing.
     * @return the builder.
     * @throws NullPointerException if {@code thingId} is {@code null}.
     */
    public static Builder newBuilder(final String thingId) {
        return new Builder(thingId);
    }

    /**
     * Constructs a new {@code ThingNotAccessibleException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingNotAccessibleException.
     */
    public static ThingNotAccessibleException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code ThingNotAccessibleException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingNotAccessibleException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static ThingNotAccessibleException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link ThingNotAccessibleException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ThingNotAccessibleException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final CharSequence thingId) {
            this();
            message(getMessage(thingId));
        }

        @Override
        protected ThingNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new ThingNotAccessibleException(dittoHeaders, message, description, cause, href);
        }

    }

}
