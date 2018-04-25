/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.signals.commands.things.exceptions;

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
 * Thrown if an event cannot be send because the affected thing does not exist or because of a missing permission.
 */
@Immutable
public final class EventSendNotAllowedException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "event.notallowed";

    private static final String MESSAGE_TEMPLATE =
            "You are not allowed to send events for the Thing with id ''{0}''!";

    private static final String DEFAULT_DESCRIPTION =
            "Please make sure that the Thing exists and that you have a WRITE permission on the Thing.";

    private static final long serialVersionUID = 9055307625270596757L;

    /**
     * Constructs a new {@code EventSendNotAllowedException} object.
     *
     * @param thingId the ID of the Thing for which an event should be sent.
     */
    public EventSendNotAllowedException(@Nullable final String thingId) {
        this(DittoHeaders.empty(), MessageFormat.format(MESSAGE_TEMPLATE, thingId), DEFAULT_DESCRIPTION, null, null);
    }

    private EventSendNotAllowedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.FORBIDDEN, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code MessageNotSendableException}.
     *
     * @param thingId the ID of the Thing for which an event should be sent.
     * @return the builder.
     */
    public static Builder newBuilder(@Nullable final String thingId) {
        return new Builder(thingId);
    }

    /**
     * Constructs a new {@code MessageNotSendableException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @return the new SubjectInvalidException.
     */
    public static EventSendNotAllowedException fromMessage(@Nullable final String message) {
        return new Builder()
                .message(message)
                .build();
    }

    /**
     * Deserialize from a JSON object.
     *
     * @param jsonObject the JSON object to deserialize.
     * @param dittoHeaders the headers.
     * @return an instance of this class.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static EventSendNotAllowedException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new Builder()
                .loadJson(jsonObject)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link EventSendNotAllowedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<EventSendNotAllowedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(@Nullable final String subject) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, subject));
        }

        @Override
        protected EventSendNotAllowedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new EventSendNotAllowedException(dittoHeaders, message, description, cause, href);
        }

    }

}
