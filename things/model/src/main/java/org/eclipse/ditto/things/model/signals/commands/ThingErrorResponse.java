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
package org.eclipse.ditto.things.model.signals.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.base.model.signals.commands.AbstractErrorResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Response to a {@link ThingCommand} which wraps the exception thrown while processing the command.
 */
@Immutable
@JsonParsableCommandResponse(type = ThingErrorResponse.TYPE)
public final class ThingErrorResponse extends AbstractErrorResponse<ThingErrorResponse>
        implements ThingCommandResponse<ThingErrorResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + "errorResponse";

    private static final GlobalErrorRegistry GLOBAL_ERROR_REGISTRY = GlobalErrorRegistry.getInstance();
    private static final ThingId FALLBACK_THING_ID = ThingId.of(FALLBACK_ID);

    private final ThingId thingId;
    private final DittoRuntimeException dittoRuntimeException;

    private ThingErrorResponse(final ThingId thingId, final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoRuntimeException.getHttpStatus(), dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.dittoRuntimeException = checkNotNull(dittoRuntimeException, "dittoRuntimeException");
    }

    /**
     * Creates a new {@code ThingErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param dittoRuntimeException the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static ThingErrorResponse of(final DittoRuntimeException dittoRuntimeException) {
        final DittoHeaders dittoHeaders = dittoRuntimeException.getDittoHeaders();
        final String nullableEntityId = dittoHeaders.get(DittoHeaderDefinition.ENTITY_ID.getKey());
        final ThingId thingId = Optional.ofNullable(nullableEntityId)
                .map(entityId -> entityId.substring(entityId.indexOf(":") + 1)) // starts with "thing:" - cut that off!
                .map(ThingId::of)
                .orElse(FALLBACK_THING_ID);
        return of(thingId, dittoRuntimeException, dittoHeaders);
    }

    /**
     * Creates a new {@code ThingErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param thingId the Thing ID which was related to the exception.
     * @param dittoRuntimeException the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static ThingErrorResponse of(final ThingId thingId, final DittoRuntimeException dittoRuntimeException) {
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
    public static ThingErrorResponse of(final ThingId thingId, final DittoRuntimeException dittoRuntimeException,
            final DittoHeaders dittoHeaders) {

        return new ThingErrorResponse(thingId, dittoRuntimeException, dittoHeaders);
    }

    /**
     * Creates a new {@code ThingErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonString}.
     *
     * @param jsonString the JSON string representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the ThingErrorResponse.
     */
    public static ThingErrorResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));

        return fromJson(jsonObject, dittoHeaders);
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
        final String extractedThingId = jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID);
        final ThingId thingId = ThingId.of(extractedThingId);
        final JsonObject payload = jsonObject.getValueOrThrow(CommandResponse.JsonFields.PAYLOAD).asObject();
        final DittoRuntimeException exception = buildExceptionFromJson(GLOBAL_ERROR_REGISTRY, payload, dittoHeaders);
        return of(thingId, exception, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
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
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(CommandResponse.JsonFields.PAYLOAD,
                dittoRuntimeException.toJson(schemaVersion, thePredicate),
                predicate);
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
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(dittoRuntimeException, that.dittoRuntimeException) &&
                super.equals(that);
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
