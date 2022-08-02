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
 * Thrown when an (external) header value can not be converted to a Ditto header.
 */
@Immutable
@JsonParsableException(errorCode = DittoHeaderInvalidException.ERROR_CODE)
public final class DittoHeaderInvalidException extends DittoRuntimeException implements GeneralException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "header.invalid";

    private static final String DEFAULT_MESSAGE = "The value of a header is invalid.";

    private static final String MESSAGE_TEMPLATE = "The value ''{0}'' of the header ''{1}'' is not a valid {2}.";

    private static final String DEFAULT_DESCRIPTION = "Verify that the header has the correct syntax and try again.";

    private static final String DESCRIPTION_TEMPLATE =
            "Verify that the value of the header ''{0}'' is a valid ''{1}'' and try again.";

    private static final long serialVersionUID = -2338222496153977081L;

    /**
     * Definition of an optional JSON field that contains the key of the invalid header.
     *
     * @since 2.0.0
     */
    static final JsonFieldDefinition<String> JSON_FIELD_INVALID_HEADER_KEY =
            JsonFieldDefinition.ofString("invalidHeaderKey",
                    FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    @Nullable private final String invalidHeaderKey;

    private DittoHeaderInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href,
            @Nullable final String invalidHeaderKey) {

        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
        this.invalidHeaderKey = invalidHeaderKey;
    }

    /**
     * A mutable builder for a {@code DittoHeaderInvalidException} in case of an invalid type.
     *
     * @param headerName the key of the header.
     * @param headerValue the value of the header.
     * @param headerType the expected type of the header. (int, String, entity-tag...)
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DittoHeaderInvalidException.Builder newInvalidTypeBuilder(final String headerName,
            @Nullable final CharSequence headerValue, final String headerType) {

        return new DittoHeaderInvalidException.Builder(headerName, headerValue, headerType);
    }

    /**
     * A mutable builder for a {@code DittoHeaderInvalidException} in case of an invalid type.
     *
     * @param headerDefinition the definition of the header.
     * @param headerValue the value of the header.
     * @param headerType the type of the header. (int, String, entity-tag...)
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.1.0
     */
    public static DittoHeaderInvalidException.Builder newInvalidTypeBuilder(final HeaderDefinition headerDefinition,
            @Nullable final CharSequence headerValue, final String headerType) {

        return new DittoHeaderInvalidException.Builder(headerDefinition.getKey(), headerValue, headerType);
    }

    /**
     * Returns a new mutable builder with a fluent API for creating a {@code DittoHeaderInvalidException}.
     * The returned builder is initialized with a default message and a default description.
     *
     * @return the builder.
     * @since 2.0.0
     */
    public static DittoHeaderInvalidException.Builder newBuilder() {
        return new Builder();
    }

    /**
     * Constructs a new {@code DittoHeaderInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new DittoHeaderInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static DittoHeaderInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final Builder builder = new Builder();
        builder.withInvalidHeaderKey(jsonObject.getValue(JSON_FIELD_INVALID_HEADER_KEY).orElse(null));

        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, builder);
    }

    /**
     * Returns the key of the invalid header if known.
     *
     * @return an Optional that either contains the key of the invalid header or is empty if the key is unknown.
     * @since 2.0.0
     */
    public Optional<String> getInvalidHeaderKey() {
        return Optional.ofNullable(invalidHeaderKey);
    }

    @Override
    protected void appendToJson(final JsonObjectBuilder jsonObjectBuilder, final Predicate<JsonField> predicate) {
        if (null != invalidHeaderKey) {
            jsonObjectBuilder.set(JSON_FIELD_INVALID_HEADER_KEY, invalidHeaderKey, predicate);
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
        final DittoHeaderInvalidException that = (DittoHeaderInvalidException) o;
        return Objects.equals(invalidHeaderKey, that.invalidHeaderKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), invalidHeaderKey);
    }

    /**
     * A mutable builder with a fluent API for a {@link DittoHeaderInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<DittoHeaderInvalidException> {

        @Nullable private String invalidHeaderKey;

        private Builder() {
            invalidHeaderKey = null;
            message(DEFAULT_MESSAGE);
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(final String headerName, @Nullable final CharSequence headerValue, final String headerType) {
            invalidHeaderKey = headerName;
            message(MessageFormat.format(MESSAGE_TEMPLATE, String.valueOf(headerValue), requireNonNull(headerName),
                    requireNonNull(headerType)));
            description(MessageFormat.format(DESCRIPTION_TEMPLATE, headerName, headerType));
        }

        private Builder(final String customMessage) {
            this();
            message(customMessage);
        }

        /**
         * Sets the key of the invalid header.
         *
         * @param invalidHeaderKey the key of the invalid header.
         * @return this builder instance for method chaining.
         * @since 2.0.0
         */
        public Builder withInvalidHeaderKey(@Nullable final CharSequence invalidHeaderKey) {
            if (null != invalidHeaderKey) {
                this.invalidHeaderKey = invalidHeaderKey.toString();
            } else {
                this.invalidHeaderKey = null;
            }
            return this;
        }

        @Override
        protected DittoHeaderInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new DittoHeaderInvalidException(dittoHeaders, message, description, cause, href, invalidHeaderKey);
        }

    }

}
