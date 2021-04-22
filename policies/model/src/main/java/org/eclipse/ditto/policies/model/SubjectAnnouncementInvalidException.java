/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model;

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
 * Thrown if a Subject announcement config is not valid (e.g. because the {@code beforeExpiry} duration could not be
 * parsed or the provided expiry timestamp was in the past).
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableException(errorCode = SubjectAnnouncementInvalidException.ERROR_CODE)
public final class SubjectAnnouncementInvalidException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "subjectannouncement.invalid";

    private static final String MESSAGE_TEMPLATE = "The 'beforeExpiry' duration ''{0}'' is not valid.";

    private static final String DESCRIPTION =
            "It must be a positive integer followed by 'h' (hours), 'm' (minutes) or 's' (seconds).";

    private SubjectAnnouncementInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code SubjectAnnouncementInvalidException}.
     *
     * @param beforeExpiry the string in the "beforeExpiry" field.
     * @return the builder.
     */
    public static Builder newBuilder(final CharSequence beforeExpiry) {
        return new Builder(beforeExpiry, DESCRIPTION);
    }

    /**
     * Constructs a new {@code SubjectAnnouncementInvalidException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubjectAnnouncementInvalidException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static SubjectAnnouncementInvalidException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code SubjectAnnouncementInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubjectAnnouncementInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SubjectAnnouncementInvalidException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link SubjectAnnouncementInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<SubjectAnnouncementInvalidException> {

        private Builder() {
            description(DESCRIPTION);
        }

        private Builder(final CharSequence expiry, final String description) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, expiry));
            description(description);
        }

        @Override
        protected SubjectAnnouncementInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new SubjectAnnouncementInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
