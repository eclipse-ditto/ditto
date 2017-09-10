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

import java.net.URI;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingException;

/**
 * Thrown if either for a REST POST or PUT request for creating a Thing it was tried to set an explicit {@code thingId}
 * in the JSON body.
 */
@Immutable
public final class ThingIdNotExplicitlySettableException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "id.notsettable";

    private static final String MESSAGE_TEMPLATE_POST =
            "It is not allowed to provide a Thing ID in the request body for "
                    + "method POST. The method POST will generate the Thing ID by itself.";

    private static final String DEFAULT_DESCRIPTION_POST = "To provide your own Thing ID use a PUT request instead.";

    private static final String MESSAGE_TEMPLATE_PUT =
            "The Thing ID in the request body is not equal to the Thing ID in " + "the request URL.";

    private static final String DEFAULT_DESCRIPTION_PUT =
            "Either delete the Thing ID from the request body or use the " + "same Thing ID as in the request URL.";

    private static final long serialVersionUID = 5477658033219182854L;

    private ThingIdNotExplicitlySettableException(final DittoHeaders dittoHeaders, final String message,
            final String description, final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ThingIdNotExplicitlySettableException}.
     *
     * @param isPostMethod whether the exception is created for a POST request ({@code true}) or for a PUT request (
     * {@code false}).
     * @return the builder.
     */
    public static Builder newBuilder(final boolean isPostMethod) {
        return new Builder(isPostMethod);
    }

    /**
     * Constructs a new {@code ThingIdNotExplicitlySettableException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingIdNotExplicitlySettableException.
     */
    public static ThingIdNotExplicitlySettableException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder(MESSAGE_TEMPLATE_POST.equalsIgnoreCase(message))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * Constructs a new {@code ThingIdNotExplicitlySettableException} object with the exception message extracted from
     * the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ThingIdNotExplicitlySettableException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static ThingIdNotExplicitlySettableException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link ThingIdNotExplicitlySettableException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ThingIdNotExplicitlySettableException> {

        private Builder(final boolean isPostMethod) {
            message(isPostMethod ? MESSAGE_TEMPLATE_POST : MESSAGE_TEMPLATE_PUT);
            description(isPostMethod ? DEFAULT_DESCRIPTION_POST : DEFAULT_DESCRIPTION_PUT);
        }

        @Override
        protected ThingIdNotExplicitlySettableException doBuild(final DittoHeaders dittoHeaders,
                final String message,
                final String description, final Throwable cause, final URI href) {
            return new ThingIdNotExplicitlySettableException(dittoHeaders, message, description, cause, href);
        }
    }


}
