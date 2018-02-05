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
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingException;

/**
 * Thrown if a Thing cannot be modified because it contained a Policy ID and a Policy or a Policy with ID.
 */
public final class PolicyIdNotAllowedException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "policy.id.notallowed";

    private static final String MESSAGE_TEMPLATE =
            "The Thing with ID ''{0}'' could not be modified as it contained an inline Policy with an ID or a Policy " +
                    "ID and a Policy";

    private static final String DEFAULT_DESCRIPTION =
            "If you want to use an existing Policy, specify it as 'policyId' in the Thing JSON. If you want to create" +
                    " a Thing with inline Policy, no Policy ID is allowed as it will be created with the Thing ID.";

    private static final long serialVersionUID = 4511420390758955872L;

    private PolicyIdNotAllowedException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyIdNotAllowedException}.
     *
     * @param thingId the ID of the thing.
     * @return the builder.
     */
    public static Builder newBuilder(final String thingId) {
        return new Builder(thingId);
    }

    /**
     * Constructs a new {@code PolicyIdNotAllowedException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyIdNotAllowedException.
     */
    public static PolicyIdNotAllowedException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code PolicyIdNotAllowedException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyIdNotAllowedException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static PolicyIdNotAllowedException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link PolicyIdNotAllowedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyIdNotAllowedException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String thingId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, thingId));
        }

        @Override
        protected PolicyIdNotAllowedException doBuild(final DittoHeaders dittoHeaders, @Nullable final String message,
                @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
            return new PolicyIdNotAllowedException(dittoHeaders, message, description, cause, href);
        }
    }
}
