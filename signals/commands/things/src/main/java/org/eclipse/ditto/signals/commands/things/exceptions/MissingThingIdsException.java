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
 * This exception is thrown when multiple things are requested without specifying IDs.
 */
@Immutable
public class MissingThingIdsException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "things.ids.missing";

    /**
     * Status code of this exception.
     */
    public static final HttpStatusCode STATUS_CODE = HttpStatusCode.BAD_REQUEST;

    private static final String DEFAULT_MESSAGE = "The required list of thing ids was missing.";

    private static final String DEFAULT_DESCRIPTION = "Please provide at least one thing id and try again.";
    
    private static final long serialVersionUID = -5672699009682971258L;

    private MissingThingIdsException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
    }

    @Override
    protected Builder getEmptyBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@link MissingThingIdsException}.
     *
     * @return the builder.
     */
    public static MissingThingIdsException.Builder newBuilder() {
        return new MissingThingIdsException.Builder();
    }

    /**
     * Constructs a new {@link MissingThingIdsException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new {@link MissingThingIdsException}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static MissingThingIdsException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new MissingThingIdsException.Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .build();
    }
    /**
     * A mutable builder with a fluent API for a {@link MissingThingIdsException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<MissingThingIdsException> {

        private Builder() {
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        @Override
        protected MissingThingIdsException doBuild(final DittoHeaders dittoHeaders, @Nullable final String message,
                @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
            return new MissingThingIdsException(dittoHeaders, message, description, cause, href);
        }
    }
}
