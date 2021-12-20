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

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Response to the {@link ChangeLogLevel} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ChangeLogLevelResponse.TYPE)
public final class ChangeLogLevelResponse extends AbstractDevOpsCommandResponse<ChangeLogLevelResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ChangeLogLevel.NAME;

    static final JsonFieldDefinition<Boolean> JSON_SUCCESSFUL =
            JsonFieldDefinition.ofBoolean("successful", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final CommandResponseJsonDeserializer<ChangeLogLevelResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final var jsonObject = context.getJsonObject();
                        return new ChangeLogLevelResponse(
                                jsonObject.getValue(DevOpsCommandResponse.JsonFields.JSON_SERVICE_NAME).orElse(null),
                                jsonObject.getValue(DevOpsCommandResponse.JsonFields.JSON_INSTANCE).orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private ChangeLogLevelResponse(@Nullable final String serviceName,
            @Nullable final String instance,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                serviceName,
                instance,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Arrays.asList(HttpStatus.OK, HttpStatus.INTERNAL_SERVER_ERROR),
                        ChangeLogLevelResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a new ChangeLogLevelResponse instance.
     *
     * @param serviceName the service name from which the DevOpsCommandResponse originated.
     * @param instance the instance identifier of the serviceName from which the DevOpsCommandResponse originated.
     * @param successful indicates whether the persistence snapshot was successful.
     * @param dittoHeaders the DittoHeaders of the response.
     * @return the new ChangeLogLevelResponse instance.
     */
    public static ChangeLogLevelResponse of(@Nullable final String serviceName,
            @Nullable final String instance,
            final boolean successful,
            final DittoHeaders dittoHeaders) {

        return new ChangeLogLevelResponse(serviceName,
                instance,
                successful ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ChangeLogLevelResponse} command from a JSON string.
     *
     * @param jsonString contains the data of the ChangeLogLevelResponse command.
     * @param dittoHeaders the headers of the request.
     * @return the ChangeLogLevelResponse command which is based on the dta of {@code jsonString}.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ChangeLogLevelResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ChangeLogLevelResponse} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ChangeLogLevelResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * Returns whether the persistence snapshot was successful.
     *
     * @return whether the persistence snapshot was successful.
     */
    public boolean isSuccessful() {
        final var httpStatus = getHttpStatus();
        return httpStatus.isSuccess();
    }

    @Override
    public ChangeLogLevelResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ChangeLogLevelResponse(getServiceName().orElse(null),
                getInstance().orElse(null),
                getHttpStatus(),
                dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        final var predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_SUCCESSFUL, isSuccessful(), predicate);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (ChangeLogLevelResponse) o;
        return that.canEqual(this) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ChangeLogLevelResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", successful=" + isSuccessful() + "]";
    }

}
