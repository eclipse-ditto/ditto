/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.exceptions;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Thrown when an (external) header value is not supported (at least on a specific level).
 */
@Immutable
@JsonParsableException(errorCode = DittoHeaderNotSupportedException.ERROR_CODE)
public final class DittoHeaderNotSupportedException extends DittoRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "header.notsupported";

    private static final String DEFAULT_MESSAGE = "The header is not supported.";

    private static final String MESSAGE_TEMPLATE = "The value ''{0}'' of the header ''{1}'' is not supported on this resource level.";

    private static final String DEFAULT_DESCRIPTION = "Verify that the header has the correct syntax and is used on the correct resource level.";

    private static final long serialVersionUID = -6073509778207603606L;
        
    /**
     * Definition of an optional JSON field that contains the key of the not supported header.
     *
     * @since 3.0.0
     */
    static final JsonFieldDefinition<String> JSON_FIELD_NOT_SUPPORTED_HEADER_KEY =
            JsonFieldDefinition.ofString("notSupportedHeaderKey",
                    FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    @Nullable private final String notSupportedHeaderKey;

    private DittoHeaderNotSupportedException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href,
            @Nullable final String notSupportedHeaderKey) {

        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
        this.notSupportedHeaderKey = notSupportedHeaderKey;
    }

    /**
     * A mutable builder for a {@code DittoHeaderInvalidException} in case of an invalid type.
     *
     * @param headerName the key of the header.
     * @param headerValue the value of the header.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DittoHeaderNotSupportedException.Builder newInvalidTypeBuilder(final String headerName,
            @Nullable final CharSequence headerValue) {

        return new DittoHeaderNotSupportedException.Builder(headerName, headerValue);
    }

    /**
     * A mutable builder for a {@code DittoHeaderInvalidException} in case of an invalid type.
     *
     * @param headerDefinition the definition of the header.
     * @param headerValue the value of the header.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 3.0.0
     */
    public static DittoHeaderNotSupportedException.Builder newInvalidTypeBuilder(final HeaderDefinition headerDefinition,
            @Nullable final CharSequence headerValue) {

        return new DittoHeaderNotSupportedException.Builder(headerDefinition.getKey(), headerValue);
    }

    /**
     * Returns a new mutable builder with a fluent API for creating a {@code DittoHeaderInvalidException}.
     * The returned builder is initialized with a default message and a default description.
     *
     * @return the builder.
     * @since 3.0.0
     */
    public static DittoHeaderNotSupportedException.Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code DittoHeaderInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new DittoHeaderInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static DittoHeaderNotSupportedException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final Builder builder = new Builder();
        builder.withNotSupportedHeaderKey(jsonObject.getValue(JSON_FIELD_NOT_SUPPORTED_HEADER_KEY).orElse(null));

        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, builder);
    }

    /**
     * Returns the key of the not supported header if known.
     *
     * @return an Optional that either contains the key of the not supported header or is empty if the key is unknown.
     * @since 3.0.0
     */
    public Optional<String> getNotSupportedHeaderKey() {
        return Optional.ofNullable(notSupportedHeaderKey);
    }

    @Override
    protected void appendToJson(final JsonObjectBuilder jsonObjectBuilder, final Predicate<JsonField> predicate) {
        if (null != notSupportedHeaderKey) {
            jsonObjectBuilder.set(JSON_FIELD_NOT_SUPPORTED_HEADER_KEY, notSupportedHeaderKey, predicate);
        }
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

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final DittoHeaderNotSupportedException that = (DittoHeaderNotSupportedException) o;
        return Objects.equals(notSupportedHeaderKey, that.notSupportedHeaderKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), notSupportedHeaderKey);
    }

    /**
     * A mutable builder with a fluent API for a {@link org.eclipse.ditto.base.model.exceptions.DittoHeaderNotSupportedException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<DittoHeaderNotSupportedException> {

        @Nullable private String notSupportedHeaderKey;

        private Builder() {
            notSupportedHeaderKey = null;
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String headerName, @Nullable final CharSequence headerValue) {
            notSupportedHeaderKey = headerName;
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(headerValue), requireNonNull(headerName)));
            description(DEFAULT_DESCRIPTION);
        }

        /**
         * Sets the key of the not supported header.
         *
         * @param notSupportedHeaderKey the key of the not supported header.
         * @return this builder instance for method chaining.
         * @since 3.0.0
         */
        public Builder withNotSupportedHeaderKey(@Nullable final CharSequence notSupportedHeaderKey) {
            if (null != notSupportedHeaderKey) {
                this.notSupportedHeaderKey = notSupportedHeaderKey.toString();
            } else {
                this.notSupportedHeaderKey = null;
            }
            return this;
        }

        @Override
        protected DittoHeaderNotSupportedException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new DittoHeaderNotSupportedException(dittoHeaders, message, description, cause, href, notSupportedHeaderKey);
        }

    }

}
