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
package org.eclipse.ditto.model.base.exceptions;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.atteo.classindex.IndexSubclasses;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersSettable;
import org.eclipse.ditto.model.base.headers.WithManifest;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Parent RuntimeException for all RuntimeExceptions of Ditto.
 */
@IndexSubclasses
public class DittoRuntimeException extends RuntimeException
        implements Jsonifiable.WithPredicate<JsonObject, JsonField>, DittoHeadersSettable<DittoRuntimeException>,
        WithManifest {

    private static final long serialVersionUID = -7010323324132561106L;

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final String description;
    private final URI href;
    private final transient DittoHeaders dittoHeaders; // not serializable!

    /**
     * Constructs a new {@code DittoRuntimeException} object.
     *
     * @param errorCode a code which uniquely identifies the exception.
     * @param statusCode the HTTP status code.
     * @param dittoHeaders the headers with which this Exception should be reported back to the user.
     * @param message the detail message for later retrieval with {@link #getMessage()}.
     * @param description a description with further information about the exception.
     * @param cause the cause of the exception for later retrieval with {@link #getCause()}.
     * @param href a link to a resource which provides further information about the exception.
     * @throws NullPointerException if {@code errorCode}, {@code statusCode} or {@code dittoHeaders} is {@code null}.
     * @deprecated as of 2.0.0 please use
     * {@link DittoRuntimeException#DittoRuntimeException(String, HttpStatus, DittoHeaders, String, String, Throwable, URI)}
     * instead.
     */
    @Deprecated
    protected DittoRuntimeException(final String errorCode,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        this(errorCode, checkNotNull(statusCode, "HTTP status").getAsHttpStatus(), dittoHeaders, message, description,
                cause, href);
    }

    /**
     * Constructs a new {@code DittoRuntimeException} object.
     *
     * @param errorCode a code which uniquely identifies the exception.
     * @param httpStatus the HTTP status.
     * @param dittoHeaders the headers with which this Exception should be reported back to the user.
     * @param message the detail message for later retrieval with {@link #getMessage()}.
     * @param description a description with further information about the exception.
     * @param cause the cause of the exception for later retrieval with {@link #getCause()}.
     * @param href a link to a resource which provides further information about the exception.
     * @throws NullPointerException if {@code errorCode}, {@code httpStatus} or {@code dittoHeaders} is {@code null}.
     * @since 2.0.0
     */
    protected DittoRuntimeException(final String errorCode,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(message, cause);
        this.errorCode = checkNotNull(errorCode, "error code");
        this.httpStatus = checkNotNull(httpStatus, "httpStatus");
        this.dittoHeaders = checkNotNull(dittoHeaders, "Ditto headers");
        this.description = description;
        this.href = href;
    }

    /**
     * Each subclass should override this method to provide an implementation of {@code
     * DittoRuntimeExceptionBuilder}.
     * <p>
     * Per default, an instance {@link Builder} is used, which builds a generic {@code DittoRuntimeException}.
     *
     * @return A builder to construct a DittoRuntimeException.
     */
    protected DittoRuntimeExceptionBuilder<? extends DittoRuntimeException> getEmptyBuilder() {
        return new Builder(errorCode, httpStatus);
    }

    /**
     * Construct a builder {@code b} such that {@code b.build()} has identical class and fields as {@code this}.
     * <p>
     * A subclass should extend this method if it adds another field.
     *
     * @return A builder to construct an identical copy of {@code this}.
     */
    public DittoRuntimeExceptionBuilder<? extends DittoRuntimeException> getBuilder() {
        return getEmptyBuilder()
                .dittoHeaders(dittoHeaders)
                .message(getMessage())
                .cause(getCause())
                .description(description)
                .href(href);
    }

    /**
     * Takes the throwable and tries to map it to a DittoRuntimeException.
     * <p>
     * If the throwable is a {@link CompletionException} or a {@link ExecutionException},
     * this method tries to map the cause of this exception to a DittoRuntimeException.
     * </p>
     *
     * @param throwable the throwable to map.
     * @param alternativeExceptionBuilder used to build an alternative DittoRuntimeException if the throwable could not
     * be mapped.
     * @return either the mapped exception or the exception built by {@code alternativeExceptionBuilder}.
     */
    public static DittoRuntimeException asDittoRuntimeException(final Throwable throwable,
            final Function<Throwable, DittoRuntimeException> alternativeExceptionBuilder) {

        final Throwable cause = getRootCause(throwable);
        if (cause instanceof DittoRuntimeException) {
            return (DittoRuntimeException) cause;
        }

        return alternativeExceptionBuilder.apply(cause);
    }

    private static Throwable getRootCause(final Throwable throwable) {
        if (throwable instanceof CompletionException || throwable instanceof ExecutionException) {
            @Nullable final Throwable cause = throwable.getCause();
            if (null != cause) {
                return getRootCause(cause);
            }
        }

        return throwable;
    }

    /**
     * Returns a new mutable builder for fluently creating instances of {@code DittoRuntimeException}s..
     *
     * @param errorCode a code which uniquely identifies the exception.
     * @param statusCode the HTTP status code.
     * @return the new builder.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code errorCode} is empty.
     * @deprecated as of 2.0.0 please use {@link #newBuilder(String, HttpStatus)} instead.
     */
    @Deprecated
    public static Builder newBuilder(final String errorCode, final HttpStatusCode statusCode) {
        return new Builder(errorCode, statusCode.getAsHttpStatus());
    }

    /**
     * Returns a new mutable builder for fluently creating instances of {@code DittoRuntimeException}s..
     *
     * @param errorCode a code which uniquely identifies the exception.
     * @param httpStatus the HTTP status.
     * @return the new builder.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code errorCode} is empty.
     * @since 2.0.0
     */
    public static Builder newBuilder(final String errorCode, final HttpStatus httpStatus) {
        return new Builder(errorCode, httpStatus);
    }

    /**
     * Returns a new mutable builder with a fluent API for a {@code dittoRuntimeException}. The builder is already
     * initialized with the properties of the given exception.
     *
     * @param dittoRuntimeException the exception to be copied.
     * @return the new builder.
     * @throws NullPointerException if {@code dittoRuntimeException} is {@code null}.
     */
    public static DittoRuntimeExceptionBuilder<? extends DittoRuntimeException> newBuilder(
            final DittoRuntimeException dittoRuntimeException) {

        checkNotNull(dittoRuntimeException, "dittoRuntimeException to be copied");

        return dittoRuntimeException.getBuilder()
                .dittoHeaders(dittoRuntimeException.dittoHeaders)
                .message(dittoRuntimeException.getMessage())
                .description(dittoRuntimeException.description)
                .cause(dittoRuntimeException.getCause())
                .href(dittoRuntimeException.href);
    }

    /**
     * Read the href field from the json object.
     *
     * @param jsonObject the object.
     * @return Optional containing the href if it was part of the json object.
     * @throws NullPointerException if {@code jsonObject} was null.
     * @deprecated since 1.3.0; might be removed from public API in future releases.
     */
    @Deprecated
    protected static Optional<URI> readHRef(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");
        return jsonObject.getValue(JsonFields.HREF).map(URI::create);
    }

    /**
     * Read the message field from the json object.
     *
     * @param jsonObject the object.
     * @return the message.
     * @throws NullPointerException if {@code jsonObject} was null.
     * @throws JsonMissingFieldException if this JsonObject did not contain a value at all at the defined location.
     * @throws JsonParseException if this JsonObject contained a value at the defined location with a type which is
     * different from {@code T}.
     * @deprecated since 1.3.0; might be removed from public API in future releases.
     */
    @Deprecated
    protected static String readMessage(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");
        return jsonObject.getValueOrThrow(JsonFields.MESSAGE);
    }

    /**
     * Read the description field from the json object.
     *
     * @param jsonObject the object.
     * @return Optional containing the description if it was part of the json object.
     * @throws NullPointerException if {@code jsonObject} was null.
     * @deprecated since 1.3.0; might be removed from public API in future releases.
     */
    @Deprecated
    protected static Optional<String> readDescription(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");
        return jsonObject.getValue(JsonFields.DESCRIPTION);
    }

    /**
     * Returns the error code to uniquely identify this exception.
     *
     * @return the error code.
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Retrieves the required HttpStatusCode with which this Exception should be reported back to the user.
     *
     * @return the HttpStatusCode.
     * @deprecated as of 2.0.0 please use {@link #getHttpStatus()} instead.
     */
    @Deprecated
    public HttpStatusCode getStatusCode() {
        return HttpStatusCode.forInt(httpStatus.getCode()).orElseThrow(() -> {

            // This might happen at runtime when httpStatus has a code which is
            // not reflected as constant in HttpStatusCode.
            final String msgPattern = "Found no HttpStatusCode for int <{0}>!";
            return new IllegalStateException(MessageFormat.format(msgPattern, httpStatus.getCode()));
        });
    }

    /**
     * Retrieves the required HttpStatus with which this Exception should be reported back to the user.
     *
     * @return the HttpStatus.
     * @since 2.0.0
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    /**
     * Each DittoRuntimeException inheriting from DittoRuntimeException must implement its custom
     * {@link #setDittoHeaders(DittoHeaders)}. If this is not done, the concrete type of the DittoRuntimeException is
     * erased when this method is invoked.
     * Unfortunately this can't be enforced without API breakage.
     * TODO Ditto 2.0: fix that
     *
     * @param dittoHeaders the DittoHeaders to set.
     * @return the newly created object with the set DittoHeaders.
     * @throws NullPointerException if the passed {@code dittoHeaders} is null.
     */
    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newBuilder(this).dittoHeaders(dittoHeaders).build();
    }

    @Override
    public JsonSchemaVersion getImplementedSchemaVersion() {
        return dittoHeaders.getSchemaVersion().orElse(getLatestSchemaVersion());
    }

    /**
     * Returns the description which should be reported to the user.
     *
     * @return the description.
     */
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns a link with which the user can find further information regarding this exception.
     *
     * @return a link to provide the user with further information about this exception.
     */
    public Optional<URI> getHref() {
        return Optional.ofNullable(href);
    }

    @Override
    public String getManifest() {
        return getErrorCode();
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoRuntimeException that = (DittoRuntimeException) o;
        return Objects.equals(errorCode, that.errorCode) &&
                Objects.equals(httpStatus, that.httpStatus) &&
                Objects.equals(description, that.description) &&
                Objects.equals(href, that.href);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorCode, httpStatus, description, href);
    }

    /**
     * Returns all non hidden marked fields of this exception.
     *
     * @return a JSON object representation of this exception including only non hidden marked fields.
     */
    @Override
    public JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final Predicate<JsonField> nonNullAndCustomDefined = predicate.and(JsonField.isValueNonNull());

        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                .set(JsonFields.STATUS, httpStatus.getCode(), nonNullAndCustomDefined)
                .set(JsonFields.ERROR_CODE, errorCode, nonNullAndCustomDefined)
                .set(JsonFields.MESSAGE, getMessage(), nonNullAndCustomDefined)
                .set(JsonFields.DESCRIPTION, description, nonNullAndCustomDefined)
                .set(JsonFields.HREF, href != null ? href.toString() : null, nonNullAndCustomDefined);

        appendToJson(jsonObjectBuilder, nonNullAndCustomDefined);

        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code DittoRuntimeException} from a JSON object.
     *
     * @param jsonObject the JSON object of which the exception is to be created.
     * @param dittoHeaders the headers of the exception.
     * @param builder the builder for the exception.
     * @param <T> the type of the DittoRuntimeException.
     * @return the exception.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected.
     * format.
     * @since 1.3.0
     */
    public static <T extends DittoRuntimeException> T fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders, final DittoRuntimeExceptionBuilder<T> builder) {
        checkNotNull(builder, "builder");
        readDescription(jsonObject).ifPresent(builder::description);
        readHRef(jsonObject).ifPresent(builder::href);

        return builder.dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .build();
    }

    /**
     * Creates a new {@code DittoRuntimeException} from a message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders dittoHeaders the headers of the command which resulted in this exception.
     * @param builder the builder for the exception.
     * @param <T> the type of the DittoRuntimeException.
     * @return the exception.
     * @throws NullPointerException if {@code dittoHeaders} or {@code builder} argument is {@code null}.
     * @since 1.3.0
     */
    public static <T extends DittoRuntimeException> T fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders, final DittoRuntimeExceptionBuilder<T> builder) {
        checkNotNull(builder, "builder");

        return builder
                .dittoHeaders(dittoHeaders)
                .message(message)
                .build();
    }

    /**
     * Allows to append exception-specific fields to the passed {@code jsonObjectBuilder}.
     *
     * @param jsonObjectBuilder the JsonObjectBuilder to add the fields to.
     * @param predicate the predicate to evaluate when adding the payload (already contains the schema version and that
     * the field is not null).
     */
    protected void appendToJson(final JsonObjectBuilder jsonObjectBuilder, final Predicate<JsonField> predicate) {
        // empty per default
    }

    protected <T extends DittoRuntimeException> DittoRuntimeExceptionBuilder<T> toBuilder(
            final DittoRuntimeExceptionBuilder<T> builder) {
        builder.message(getMessage());
        builder.dittoHeaders(getDittoHeaders());
        builder.cause(getCause());
        getHref().ifPresent(builder::href);
        getDescription().ifPresent(builder::description);
        return builder;
    }

    /**
     * Deserialize an error whose java class isn't known.
     *
     * @param jsonObject the error object.
     * @param headers the headers.
     * @return a generic {@code DittoRuntimeException} object if the JSON can be parsed as such; an empty optional
     * otherwise.
     */
    public static Optional<DittoRuntimeException> fromUnknownErrorJson(final JsonObject jsonObject,
            final DittoHeaders headers) {

        final DittoRuntimeException result;
        final Optional<String> errorCodeOptional = jsonObject.getValue(JsonFields.ERROR_CODE);
        if (errorCodeOptional.isPresent()) {
            final Optional<HttpStatus> httpStatusOptional = getHttpStatus(jsonObject);
            if (httpStatusOptional.isPresent()) {
                final Builder builder = new Builder(errorCodeOptional.get(), httpStatusOptional.get());
                builder.dittoHeaders(headers);
                jsonObject.getValue(JsonFields.MESSAGE).ifPresent(builder::message);
                jsonObject.getValue(JsonFields.DESCRIPTION).ifPresent(builder::description);
                getHref(jsonObject).ifPresent(builder::href);
                result = builder.build();
            } else {
                result = null;
            }
        } else {
            result = null;
        }
        return Optional.ofNullable(result);
    }

    private static Optional<HttpStatus> getHttpStatus(final JsonObject jsonObject) {
        return jsonObject.getValue(JsonFields.STATUS).flatMap(HttpStatus::tryGetInstance);
    }

    private static Optional<URI> getHref(final JsonObject jsonObject) {
        final Function<String, URI> uriForStringOrNull = uriString -> {
            try {
                return new URI(uriString);
            } catch (final URISyntaxException e) {
                return null;
            }
        };

        return jsonObject.getValue(JsonFields.HREF).map(uriForStringOrNull);
    }

    @Override
    public String toString() {
        return getClass().getName() + " [" +
                "message='" + getMessage() + '\'' +
                ", errorCode=" + errorCode +
                ", httpStatus=" + httpStatus +
                ", description='" + description + '\'' +
                ", href=" + href +
                ", dittoHeaders=" + dittoHeaders +
                ']';
    }

    /**
     * A mutable builder with a fluent API for a {@link DittoRuntimeException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<DittoRuntimeException> {

        private final String errorCode;
        private final HttpStatus httpStatus;

        private Builder(final String theErrorCode, final HttpStatus httpStatus) {
            checkNotNull(theErrorCode, "exception error code");
            errorCode = checkNotEmpty(theErrorCode, "exception error code");
            this.httpStatus = checkNotNull(httpStatus, "httpStatus");
        }

        @Override
        protected DittoRuntimeException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new DittoRuntimeException(errorCode, httpStatus, dittoHeaders, message, description, cause, href);
        }
    }

    /**
     * An enumeration of the known {@link JsonField}s of a {@code DittoRuntimeException}.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * JSON field containing the HTTP status code of the exception.
         */
        public static final JsonFieldDefinition<Integer> STATUS =
                JsonFactory.newIntFieldDefinition("status", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the error code of the exception.
         */
        public static final JsonFieldDefinition<String> ERROR_CODE =
                JsonFactory.newStringFieldDefinition("error", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the message of the exception.
         */
        public static final JsonFieldDefinition<String> MESSAGE =
                JsonFactory.newStringFieldDefinition("message", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the description of the message.
         */
        public static final JsonFieldDefinition<String> DESCRIPTION =
                JsonFactory.newStringFieldDefinition("description", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the link to further information about the exception.
         */
        public static final JsonFieldDefinition<String> HREF =
                JsonFactory.newStringFieldDefinition("href", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
