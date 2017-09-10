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
 * Thrown if a {@link org.eclipse.ditto.model.policies.Subject} was either not present or the requester had insufficient
 * permissions to access it.
 */
@Immutable
public final class SubjectNotAccessibleException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "subject.notfound";

    private static final String MESSAGE_TEMPLATE = "The Subject with ID ''{0}'' of the PolicyEntry with Label ''{1}''" +
            " on the Policy with ID ''{2}'' could not be found or requester had insufficient permissions to access it.";

    private static final String DEFAULT_DESCRIPTION = "Check if the ID of the Policy, the Label of the PolicyEntry" +
            " and ID of your requested Subject was correct and you have sufficient permissions.";

    private static final long serialVersionUID = -316536964837269703L;

    private SubjectNotAccessibleException(final DittoHeaders dittoHeaders,
            final String message,
            final String description,
            final Throwable cause,
            final URI href) {

        super(ERROR_CODE, HttpStatusCode.NOT_FOUND, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code SubjectNotAccessibleException}.
     *
     * @param policyId the identifier of the Policy.
     * @param label the Label of the PolicyEntry.
     * @param subjectId the identifier of the Subject.
     * @return the builder.
     */
    public static Builder newBuilder(final String policyId, final CharSequence label, final CharSequence subjectId) {
        return new Builder(policyId, label, subjectId);
    }

    /**
     * Constructs a new {@code SubjectNotAccessibleException} object with given message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubjectNotAccessibleException.
     */
    public static SubjectNotAccessibleException fromMessage(final String message, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Constructs a new {@code SubjectNotAccessibleException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubjectNotAccessibleException.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link JsonFields#MESSAGE} field.
     */
    public static SubjectNotAccessibleException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    /**
     * A mutable builder with a fluent API for a {@link SubjectNotAccessibleException}.
     *
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<SubjectNotAccessibleException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String policyId, final CharSequence label, final CharSequence subjectId) {
            description(DEFAULT_DESCRIPTION);
            message(MessageFormat.format(MESSAGE_TEMPLATE, subjectId, label, policyId));
        }

        @Override
        protected SubjectNotAccessibleException doBuild(final DittoHeaders dittoHeaders,
                final String message,
                final String description,
                final Throwable cause,
                final URI href) {

            return new SubjectNotAccessibleException(dittoHeaders, message, description, cause, href);
        }

    }

}
