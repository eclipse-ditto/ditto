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
 * values which are common for all command responses. All remaining required values have to be extracted in
 * {@link CommandResponseJsonDeserializer.FactoryMethodFunction#create(org.eclipse.ditto.base.model.common.HttpStatus)}.
 * There the actual command response object is created, too.
 */
public final class CommandResponseJsonDeserializer<T extends CommandResponse<?>> {

    @Nullable private final JsonObject jsonObject;
    private final String expectedCommandResponseType;
    private final DeserializationFunction<T> deserializationFunction;

    private CommandResponseJsonDeserializer(final CharSequence type,
            @Nullable final JsonObject jsonObject,
            final DeserializationFunction<T> deserializationFunction) {

        this.jsonObject = jsonObject;
        expectedCommandResponseType = ConditionChecker.checkArgument(checkNotNull(type, "type").toString(),
                arg -> !arg.trim().isEmpty(),
                () -> "The type must not be empty or blank.");
        this.deserializationFunction = deserializationFunction;
    }

    /**
     * Constructs a new {@code CommandResponseJsonDeserializer} object.
     *
     * @param type the type of the command response.
     * @param jsonObject the JSON object to deserialize.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code type} is empty or blank.
     * @deprecated as of 2.3.0 please use {@link #newInstance(CharSequence, DeserializationFunction)} instead.
     */
    @Deprecated
    public CommandResponseJsonDeserializer(final String type, final JsonObject jsonObject) {
        this(type, checkJsonObjectNotNull(jsonObject), null);
    }

    private static JsonObject checkJsonObjectNotNull(@Nullable final JsonObject jsonObject) {
        return checkNotNull(jsonObject, "jsonObject");
    }

    /**
     * Constructs a new {@code CommandResponseJsonDeserializer} object.
     *
     * @param type the type of the target command response of deserialization.
     * @param jsonString the JSON string to be deserialized.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code type} is empty or blank or if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonString} does not contain a valid JSON object.
     * @deprecated as of 2.3.0 please use {@link #newInstance(CharSequence, DeserializationFunction)} instead.
     */
    @Deprecated
    public CommandResponseJsonDeserializer(final String type, final String jsonString) {
        this(type, JsonObject.of(jsonString));
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

        return new CommandResponseJsonDeserializer(type,
                null,
                checkNotNull(deserializationFunction, "deserializationFunction"));
    }

    /**
     * Partly deserializes the JSON which was given to this object's constructor. The factory method function which is
     * given to this method is responsible for creating the actual {@code CommandResponseType}. This method receives
     * the partly deserialized values which can be completed by implementors if further values are required.
     *
     * @param factoryMethodFunction creates the actual {@code CommandResponseType} object.
     * @return the command response.
     * @throws NullPointerException if {@code factoryMethodFunction} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the JSON is invalid or if the command response type
     * differs from the expected one.
     * @deprecated as of 2.3.0 please use {@link #deserialize(JsonObject, DittoHeaders)} instead.
     */
    @Deprecated
    public T deserialize(final FactoryMethodFunction<T> factoryMethodFunction) {
        final CommandResponseJsonDeserializer<T> deserializer =
                new CommandResponseJsonDeserializer<>(expectedCommandResponseType,
                        checkJsonObjectNotNull(jsonObject),
                        context -> factoryMethodFunction.create(context.getDeserializedHttpStatus()));

        return deserializer.deserialize(jsonObject, DittoHeaders.empty());
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
     * Represents a function that accepts three arguments to produce a {@code CommandResponse}. The arguments were
     * extracted from a given JSON beforehand.
     *
     * @param <T> the type of the result of the function.
     * @deprecated as of 2.3.0 please use {@link DeserializationFunction} instead.
     */
    @Deprecated
    @FunctionalInterface
    public interface FactoryMethodFunction<T extends CommandResponse<?>> {

        /**
         * Creates a {@code CommandResponse} with the help of the given arguments.
         *
         * @param httpStatus the HTTP status of the response.
         * @return the command response.
         * @since 2.0.0
         */
        T create(HttpStatus httpStatus);

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
