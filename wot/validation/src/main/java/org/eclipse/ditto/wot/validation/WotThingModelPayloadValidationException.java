/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.validation;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.io.Serial;
import java.net.URI;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableException;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.wot.model.WotException;
import org.eclipse.ditto.wot.model.WotThingModelInvalidException;

/**
 * Exception thrown when a Ditto Thing (or parts of it), so the payload, could not be validated against the WoT Model.
 *
 * @since 3.6.0
 */
@Immutable
@JsonParsableException(errorCode = WotThingModelPayloadValidationException.ERROR_CODE)
public final class WotThingModelPayloadValidationException extends DittoRuntimeException implements WotException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "payload.validation.error";

    private static final String DEFAULT_MESSAGE =
            "The provided payload did not conform to the specified WoT (Web of Things) model.";

    @Serial
    private static final long serialVersionUID = -236554134452227841L;

    private final Map<JsonPointer, List<String>> validationDetails;

    private WotThingModelPayloadValidationException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href,
            final Map<JsonPointer, List<String>> validationDetails) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, dittoHeaders, message, description, cause, href);
        this.validationDetails = validationDetails;
    }

    /**
     * A mutable builder for a {@code WotThingModelPayloadValidationException}.
     *
     * @param validationDescription the details about what was not valid.
     * @return the builder.
     * @throws NullPointerException if {@code validationDescription} is {@code null}.
     */
    public static Builder newBuilder(final String validationDescription) {
        return new Builder(validationDescription);
    }

    /**
     * Constructs a new {@code WotThingModelPayloadValidationException} object with the exception message extracted from the given
     * JSON object.
     *
     * @param jsonObject the JSON to read the {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException.JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new WotThingModelPayloadValidationException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this JsonObject did not contain an error message.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static WotThingModelPayloadValidationException fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return DittoRuntimeException.fromJson(jsonObject, dittoHeaders,
                new Builder(readValidationDetails(jsonObject, dittoHeaders))
        );
    }

    @Override
    public DittoRuntimeException setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Builder(validationDetails)
                .message(getMessage())
                .description(getDescription().orElse(null))
                .cause(getCause())
                .href(getHref().orElse(null))
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static Map<JsonPointer, List<String>> readValidationDetails(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        checkNotNull(jsonObject, "JSON object");
        return jsonObject.getValue(JsonFields.VALIDATION_DETAILS)
                .map(validationDetails -> validationDetails.stream()
                        .map(field -> new AbstractMap.SimpleEntry<>(
                                JsonPointer.of(field.getKey().toString()),
                                field.getValue().asArray().stream().map(JsonValue::formatAsString).toList())
                        )
                ).stream()
                .flatMap(Function.identity())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> {
                    throw WotThingModelInvalidException.newBuilder(String.format("Validation details: Duplicate key %s", u))
                            .dittoHeaders(dittoHeaders)
                            .build();
                }, LinkedHashMap::new));
    }

    @Override
    protected void appendToJson(final JsonObjectBuilder jsonObjectBuilder, final Predicate<JsonField> predicate) {
        final JsonObject detailsObject = validationDetails.entrySet().stream()
                .map(entry -> JsonField.newInstance(entry.getKey().toString(),
                        entry.getValue().stream()
                                .map(JsonValue::of)
                                .collect(JsonCollectors.valuesToArray())
                ))
                .collect(JsonCollectors.fieldsToObject());
        if (!detailsObject.isEmpty()) {
            jsonObjectBuilder.set(JsonFields.VALIDATION_DETAILS, detailsObject, predicate);
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
        final WotThingModelPayloadValidationException that = (WotThingModelPayloadValidationException) o;
        return Objects.equals(validationDetails, that.validationDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), validationDetails);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "message='" + getMessage() + '\'' +
                ", errorCode=" + getErrorCode() +
                ", httpStatus=" + getHttpStatus() +
                ", description='" + getDescription().orElse(null) + '\'' +
                ", href=" + getHref().orElse(null) +
                ", validationDetails=" + validationDetails +
                ", dittoHeaders=" + getDittoHeaders() +
                ']';
    }

    /**
     * A mutable builder with a fluent API for a {@link WotThingModelPayloadValidationException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<WotThingModelPayloadValidationException> {

        private Map<JsonPointer, List<String>> validationDetails;

        private Builder() {
            validationDetails = new LinkedHashMap<>();
            message(DEFAULT_MESSAGE);
        }

        private Builder(final String validationDescription) {
            this();
            description(checkNotNull(validationDescription, "validationDescription"));
        }

        private Builder(final Map<JsonPointer, List<String>> validationDetails) {
            this();
            this.validationDetails = validationDetails;
        }

        public Builder addValidationDetail(final JsonPointer jsonPointer, final List<String> validationErrors) {
            validationDetails.put(jsonPointer, validationErrors);
            return this;
        }

        @Override
        protected WotThingModelPayloadValidationException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new WotThingModelPayloadValidationException(dittoHeaders, message, description, cause, href,
                    validationDetails);
        }

    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a {@code WotThingModelPayloadValidationException}.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * JSON field containing the validation details.
         */
        static final JsonFieldDefinition<JsonObject> VALIDATION_DETAILS =
                JsonFactory.newJsonObjectFieldDefinition("validationDetails", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }
}
