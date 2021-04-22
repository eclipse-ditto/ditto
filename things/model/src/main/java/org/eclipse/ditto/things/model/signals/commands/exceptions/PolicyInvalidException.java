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
package org.eclipse.ditto.things.model.signals.commands.exceptions;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Collection;

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
import org.eclipse.ditto.things.model.ThingException;
import org.eclipse.ditto.things.model.ThingId;

/**
 * This exception indicates that a Policy is not valid for a Thing.
 */
@Immutable
@JsonParsableException(errorCode = PolicyInvalidException.ERROR_CODE)
public final class PolicyInvalidException extends DittoRuntimeException implements ThingException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "policy.invalid";

    /**
     * Status code of this exception.
     */
    static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;

    private static final String MESSAGE_TEMPLATE = "The Policy specified for the Thing with ID ''{0}'' is invalid.";

    private static final String DESCRIPTION_TEMPLATE =
            "It must contain at least one Subject with the following permission(s): ''{0}''!";

    private static final long serialVersionUID = -4503670096839743360L;

    private PolicyInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, STATUS, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code PolicyInvalidException}.
     *
     * @param permissions the required permissions.
     * @param thingId the identifier of the Thing.
     * @return the builder.
     */
    public static Builder newBuilder(final Collection<String> permissions, final ThingId thingId) {
        checkNotNull(permissions, "permissions");
        checkNotNull(thingId, "thingId");
        return new Builder(permissions, thingId);
    }

    /**
     * A mutable builder for a {@code PolicyInvalidException} caused by some other error.
     *
     * @param cause reason why the policy is invalid.
     * @param thingId ID of the thing the policy applies to.
     * @return the builder.
     */
    public static Builder newBuilderForCause(final Throwable cause, final ThingId thingId) {
        checkNotNull(cause, "cause");
        checkNotNull(thingId, "thingId");
        return new Builder(cause, thingId);
    }

    /**
     * Constructs a new {@code PolicyInvalidException} object from the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new PolicyInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static PolicyInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, new Builder());
    }

    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
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
     * A mutable builder with a fluent API for a {@link PolicyInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<PolicyInvalidException> {

        private Builder() {
            description(DESCRIPTION_TEMPLATE);
        }

        private Builder(final Collection<String> permissions, final ThingId thingId) {
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(thingId)));
            description(MessageFormat.format(DESCRIPTION_TEMPLATE, permissions));
        }

        private Builder(final Throwable cause, final ThingId thingId) {
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(thingId)));
            description(cause.getMessage());
            cause(cause);
        }

        @Override
        protected PolicyInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new PolicyInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
