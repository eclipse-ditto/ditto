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

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Collection;

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
 * This exception indicates that a Policy is not valid for a Thing.
 */
@Immutable
public final class PolicyInvalidException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "policy.invalid";
    /**
     * Status code of this exception.
     */
    static final HttpStatusCode STATUS_CODE = HttpStatusCode.BAD_REQUEST;

    private static final String MESSAGE_TEMPLATE = "The Policy specified for the Thing with ID ''{0}'' is invalid.";

    private static final String DESCRIPTION_TEMPLATE =
            "It must contain at least one Subject with the following permission(s): ''{0}''!";

    private static final long serialVersionUID = -4503670096839743360L;

    private PolicyInvalidException(final DittoHeaders dittoHeaders, final String message, final String description,
            final Throwable cause, final URI href) {
        super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@link PolicyInvalidException}.
     *
     * @param permissions the required permissions.
     * @param thingId the identifier of the Thing.
     * @return the builder.
     */
    public static Builder newBuilder(final Collection<String> permissions, final String thingId) {
        return new Builder(requireNonNull(permissions), requireNonNull(thingId));
    }

    /**
     * Constructs a new {@link PolicyInvalidException} object from the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new {@link PolicyInvalidException}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static PolicyInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String message = readMessage(jsonObject);
        final String description = readDescription(jsonObject).orElse(null);

        return new Builder()
                .message(message)
                .description(description)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    @Override
    protected DittoRuntimeExceptionBuilder<? extends DittoRuntimeException> getEmptyBuilder() {
        return new Builder();
    }

    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * A mutable builder with a fluent API for a {@link PolicyInvalidException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyInvalidException> {

        private Builder() {}

        private Builder(final Collection<String> permissions, final String thingId) {
            message(MessageFormat.format(MESSAGE_TEMPLATE, thingId));
            description(MessageFormat.format(DESCRIPTION_TEMPLATE, permissions));
        }

        @Override
        protected PolicyInvalidException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new PolicyInvalidException(dittoHeaders, message, description, cause, href);
        }
    }
}
