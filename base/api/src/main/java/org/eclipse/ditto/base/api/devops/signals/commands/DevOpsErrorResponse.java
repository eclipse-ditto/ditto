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
package org.eclipse.ditto.base.api.devops.signals.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;

/**
 * Response to a {@link DevOpsCommand} which wraps the exception thrown while processing the command.
 */
@Immutable
@JsonParsableCommandResponse(type = DevOpsErrorResponse.TYPE)
public final class DevOpsErrorResponse extends AbstractCommandResponse<DevOpsErrorResponse>
        implements DevOpsCommandResponse<DevOpsErrorResponse>, WithEntity<DevOpsErrorResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + "errorResponse";

    @Nullable private final String serviceName;
    @Nullable private final String instance;
    private final JsonObject dittoRuntimeException;

    private DevOpsErrorResponse(@Nullable final String serviceName,
            @Nullable final String instance,
            final JsonObject dittoRuntimeException,
            final DittoHeaders dittoHeaders) {

        super(TYPE, getHttpStatus(dittoRuntimeException), dittoHeaders);
        this.serviceName = serviceName;
        this.instance = instance;
        this.dittoRuntimeException =
                checkNotNull(dittoRuntimeException, "The Ditto Runtime Exception must not be null");
    }

    private static HttpStatus getHttpStatus(final JsonObject jsonObject) {
        final Integer statusCode = jsonObject.getValueOrThrow(DittoRuntimeException.JsonFields.STATUS);
        return HttpStatus.tryGetInstance(statusCode).orElse(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Creates a new {@code DevOpsErrorResponse} for the specified {@code dittoRuntimeException}.
     *
     * @param dittoRuntimeException the exception.
     * @param dittoHeaders the headers of the command which caused the exception.
     * @return the response.
     * @throws NullPointerException if one of the arguments is {@code null}.
     */
    public static DevOpsErrorResponse of(@Nullable final String serviceName,
            @Nullable final String instance,
            final JsonObject dittoRuntimeException,
            final DittoHeaders dittoHeaders) {

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
    public static DevOpsErrorResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
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
        final String instance = jsonObject.getValue(DevOpsCommandResponse.JsonFields.JSON_INSTANCE)
                .orElse(null);
        final JsonObject exception = jsonObject.getValueOrThrow(CommandResponse.JsonFields.PAYLOAD).asObject();

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
    public Optional<String> getInstance() {
        return Optional.ofNullable(instance);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(DevOpsCommandResponse.JsonFields.JSON_SERVICE_NAME, serviceName, predicate);
        jsonObjectBuilder.set(DevOpsCommandResponse.JsonFields.JSON_INSTANCE, instance, predicate);
        jsonObjectBuilder.set(CommandResponse.JsonFields.PAYLOAD, dittoRuntimeException, predicate);
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
        return that.canEqual(this) &&
                Objects.equals(serviceName, that.serviceName) &&
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

    @Override
    public DevOpsErrorResponse setEntity(final JsonValue entity) {
        return of(serviceName, instance, entity.asObject(), getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return dittoRuntimeException;
    }

}
