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
package org.eclipse.ditto.base.model.signals.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.HttpStatusCodeOutOfRangeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;

/**
 * This class helps to deserialize JSON to a sub-class of {@link CommandResponse}. Hereby this class extracts the
 * values which are common for all command responses.
 */
public final class CommandResponseJsonDeserializer<T extends CommandResponse<?>> {

    private final String expectedCommandResponseType;
    private final DeserializationFunction<T> deserializationFunction;

    private CommandResponseJsonDeserializer(final CharSequence type,
            final DeserializationFunction<T> deserializationFunction) {

        expectedCommandResponseType = ConditionChecker.checkArgument(checkNotNull(type, "type").toString(),
                arg -> !arg.trim().isEmpty(),
                () -> "The type must not be empty or blank.");
        this.deserializationFunction = deserializationFunction;
    }

    private static JsonObject checkJsonObjectNotNull(@Nullable final JsonObject jsonObject) {
        return checkNotNull(jsonObject, "jsonObject");
    }

    /**
     * Returns a new instance of {@code CommandResponseJsonDeserializer}.
     *
     * @param <T> the type of the {@code CommandResponse} to be deserialized.
     * @param type the type of the deserialized {@code CommandResponse}.
     * @param deserializationFunction deserializes a {@code CommandResponse} for a provided
     * {@link DeserializationContext}.
     * Any {@code Exception} that is thrown by this function will be wrapped in a {@code JsonParseException}.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code type} is empty or blank.
     * @since 2.3.0
     */
    public static <T extends CommandResponse<?>> CommandResponseJsonDeserializer<T> newInstance(final CharSequence type,
            final DeserializationFunction<T> deserializationFunction) {

        return new CommandResponseJsonDeserializer<>(type,
                checkNotNull(deserializationFunction, "deserializationFunction"));
    }

    /**
     * Deserializes the specified {@code JsonObject} argument to an instance of {@code CommandResponse}.
     * Any exception that is thrown during deserialization will be subsumed as cause of a {@code JsonParseException}.
     *
     * @param jsonObject the JSON object to deserialize.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws JsonParseException if deserialization failed.
     * @since 2.3.0
     */
    public T deserialize(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        checkJsonObjectNotNull(jsonObject);
        checkNotNull(dittoHeaders, "dittoHeaders");
        try {
            return deserializationFunction.deserializeCommandResponse(new DefaultDeserializationContext(jsonObject,
                    dittoHeaders,
                    validateCommandResponseType(deserializeCommandResponseTypeOrThrow(jsonObject)),
                    deserializeHttpStatusOrThrow(jsonObject)));
        } catch (final Exception e) {
            throw newJsonParseException(e);
        }
    }

    private static String deserializeCommandResponseTypeOrThrow(final JsonObject jsonObject) {
        return jsonObject.getValueOrThrow(CommandResponse.JsonFields.TYPE);
    }

    private String validateCommandResponseType(final String deserializedCommandResponseType) {
        if (expectedCommandResponseType.equals(deserializedCommandResponseType)) {
            return deserializedCommandResponseType;
        } else {
            throw new JsonParseException(MessageFormat.format("Value <{0}> for <{1}> does not match <{2}>.",
                    deserializedCommandResponseType,
                    CommandResponse.JsonFields.TYPE.getPointer(),
                    expectedCommandResponseType));
        }
    }

    private static HttpStatus deserializeHttpStatusOrThrow(final JsonObject jsonObject)
            throws HttpStatusCodeOutOfRangeException {

        return HttpStatus.getInstance(jsonObject.getValueOrThrow(CommandResponse.JsonFields.STATUS));
    }

    private JsonParseException newJsonParseException(final Exception cause) {
        final String pattern = "Failed to deserialize JSON object to a command response of type <{0}>: {1}";
        return JsonParseException.newBuilder()
                .message(MessageFormat.format(pattern, expectedCommandResponseType, cause.getMessage()))
                .cause(cause)
                .build();
    }

    /**
     * Function that actually deserializes a {@code CommandResponse} from a provided {@link DeserializationContext}.
     *
     * @param <T> the type of the deserialized {@code CommandResponse}.
     * @since 2.3.0
     */
    @FunctionalInterface
    public interface DeserializationFunction<T extends CommandResponse<?>> {

        /**
         * Deserializes a {@code CommandResponse} from the specified {@code DeserializationContext} argument.
         * Any exception thrown by this method will be wrapped with a {@link JsonParseException}.
         *
         * @param context provides information for deserializing a {@code CommandResponse}.
         * @return the deserialized {@code CommandResponse}.
         * @throws NullPointerException if {@code context} is {@code null}.
         */
        T deserializeCommandResponse(DeserializationContext context);

    }

    /**
     * The context for deserializing a JSON object to a {@code CommandResponse}.
     *
     * @since 2.3.0
     */
    public interface DeserializationContext {

        /**
         * Returns the JSON object to be deserialized.
         *
         * @return the JSON object.
         */
        JsonObject getJsonObject();

        /**
         * Returns the {@code DittoHeaders} of the {@code CommandResponse} to be deserialized.
         *
         * @return the DittoHeaders.
         */
        DittoHeaders getDittoHeaders();

        /**
         * Returns the already deserialized command response type.
         *
         * @return the command response type.
         */
        String getDeserializedType();

        /**
         * Returns the already deserialized HTTP status.
         *
         * @return the HTTP status.
         */
        HttpStatus getDeserializedHttpStatus();

    }

    @Immutable
    private static final class DefaultDeserializationContext implements DeserializationContext {

        private final JsonObject jsonObject;
        private final DittoHeaders dittoHeaders;
        private final String type;
        private final HttpStatus httpStatus;

        // Introduce a builder if the number of constructor arguments grows
        // too big.
        private DefaultDeserializationContext(final JsonObject jsonObject,
                final DittoHeaders dittoHeaders,
                final String type,
                final HttpStatus httpStatus) {

            this.jsonObject = checkJsonObjectNotNull(jsonObject);
            this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders");
            this.type = checkNotNull(type, "type");
            this.httpStatus = checkNotNull(httpStatus, "httpStatus");
        }

        @Override
        public JsonObject getJsonObject() {
            return jsonObject;
        }

        @Override
        public DittoHeaders getDittoHeaders() {
            return dittoHeaders;
        }

        @Override
        public String getDeserializedType() {
            return type;
        }

        @Override
        public HttpStatus getDeserializedHttpStatus() {
            return httpStatus;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final DefaultDeserializationContext that = (DefaultDeserializationContext) o;
            return jsonObject.equals(that.jsonObject) &&
                    dittoHeaders.equals(that.dittoHeaders) &&
                    type.equals(that.type) &&
                    httpStatus.equals(that.httpStatus);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jsonObject, dittoHeaders, type, httpStatus);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "jsonObject=" + jsonObject +
                    ", dittoHeaders=" + dittoHeaders +
                    ", type=" + type +
                    ", httpStatus=" + httpStatus +
                    "]";
        }

    }

}
