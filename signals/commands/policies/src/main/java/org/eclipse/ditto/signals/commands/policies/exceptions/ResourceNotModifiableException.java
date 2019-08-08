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
package org.eclipse.ditto.signals.commands.policies.exceptions;

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
import org.eclipse.ditto.model.base.json.JsonParsableException;
import org.eclipse.ditto.model.policies.PolicyException;
import org.eclipse.ditto.model.policies.id.PolicyId;

/**
 * Thrown if a {@link org.eclipse.ditto.model.policies.Resource} could not be modified because the requester had
 * insufficient permissions.
 */
@Immutable
@JsonParsableException(errorCode = ResourceNotModifiableException.ERROR_CODE)
public final class ResourceNotModifiableException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "resource.notmodifiable";

    private static final String MESSAGE_TEMPLATE = "The Resource ''{0}'' of the PolicyEntry with Label ''{1}'' on" +
            " the Policy with ID ''{2}'' could not be modified as the requester had insufficient permissions.";

    private static final String DEFAULT_DESCRIPTION = "Check if the ID of the Policy, the Label of the PolicyEntry" +
            " and the path of your requested Resource was correct and you have sufficient permissions.";

    private static final long serialVersionUID = -3333742392554340824L;

    private ResourceNotModifiableException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.FORBIDDEN, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ResourceNotModifiableException}.
     *
     * @param policyId the identifier of the Policy.
     * @param label the Label of the PolicyEntry.
     * @param path the path of the Resource.
     * @return the builder.
     */
    public static Builder newBuilder(final PolicyId policyId, final CharSequence label, final CharSequence path) {
        return new Builder(policyId, label, path);
    }

    /**
     * Constructs a new {@code ResourceNotModifiableException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ResourceNotModifiableException.
     */
    public static ResourceNotModifiableException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code ResourceNotModifiableException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ResourceNotModifiableException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static ResourceNotModifiableException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    /**
     * A mutable builder with a fluent API for a {@link ResourceNotModifiableException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ResourceNotModifiableException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final PolicyId policyId, final CharSequence label, final CharSequence path) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, path, label, String.valueOf(policyId)));
        }

        @Override
        protected ResourceNotModifiableException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new ResourceNotModifiableException(dittoHeaders, message, description, cause, href);
        }

    }

}
