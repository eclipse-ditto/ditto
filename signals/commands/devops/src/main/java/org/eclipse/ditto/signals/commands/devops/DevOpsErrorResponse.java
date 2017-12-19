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
package org.eclipse.ditto.signals.commands.devops;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;

/**
 * Response to a {@link DevOpsCommand} which wraps the exception thrown while processing the command.
 */
@Immutable
public final class DevOpsErrorResponse extends AbstractCommandResponse<DevOpsErrorResponse> implements
        DevOpsCommandResponse<DevOpsErrorResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + "errorResponse";

    @Nullable private final String serviceName;
    @Nullable private final Integer instance;
    private final JsonObject dittoRuntimeException;

    private DevOpsErrorResponse(@Nullable final String serviceName, @Nullable final Integer instance,
            final JsonObject dittoRuntimeException, final DittoHeaders dittoHeaders) {
        super(TYPE,
                HttpStatusCode.forInt(dittoRuntimeException.getValueOrThrow(DittoRuntimeException.JsonFields.STATUS))
                        .orElse(HttpStatusCode.INTERNAL_SERVER_ERROR), dittoHeaders);
        this.serviceName = serviceName;
        this.instance = instance;
        this.dittoRuntimeException =
                requireNonNull(dittoRuntimeException, "The Ditto Runtime Exception must not be null");
    }

    /**
     * Creates a new {@code DevOpsErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param dittoRuntimeException the exception.
     * @param dittoHeaders the headers of the command which caused the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static DevOpsErrorResponse of(@Nullable final String serviceName, @Nullable final Integer instance,
            final JsonObject dittoRuntimeException, final DittoHeaders dittoHeaders) {
        return new DevOpsErrorResponse(serviceName, instance, dittoRuntimeException, dittoHeaders);
    }

    /**
     * Creates a new {@code DevOpsErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonString} using a special {@code ThingErrorRegistry}.
     *
     * @param jsonString the JSON string representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the DevOpsErrorResponse.
     */
    public static DevOpsErrorResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code DevOpsErrorResponse} containing the causing {@code DittoRuntimeException} which is
     * deserialized from the passed {@code jsonObject} using a special {@code ThingErrorRegistry}.
     *
     * @param jsonObject the JSON representation of the causing {@code DittoRuntimeException}.
     * @param dittoHeaders the DittoHeaders to use.
     * @return the DevOpsErrorResponse.
     */
    public static DevOpsErrorResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {

        final String serviceName = jsonObject.getValue(DevOpsCommandResponse.JsonFields.JSON_SERVICE_NAME)
                .orElse(null);
        final Integer instance = jsonObject.getValue(DevOpsCommandResponse.JsonFields.JSON_INSTANCE)
                .orElse(null);
        final JsonObject exception = jsonObject.getValueOrThrow(DevOpsCommandResponse.JsonFields.PAYLOAD).asObject();

        return of(serviceName, instance, exception, dittoHeaders);
    }

    /**
     * @return the DittoRuntimeException as JSON.
     */
    public JsonObject getDittoRuntimeException() {
        return dittoRuntimeException;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public Optional<String> getServiceName() {
        return Optional.ofNullable(serviceName);
    }

    @Override
    public Optional<Integer> getInstance() {
        return Optional.ofNullable(instance);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(DevOpsCommandResponse.JsonFields.JSON_SERVICE_NAME, serviceName, predicate);
        jsonObjectBuilder.set(DevOpsCommandResponse.JsonFields.JSON_INSTANCE, instance, predicate);
        jsonObjectBuilder.set(DevOpsCommandResponse.JsonFields.PAYLOAD,
                dittoRuntimeException, predicate);
    }

    @Override
    public DevOpsErrorResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(serviceName, instance, dittoRuntimeException, dittoHeaders);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), serviceName, instance, dittoRuntimeException);
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
        final DevOpsErrorResponse that = (DevOpsErrorResponse) o;
        return that.canEqual(this) && Objects.equals(serviceName, that.serviceName) &&
                Objects.equals(instance, that.instance) &&
                Objects.equals(dittoRuntimeException, that.dittoRuntimeException) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DevOpsErrorResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", serviceName=" + serviceName +
                ", instance=" + instance + ", dittoRuntimeException=" + dittoRuntimeException + "]";
    }

}
