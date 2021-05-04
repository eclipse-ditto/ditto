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
package org.eclipse.ditto.messages.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Indicates that the performed validation on this message failed.
 */
@JsonParsableException(errorCode = MessageFormatInvalidException.ERROR_CODE)
public final class MessageFormatInvalidException extends DittoRuntimeException implements MessageException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "messageformat.invalid";

    private static final String MESSAGE =
            "The validation against the defined schema of this message failed.";

    private static final String DEFAULT_DESCRIPTION =
            "Please make sure that the message has the correct format or change the used json schema for validation.";

    private static final JsonFieldDefinition<JsonArray> VALIDATION_ERRORS =
            JsonFactory.newJsonArrayFieldDefinition("validationErrors", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private static final long serialVersionUID = -7767643705375184157L;

    /**
     * ValidationErrors are marked as transient because JsonArray is not serializable and we do not use Java
     * serialization anyway.
     */
    @SuppressWarnings("squid:S1948") // validationErrors cannot be serialized by java, but as JSON
    private final JsonArray validationErrors;

    private MessageFormatInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href,
            @Nullable final JsonArray validationErrors) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
        this.validationErrors = validationErrors;
    }

    /**
     * A mutable builder for a {@code MessageFormatInvalidException}.
     *
     * @param validationErrors errors to build the message from.
     * @return the builder.
     */
    public static MessageFormatInvalidException.Builder newBuilder(final JsonArray validationErrors) {
        return new MessageFormatInvalidException.Builder(validationErrors);
    }

    @Override
    protected void appendToJson(final JsonObjectBuilder jsonObjectBuilder, final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(VALIDATION_ERRORS, null != validationErrors ? validationErrors : JsonFactory.nullArray(),
                predicate);
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
        final MessageFormatInvalidException that = (MessageFormatInvalidException) o;
        return Objects.equals(validationErrors, that.validationErrors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), validationErrors);
    }

    /**
     * Deserialize from a JSON object.
     *
     * @param jsonObject the JSON object to deserialize.
     * @param dittoHeaders the headers.
     * @return an instance of this class.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static MessageFormatInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        checkNotNull(jsonObject, "jsonObject");
        final Builder builder = new Builder();
        jsonObject.getValue(VALIDATION_ERRORS)
                .filter(JsonValue::isArray)
                .map(JsonValue::asArray)
                .ifPresent(builder::validationErrors);
        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders, builder);
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
     * A mutable builder with a fluent API for a {@link MessageFormatInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<MessageFormatInvalidException> {

        @Nullable private JsonArray validationErrors;

        private Builder() {
            this(JsonArray.newBuilder().build());
        }

        private Builder(final JsonArray validationErrors) {
            message(MESSAGE);
            description(DEFAULT_DESCRIPTION);
            validationErrors(validationErrors);
        }

        private Builder validationErrors(@Nullable final JsonArray validationErrors) {
            this.validationErrors = validationErrors;
            return this;
        }

        @Override
        protected MessageFormatInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new MessageFormatInvalidException(dittoHeaders, message, description, cause, href,
                    validationErrors);
        }

    }

}
