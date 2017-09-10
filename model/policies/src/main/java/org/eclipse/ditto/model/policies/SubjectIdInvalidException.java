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
package org.eclipse.ditto.model.policies;

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
 * Thrown if a Subject ID is not valid (it requires at least one
 * "{@value SubjectId#ISSUER_DELIMITER}" in it).
 */
@Immutable
public final class SubjectIdInvalidException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "subjectid.invalid";

    private static final String MESSAGE_TEMPLATE = "Subject ID ''{0}'' is not valid!";

    private static final String DEFAULT_DESCRIPTION =
            "It must contain an issuer as prefix separated by a colon ':' from the actual subject";

    private static final long serialVersionUID = -2833735892375663681L;

    /**
     * Constructs a new {@code SubjectIdInvalidException} object.
     *
     * @param subjectId the invalid Subject ID.
     */
    public SubjectIdInvalidException(final CharSequence subjectId) {
        this(DittoHeaders.empty(), MessageFormat.format(MESSAGE_TEMPLATE, subjectId), DEFAULT_DESCRIPTION, null, null);
    }

    private SubjectIdInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code SubjectIdInvalidException}.
     *
     * @param subjectId the ID of the subject.
     * @return the builder.
     */
    public static Builder newBuilder(@Nullable final CharSequence subjectId) {
        return new Builder(subjectId);
    }

    /**
     * Constructs a new {@code SubjectIdInvalidException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubjectIdInvalidException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static SubjectIdInvalidException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {

        return new Builder()
                .message(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * Constructs a new {@code SubjectIdInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubjectIdInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static SubjectIdInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromMessage(readMessage(jsonObject), dittoHeaders);
    }

    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * A mutable builder with a fluent API for a {@link SubjectIdInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<SubjectIdInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(@Nullable final CharSequence subjectId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, subjectId));
        }

        @Override
        protected SubjectIdInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new SubjectIdInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
