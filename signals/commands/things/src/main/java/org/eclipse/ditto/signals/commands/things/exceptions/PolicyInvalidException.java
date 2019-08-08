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
package org.eclipse.ditto.signals.commands.things.exceptions;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Collection;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingException;
import org.eclipse.ditto.model.things.id.ThingId;

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
    static final HttpStatusCode STATUS_CODE = HttpStatusCode.BAD_REQUEST;

    private static final String MESSAGE_TEMPLATE = "The Policy specified for the Thing with ID ''{0}'' is invalid.";

    private static final String DESCRIPTION_TEMPLATE =
            "It must contain at least one Subject with the following permission(s): ''{0}''!";

    private static final long serialVersionUID = -4503670096839743360L;

    private PolicyInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, STATUS_CODE, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@link PolicyInvalidException}.
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
     * A mutable builder for a {@link PolicyInvalidException} caused by some other error.
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
     * Constructs a new {@link PolicyInvalidException} object from the given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new {@link PolicyInvalidException}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static PolicyInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DESCRIPTION_TEMPLATE))
                .href(readHRef(jsonObject).orElse(null))
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
