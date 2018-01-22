/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.base.exceptions;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.WithManifest;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Parent RuntimeException for all RuntimeExceptions of Ditto.
 */
public class DittoRuntimeException extends RuntimeException implements
        Jsonifiable.WithPredicate<JsonObject, JsonField>, WithDittoHeaders<DittoRuntimeException>, WithManifest {

    private static final long serialVersionUID = -7010323324132561106L;

    private final String errorCode;
    private final HttpStatusCode statusCode;
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
     */
    protected DittoRuntimeException(final String errorCode,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(message, cause);
        this.errorCode = checkNotNull(errorCode, "error code");
        this.statusCode = checkNotNull(statusCode, "HTTP status");
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
        return new Builder(errorCode, statusCode);
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
     * Returns a new mutable builder for fluently creating instances of {@code DittoRuntimeException}s..
     *
     * @param errorCode a code which uniquely identifies the exception.
     * @param statusCode the HTTP status code.
     * @return the new builder.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code errorCode} is empty.
     */
    public static Builder newBuilder(final String errorCode, final HttpStatusCode statusCode) {
        return new Builder(errorCode, statusCode);
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

    protected static String readMessage(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        return jsonObject.getValueOrThrow(JsonFields.MESSAGE);
    }

    protected static Optional<String> readDescription(final JsonObject jsonObject) {
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
     */
    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

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

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoRuntimeException that = (DittoRuntimeException) o;
        return Objects.equals(errorCode, that.errorCode)
                && statusCode == that.statusCode
                && Objects.equals(description, that.description)
                && Objects.equals(href, that.href);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorCode, statusCode, description, href);
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
                .set(JsonFields.STATUS, statusCode.toInt(), nonNullAndCustomDefined)
                .set(JsonFields.ERROR_CODE, errorCode, nonNullAndCustomDefined)
                .set(JsonFields.MESSAGE, getMessage(), nonNullAndCustomDefined)
                .set(JsonFields.DESCRIPTION, description, nonNullAndCustomDefined)
                .set(JsonFields.HREF, href != null ? href.toString() : null, nonNullAndCustomDefined);

        appendToJson(jsonObjectBuilder, nonNullAndCustomDefined);

        return jsonObjectBuilder.build();
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


    /**
     * A mutable builder with a fluent API for a {@link DittoRuntimeException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<DittoRuntimeException> {

        private final String errorCode;
        private final HttpStatusCode statusCode;

        private Builder(final String theErrorCode, final HttpStatusCode theStatusCode) {
            checkNotNull(theErrorCode, "exception error code");
            errorCode = checkNotEmpty(theErrorCode, "exception error code");
            statusCode = checkNotNull(theStatusCode, "exception HTTP status code");
        }

        @Override
        protected DittoRuntimeException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new DittoRuntimeException(errorCode, statusCode, dittoHeaders, message, description, cause, href);
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
                JsonFactory.newIntFieldDefinition("status", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the error code of the exception.
         */
        public static final JsonFieldDefinition<String> ERROR_CODE =
                JsonFactory.newStringFieldDefinition("error", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the message of the exception.
         */
        public static final JsonFieldDefinition<String> MESSAGE =
                JsonFactory.newStringFieldDefinition("message", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the description of the message.
         */
        public static final JsonFieldDefinition<String> DESCRIPTION =
                JsonFactory.newStringFieldDefinition("description", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the link to further information about the exception.
         */
        public static final JsonFieldDefinition<String> HREF =
                JsonFactory.newStringFieldDefinition("href", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
