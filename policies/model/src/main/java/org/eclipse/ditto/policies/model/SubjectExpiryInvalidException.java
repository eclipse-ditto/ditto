/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
 * Thrown if a Subject {@code expiry} timestamp is not valid (e.g. because the provided string could not be parsed
 * as ISO-8601 timestamp or the provided expiry timestamp was in the past).
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableException(errorCode = SubjectExpiryInvalidException.ERROR_CODE)
public final class SubjectExpiryInvalidException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "subjectexpiry.invalid";

    private static final String MESSAGE_TEMPLATE = "Subject expiry timestamp ''{0}'' is not valid.";

    private static final String NOT_PARSABLE_AS_ISO_DESCRIPTION = "It must be provided as ISO-8601 formatted char " +
            "sequence.";
    private static final String MUST_NOT_BE_PAST_DESCRIPTION = "It must not be in the past, please adjust to a " +
            "timestamp in the future.";

    private static final long serialVersionUID = 980234789562098342L;

    private SubjectExpiryInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code SubjectExpiryInvalidException}.
     *
     * @param expiry the expiry of the subject.
     * @return the builder.
     */
    public static Builder newBuilder(final CharSequence expiry) {
        return new Builder(expiry, NOT_PARSABLE_AS_ISO_DESCRIPTION);
    }

    /**
     * A mutable builder for a {@code SubjectExpiryInvalidException} caused by the expiry being in the past.
     *
     * @param expiry the expiry of the subject.
     * @return the builder.
     */
    public static Builder newBuilderTimestampInThePast(final CharSequence expiry) {
        return new Builder(expiry, MUST_NOT_BE_PAST_DESCRIPTION);
    }

    /**
     * Constructs a new {@code SubjectExpiryInvalidException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubjectExpiryInvalidException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static SubjectExpiryInvalidException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return DittoRuntimeException.fromMessage(message, dittoHeaders, new Builder());
    }

    /**
     * Constructs a new {@code SubjectExpiryInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new SubjectExpiryInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SubjectExpiryInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
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
     * A mutable builder with a fluent API for a {@link SubjectExpiryInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<SubjectExpiryInvalidException> {

        private Builder() {
            description(NOT_PARSABLE_AS_ISO_DESCRIPTION);
        }

        private Builder(final CharSequence expiry, final String description) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, expiry));
            description(description);
        }

        @Override
        protected SubjectExpiryInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new SubjectExpiryInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}
