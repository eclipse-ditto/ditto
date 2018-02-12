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
package org.eclipse.ditto.model.things;

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
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * This exception indicates that a {@link org.eclipse.ditto.model.things.Thing}'s
 * {@link org.eclipse.ditto.model.base.json.JsonSchemaVersion} requires a policyId.
 */
@Immutable
public final class PolicyIdMissingException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "policy.id.missing";


    private static final String MESSAGE_TEMPLATE =
            "The schema version of the Thing with ID ''{0}'' does not allow an update on schema version ''{1}'' without" +
                    " providing a policy id";

    private static final String DEFAULT_DESCRIPTION =
            "When updating a schema version 1 Thing using a higher schema version API, you need to add a policyId. " +
                    "Be aware that this will convert the Thing to the higher schema version, thus removing all ACL " +
                    "information from it.";

    private static final long serialVersionUID = -2640894758584381867L;

    private PolicyIdMissingException(final DittoHeaders dittoHeaders, @Nullable final String message,
            @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * Constructs a new {@code PolicyIdMissingException} object with the given exception message.
     *
     * @param message the Message.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyIdMissingException.
     */
    public static PolicyIdMissingException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code PolicyIdMissingException} object with the given exception message.
     *
     * @param thingId the ID of the Thing.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyIdMissingException.
     */
    public static PolicyIdMissingException fromThingId(final String thingId, final DittoHeaders dittoHeaders) {
        final JsonSchemaVersion schemaVersion = dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST);
        return new Builder(thingId, schemaVersion)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * Constructs a new {@code PolicyIdMissingException} object with the exception message extracted from the given JSON
     * object.
     *
     * @param jsonObject the JSON to read the
     * {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyIdMissingException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the
     * {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field.
     */
    public static PolicyIdMissingException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_1};
    }

    /**
     * A mutable builder with a fluent API for a {@link PolicyIdMissingException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyIdMissingException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String thingId, final JsonSchemaVersion version) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, thingId, version.toInt()));
        }

        @Override
        protected PolicyIdMissingException doBuild(final DittoHeaders dittoHeaders, @Nullable final String message,
                @Nullable final String description, @Nullable final Throwable cause, @Nullable final URI href) {
            return new PolicyIdMissingException(dittoHeaders, message, description, cause, href);
        }
    }

}
