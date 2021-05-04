/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.things.model;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * This exception indicates that a {@link Thing}'s
 * {@link org.eclipse.ditto.base.model.json.JsonSchemaVersion} requires a policyId.
 */
@Immutable
@JsonParsableException(errorCode = PolicyIdMissingException.ERROR_CODE)
public final class PolicyIdMissingException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "policy.id.missing";

    private static final String MESSAGE_TEMPLATE_UPDATE =
            "The schema version of the Thing with ID ''{0}'' does not allow an update on schema version ''{1}'' " +
                    "without providing a policy id";

    private static final String DEFAULT_DESCRIPTION = "Please provide a valid Policy ID.";

    private static final String MESSAGE_TEMPLATE_CREATE =
            "The schema version of the Thing with ID ''{0}'' (''{1}'') does not allow creation without " +
                    "providing a policy id";

    private static final long serialVersionUID = -2640894758584381867L;

    private PolicyIdMissingException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * Constructs a new {@code PolicyIdMissingException} object with an exception message for a thing-update scenario.
     *
     * @param thingId the ID of the Thing.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyIdMissingException.
     */
    public static PolicyIdMissingException fromThingIdOnUpdate(final ThingId thingId, final DittoHeaders dittoHeaders) {
        final JsonSchemaVersion schemaVersion = dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST);
        return new Builder(thingId, schemaVersion, MESSAGE_TEMPLATE_UPDATE, DEFAULT_DESCRIPTION)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * Constructs a new {@code PolicyIdMissingException} object with an exception message for a thing-create scenario.
     *
     * @param thingId the ID of the Thing.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyIdMissingException.
     */
    public static PolicyIdMissingException fromThingIdOnCreate(final ThingId thingId, final DittoHeaders dittoHeaders) {
        final JsonSchemaVersion schemaVersion = dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST);
        return new Builder(thingId, schemaVersion, MESSAGE_TEMPLATE_CREATE, DEFAULT_DESCRIPTION)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * Constructs a new {@code PolicyIdMissingException} object with the exception message and description extracted
     * from the given JSON object.
     *
     * @param jsonObject the JSON to read the
     * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyIdMissingException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static PolicyIdMissingException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(getMessage())
                .description(getDescription().orElse(null))
                .cause(getCause())
                .href(getHref().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link PolicyIdMissingException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyIdMissingException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final ThingId thingId, final JsonSchemaVersion version, final String messageTemplate,
                final String description) {

            this();
            message(MessageFormat.format(messageTemplate, String.valueOf(thingId), version.toInt()));
            description(description);
        }

        @Override
        protected PolicyIdMissingException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new PolicyIdMissingException(dittoHeaders, message, description, cause, href);
        }
    }

}
