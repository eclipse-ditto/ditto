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
package org.eclipse.ditto.signals.commands.policies.exceptions;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PolicyException;

/**
 * Thrown if a {@link org.eclipse.ditto.model.policies.Resource} was either not present or the requester had
 * insufficient permissions to access it.
 */
@Immutable
public final class ResourceNotAccessibleException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "resource.notfound";

    private static final String MESSAGE_TEMPLATE = "The Resource ''{0}'' of the PolicyEntry with Label ''{1}'' on the" +
            " Policy with ID ''{2}'' could not be found or requester had insufficient permissions to access it.";

    private static final String DEFAULT_DESCRIPTION = "Check if the ID of the Policy, the Label of the PolicyEntry" +
            " and the path of your requested Resource was correct and you have sufficient permissions.";

    private static final long serialVersionUID = 2620243998960976955L;

    private ResourceNotAccessibleException(final DittoHeaders dittoHeaders,
            final String message,
            final String description,
            final Throwable cause,
            final URI href) {

        super(ERROR_CODE, HttpStatusCode.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code ResourceNotAccessibleException}.
     *
     * @param policyId the identifier of the Policy.
     * @param label the Label of the PolicyEntry.
     * @param path the path of the Resource.
     * @return the builder.
     */
    public static Builder newBuilder(final String policyId, final CharSequence label, final String path) {
        return new Builder(policyId, label, path);
    }

    /**
     * Constructs a new {@code ResourceNotAccessibleException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ResourceNotAccessibleException.
     */
    public static ResourceNotAccessibleException fromMessage(final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code ResourceNotAccessibleException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new ResourceNotAccessibleException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static ResourceNotAccessibleException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link ResourceNotAccessibleException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<ResourceNotAccessibleException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String policyId, final CharSequence label, final String path) {
            description(DEFAULT_DESCRIPTION);
            message(MessageFormat.format(MESSAGE_TEMPLATE, path, label, policyId));
        }

        @Override
        protected ResourceNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                final String message,
                final String description,
                final Throwable cause,
                final URI href) {

            return new ResourceNotAccessibleException(dittoHeaders, message, description, cause, href);
        }

    }

}
