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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * This exception is thrown to indicate that a particular {@link Adaptable}
 * is illegal in a certain context.
 *
 * @since 2.3.0
 */
@JsonParsableException(errorCode = IllegalAdaptableException.ERROR_CODE)
public final class IllegalAdaptableException extends DittoRuntimeException {

    /**
     * Error code of {@code IllegalAdaptableException}.
     */
    public static final String ERROR_CODE = "things.protocol.adapter:adaptable.illegal";

    /**
     * Generic default description of an {@code IllegalAdaptableException}.
     */
    static final String DEFAULT_DESCRIPTION = "Please ensure that the Adaptable matches the expectations.";

    /**
     * HTTP status of an {@code IllegalAdaptableException}.
     */
    static final HttpStatus HTTP_STATUS = HttpStatus.UNPROCESSABLE_ENTITY;

    static final JsonFieldDefinition<String> JSON_FIELD_TOPIC_PATH =
            JsonFieldDefinition.ofString("topicPath", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_FIELD_SIGNAL_TYPE =
            JsonFieldDefinition.ofString("signalType", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final long serialVersionUID = 1552465013185426687L;

    private final transient TopicPath topicPath;
    @Nullable private final transient CharSequence signalType;

    private IllegalAdaptableException(final String errorCode,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders,
            final Builder builder) {

        super(errorCode,
                httpStatus,
                dittoHeaders,
                builder.message,
                builder.description,
                builder.cause,
                builder.href);
        topicPath = builder.topicPath;
        signalType = builder.signalType;
    }

    /**
     * Returns a new instance of {@code IllegalAdaptableException}.
     *
     * @param message the detail message of the exception.
     * @param adaptable the illegal {@code Adaptable}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code message} is blank.
     */
    public static IllegalAdaptableException newInstance(final String message, final Adaptable adaptable) {
        return newInstance(message, DEFAULT_DESCRIPTION, adaptable);
    }

    /**
     * Returns a new instance of {@code IllegalAdaptableException}.
     *
     * @param message the detail message of the exception.
     * @param description the description of the exception.
     * @param adaptable the illegal {@code Adaptable}.
     * @throws NullPointerException if any argument but {@code description} is {@code null}.
     * @throws IllegalArgumentException if {@code message} is blank.
     */
    public static IllegalAdaptableException newInstance(final String message,
            @Nullable final String description,
            final Adaptable adaptable) {

        return newBuilder(message, adaptable)
                .withDescription(description)
                .build();
    }

    /**
     * Returns a mutable builder with a fluent API for constructing an instance of {@code IllegalAdaptableException}.
     *
     * @param message the detail message of the exception.
     * @param adaptable the illegal {@code Adaptable} that is subject to the exception.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code message} is blank.
     */
    public static Builder newBuilder(final String message, final Adaptable adaptable) {
        ConditionChecker.checkNotNull(adaptable, "adaptable");
        return new Builder(message, adaptable.getTopicPath(), adaptable.getDittoHeaders());
    }

    /**
     * Deserializes the specified {@code JsonObject} argument to an {@code IllegalAdaptableException}.
     *
     * @param jsonObject the JSON object to be deserialized.
     * @param dittoHeaders the headers of the deserialized exception.
     * @return the new {@code IllegalAdaptableException}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain all mandatory
     * fields.
     * @throws JsonParseException if {@code jsonObject} was not in the expected format.
     */
    public static IllegalAdaptableException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        ConditionChecker.checkNotNull(jsonObject, "jsonObject");
        try {
            return new IllegalAdaptableException(
                    deserializeErrorCode(jsonObject),
                    deserializeHttpStatus(jsonObject),
                    dittoHeaders,
                    new Builder(jsonObject.getValueOrThrow(JsonFields.MESSAGE),
                            deserializeTopicPath(jsonObject),
                            dittoHeaders)
                            .withSignalType(deserializeSignalType(jsonObject).orElse(null))
                            .withDescription(jsonObject.getValue(JsonFields.DESCRIPTION).orElse(null))
                            .withHref(deserializeHref(jsonObject).orElse(null))
            );
        } catch (final Exception e) {
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format("Failed to deserialize JSON object to a {0}: {1}",
                            IllegalAdaptableException.class.getSimpleName(),
                            e.getMessage()))
                    .cause(e)
                    .build();
        }
    }

    private static String deserializeErrorCode(final JsonObject jsonObject) {
        final JsonFieldDefinition<String> fieldDefinition = JsonFields.ERROR_CODE;
        final String result = jsonObject.getValueOrThrow(fieldDefinition);
        if (!ERROR_CODE.equals(result)) {
            throw new JsonParseException(MessageFormat.format(
                    "Error code <{0}> of field <{1}> differs from the expected <{2}>.",
                    result,
                    fieldDefinition.getPointer(),
                    ERROR_CODE
            ));
        }
        return result;
    }

    private static HttpStatus deserializeHttpStatus(final JsonObject jsonObject) {
        final JsonFieldDefinition<Integer> fieldDefinition = JsonFields.STATUS;
        final Integer statusCode = jsonObject.getValueOrThrow(fieldDefinition);
        if (!Objects.equals(HTTP_STATUS.getCode(), statusCode)) {
            throw new JsonParseException(MessageFormat.format(
                    "HTTP status code <{0}> of field <{1}> differs from the expected <{2}>.",
                    statusCode,
                    fieldDefinition.getPointer(),
                    HTTP_STATUS.getCode()
            ));
        } else {
            return HTTP_STATUS;
        }
    }

    private static TopicPath deserializeTopicPath(final JsonObject jsonObject) {
        final String deserializedTopicPathOptional = jsonObject.getValueOrThrow(JSON_FIELD_TOPIC_PATH);
        return ProtocolFactory.newTopicPath(deserializedTopicPathOptional);
    }

    private static Optional<String> deserializeSignalType(final JsonObject jsonObject) {
        final Optional<String> result;
        final JsonFieldDefinition<String> fieldDefinition = JSON_FIELD_SIGNAL_TYPE;
        final Optional<String> deserializedSignalTypeOptional = jsonObject.getValue(fieldDefinition);
        if (deserializedSignalTypeOptional.isPresent() && isBlank(deserializedSignalTypeOptional.get())) {
            throw new JsonParseException(
                    MessageFormat.format("Value of field <{0}> must not be blank.", fieldDefinition.getPointer())
            );
        } else {
            result = deserializedSignalTypeOptional;
        }
        return result;
    }

    private static boolean isBlank(final CharSequence charSequence) {
        return 0 == charSequence.chars().filter(ch -> !Character.isWhitespace(ch)).count();
    }

    private static Optional<URI> deserializeHref(final JsonObject jsonObject) {
        final JsonFieldDefinition<String> fieldDefinition = JsonFields.HREF;
        try {
            return jsonObject.getValue(fieldDefinition).map(URI::create);
        } catch (final IllegalArgumentException e) {
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format("Syntax of link URI of field <{0}> is invalid: {1}",
                            fieldDefinition.getPointer(),
                            e.getMessage()))
                    .cause(e)
                    .build();
        }
    }

    /**
     * Returns the {@code TopicPath} of the illegal {@code Adaptable}.
     *
     * @return the topic path.
     */
    public TopicPath getTopicPath() {
        return topicPath;
    }

    /**
     * Returns the signal type if available.
     * The signal type is the {@code TYPE} of the {@link org.eclipse.ditto.base.model.signals.Signal} that
     * corresponds to the illegal {@code Adaptable}.
     *
     * @return the optional signal type.
     */
    public Optional<String> getSignalType() {
        return Optional.ofNullable(signalType).map(CharSequence::toString);
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new IllegalAdaptableException(
                getErrorCode(),
                getHttpStatus(),
                dittoHeaders,
                new Builder(getMessage(), topicPath, dittoHeaders)
                        .withSignalType(signalType)
                        .withDescription(getDescription().orElse(null))
                        .withCause(getCause())
                        .withHref(getHref().orElse(null))
        );
    }

    @Override
    protected void appendToJson(final JsonObjectBuilder jsonObjectBuilder, final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(JSON_FIELD_TOPIC_PATH, topicPath.getPath());
        if (null != signalType) {
            jsonObjectBuilder.set(JSON_FIELD_SIGNAL_TYPE, signalType.toString());
        }
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
        final IllegalAdaptableException that = (IllegalAdaptableException) o;
        return Objects.equals(topicPath, that.topicPath) &&
                Objects.equals(signalType, that.signalType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), topicPath, signalType);
    }

    /**
     * A mutable builder with a fluent API for constructing an {@code IllegalAdaptableException}.
     */
    @NotThreadSafe
    public static final class Builder {

        private final String message;
        private final TopicPath topicPath;
        private final DittoHeaders dittoHeaders;
        @Nullable private CharSequence signalType;
        @Nullable private String description;
        @Nullable private Throwable cause;
        @Nullable private URI href;

        private Builder(final String message, final TopicPath topicPath, final DittoHeaders dittoHeaders) {
            this.message = checkCharSequenceArgumentNotBlank(message, "message");
            this.topicPath = ConditionChecker.checkNotNull(topicPath, "topicPath");
            this.dittoHeaders = ConditionChecker.checkNotNull(dittoHeaders, "dittoHeaders");
        }

        private static <T extends CharSequence> T checkCharSequenceArgumentNotBlank(final T charSequence,
                final String argumentName) {

            return ConditionChecker.checkArgument(ConditionChecker.checkNotNull(charSequence, argumentName),
                    argument -> !isBlank(charSequence),
                    () -> MessageFormat.format("The {0} must not be blank.", argumentName));
        }

        /**
         * Sets the specified CharSequence argument as signal type.
         * The signal type is the {@code TYPE} of the {@link org.eclipse.ditto.base.model.signals.Signal} that
         * corresponds to the illegal {@code Adaptable}.
         *
         * @param signalType the signal type.
         * @return this builder instance for method chaining.
         * @throws IllegalArgumentException if {@code signalType} is blank.
         */
        public Builder withSignalType(@Nullable final CharSequence signalType) {
            if (null != signalType) {
                checkCharSequenceArgumentNotBlank(signalType, "signalType");
            }
            this.signalType = signalType;
            return this;
        }

        /**
         * Sets the specified string argument as description.
         * The description provides further information about the cause or possible solution for the failure.
         * If this method is omitted, the constructed {@code IllegalAdaptableException} has a generic default
         * description.
         *
         * @param description the description or {@code null} if explicitly no description is available.
         * @return this builder instance for method chaining.
         */
        public Builder withDescription(@Nullable final String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the specified Throwable argument as cause.
         *
         * @param cause the cause of the constructed {@code IllegalAdaptableException} or {@code null} if the cause
         * is unknown.
         * @return this builder instance for method chaining.
         */
        public Builder withCause(@Nullable final Throwable cause) {
            this.cause = cause;
            return this;
        }

        /**
         * Sets the specified URI argument as HREF.
         * The HREF is a link to a resource which provides further information about the exception to be built.
         *
         * @param href the HREF.
         * @return this builder instance for method chaining.
         */
        public Builder withHref(@Nullable final URI href) {
            this.href = href;
            return this;
        }

        /**
         * Returns a new instance of {@code IllegalAdaptableException}.
         * This is a terminal operation and the builder should not be used anymore after calling this method.
         *
         * @return the new {@code IllegalAdaptableException}.
         */
        public IllegalAdaptableException build() {
            return new IllegalAdaptableException(ERROR_CODE, HTTP_STATUS, dittoHeaders, this);
        }

    }

}
