/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.messages;

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
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Indicates that the performed validation on this message failed.
 */
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
            JsonFactory.newJsonArrayFieldDefinition("validationErrors", FieldType.REGULAR, JsonSchemaVersion.V_1,
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

        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
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
     */
    public static MessageFormatInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .loadJson(jsonObject)
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

        public Builder validationErrors(@Nullable final JsonArray validationErrors) {
            this.validationErrors = validationErrors;
            return this;
        }

        @Override
        public Builder loadJson(final JsonObject jsonObject) {
            super.loadJson(jsonObject);
            jsonObject.getValue(VALIDATION_ERRORS)
                    .filter(JsonValue::isArray)
                    .map(JsonValue::asArray)
                    .ifPresent(this::validationErrors);
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
