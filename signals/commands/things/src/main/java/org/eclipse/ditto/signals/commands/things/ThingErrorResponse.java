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
package org.eclipse.ditto.signals.commands.things;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingErrorRegistry;

/**
 * Response to a {@link ThingCommand} which wraps the exception thrown while processing the command.
 */
@Immutable
public final class ThingErrorResponse extends AbstractCommandResponse<ThingErrorResponse> implements
        ThingCommandResponse<ThingErrorResponse>, ErrorResponse<ThingErrorResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + "errorResponse";

    private static final ThingErrorRegistry THING_ERROR_REGISTRY = ThingErrorRegistry.newInstance();
    private static final String FALLBACK_ID = "unknown:unknown";
    private static final String FALLBACK_THING_ID = FALLBACK_ID;
    private static final String FALLBACK_ERROR_CODE = FALLBACK_ID;

    private final String thingId;
    private final DittoRuntimeException dittoRuntimeException;

    private ThingErrorResponse(final String thingId, final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoRuntimeException.getStatusCode(), dittoHeaders);
        this.thingId = requireNonNull(thingId, "Thing ID");
        this.dittoRuntimeException =
                requireNonNull(dittoRuntimeException, "The Ditto Runtime Exception must not be null");
    }

    /**
     * Creates a new {@code ThingErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param dittoRuntimeException the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static ThingErrorResponse of(final DittoRuntimeException dittoRuntimeException) {
        return of(FALLBACK_THING_ID, dittoRuntimeException, dittoRuntimeException.getDittoHeaders());
    }

    /**
     * Creates a new {@code ThingErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param thingId the Thing ID which was related to the exception.
     * @param dittoRuntimeException the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static ThingErrorResponse of(final String thingId, final DittoRuntimeException dittoRuntimeException) {
        return new ThingErrorResponse(thingId, dittoRuntimeException, dittoRuntimeException.getDittoHeaders());
    }

    /**
     * Creates a new {@code ThingErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param dittoRuntimeException the exception.
     * @param dittoHeaders the headers of the command which caused the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static ThingErrorResponse of(final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {
        return of(FALLBACK_THING_ID, dittoRuntimeException, dittoHeaders);
    }

    /**
     * Creates a new {@code ThingErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param thingId the Thing's ID.
     * @param dittoRuntimeException the exception.
     * @param dittoHeaders the headers of the command which caused the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static ThingErrorResponse of(final String thingId, final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {
        return new ThingErrorResponse(thingId, dittoRuntimeException, dittoHeaders);
    }

    /**
     * Creates a new {@code ThingErrorResponse} containing the causing {@code DittoRuntimeException} which is deserialized
     * from the passed {@code jsonString}.
     *
     * @param jsonString the JSON string representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the ThingErrorResponse.
     */
    public static ThingErrorResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(THING_ERROR_REGISTRY, jsonString, dittoHeaders);
    }

    /**
     * Creates a new {@code ThingErrorResponse} containing the causing {@code DittoRuntimeException} which is deserialized
     * from the passed {@code jsonString} using a special {@code ThingErrorRegistry}.
     *
     * @param thingErrorRegistry the special {@code ThingErrorRegistry} to use for deserializing the
     * DittoRuntimeException.
     * @param jsonString the JSON string representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the ThingErrorResponse.
     */
    public static ThingErrorResponse fromJson(final ThingErrorRegistry thingErrorRegistry, final String jsonString,
            final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return fromJson(thingErrorRegistry, jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code ThingErrorResponse} containing the causing {@code DittoRuntimeException} which is deserialized
     * from the passed {@code jsonObject}.
     *
     * @param jsonObject the JSON representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the ThingErrorResponse.
     */
    public static ThingErrorResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return fromJson(THING_ERROR_REGISTRY, jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code ThingErrorResponse} containing the causing {@code DittoRuntimeException} which is deserialized
     * from the passed {@code jsonObject} using a special {@code ThingErrorRegistry}.
     *
     * @param thingErrorRegistry the special {@code ThingErrorRegistry} to use for deserializing the
     * DittoRuntimeException.
     * @param jsonObject the JSON representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the ThingErrorResponse.
     */
    public static ThingErrorResponse fromJson(final ThingErrorRegistry thingErrorRegistry, final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        final String thingId = jsonObject.getValue(ThingCommandResponse.JsonFields.JSON_THING_ID)
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                        .fieldName(ThingCommandResponse.JsonFields.JSON_THING_ID.getPointer())
                        .build());
        final JsonObject payload = jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.PAYLOAD).asObject();

        DittoRuntimeException exception;
        try {
            exception = thingErrorRegistry.parse(payload, dittoHeaders);
        } catch (final Exception e) {
            final int status = jsonObject.getValue(CommandResponse.JsonFields.STATUS).orElse(500);
            final String errorCode =
                    payload.getValue(DittoRuntimeException.JsonFields.ERROR_CODE).orElse(FALLBACK_ERROR_CODE);
            final String errorMessage =
                    payload.getValue(DittoRuntimeException.JsonFields.MESSAGE).orElse("An unknown error occurred");
            final String errorDescription = payload.getValue(DittoRuntimeException.JsonFields.DESCRIPTION).orElse("");
            exception =
                    DittoRuntimeException.newBuilder(errorCode,
                            HttpStatusCode.forInt(status).orElse(HttpStatusCode.INTERNAL_SERVER_ERROR))
                            .message(errorMessage)
                            .description(errorDescription)
                            .dittoHeaders(dittoHeaders)
                            .build();
        }

        return of(thingId, exception, dittoHeaders);
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public DittoRuntimeException getDittoRuntimeException() {
        return dittoRuntimeException;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.PAYLOAD,
                dittoRuntimeException.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public ThingErrorResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, dittoRuntimeException, dittoHeaders);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, dittoRuntimeException);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ThingErrorResponse that = (ThingErrorResponse) o;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) &&
                Objects.equals(dittoRuntimeException, that.dittoRuntimeException) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ThingErrorResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId +
                ", dittoRuntimeException=" + dittoRuntimeException +
                "]";
    }

}
