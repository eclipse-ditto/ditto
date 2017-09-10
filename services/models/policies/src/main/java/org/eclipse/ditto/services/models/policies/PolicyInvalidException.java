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
package org.eclipse.ditto.services.models.policies;

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
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PolicyException;

/**
 * This exception indicates that a the Policy is not valid.
 */
@Immutable
public final class PolicyInvalidException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "policy.invalid";

    private static final String MESSAGE_TEMPLATE = "The Policy of the Thing with ID ''{0}'' is invalid.";

    private static final String DESCRIPTION_TEMPLATE =
            "It must contain at least one Subject with the following permission(s): ''{0}''!";

    private static final long serialVersionUID = -4503670096839743360L;

    private PolicyInvalidException(final DittoHeaders dittoHeaders, final String message, final String description,
            final Throwable cause, final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyInvalidException}.
     *
     * @param permissions the required permissions.
     * @param thingId the identifier of the Thing.
     * @return the builder.
     */
    public static Builder newBuilder(final Permissions permissions, final String thingId) {
        return new Builder(permissions, thingId);
    }

    /**
     * Constructs a new {@code PolicyInvalidException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyInvalidException.
     */
    public static PolicyInvalidException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder(Permissions.none()) //
                .message(message) //
                .dittoHeaders(dittoHeaders) //
                .build();
    }

    /**
     * Constructs a new {@code PolicyInvalidException} object with the exception message extracted from the given JSON
     * object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyInvalidException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static PolicyInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
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

        private Builder(final Permissions permissions) {
            description(MessageFormat.format(DESCRIPTION_TEMPLATE, permissions));
        }

        private Builder(final Permissions permissions, final String thingId) {
            this(permissions);
            message(MessageFormat.format(MESSAGE_TEMPLATE, thingId));
        }

        @Override
        protected PolicyInvalidException doBuild(final DittoHeaders dittoHeaders, final String message,
                final String description, final Throwable cause, final URI href) {
            return new PolicyInvalidException(dittoHeaders, message, description, cause, href);
        }
    }
}
