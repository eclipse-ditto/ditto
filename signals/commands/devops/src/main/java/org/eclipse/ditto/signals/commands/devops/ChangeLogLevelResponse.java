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

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to the {@link ChangeLogLevel} command.
 */
@Immutable
public final class ChangeLogLevelResponse extends AbstractDevOpsCommandResponse<ChangeLogLevelResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ChangeLogLevel.NAME;

    static final JsonFieldDefinition<Boolean> JSON_SUCCESSFUL =
            JsonFactory.newBooleanFieldDefinition("successful", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final boolean successful;

    private ChangeLogLevelResponse(@Nullable final String serviceName, @Nullable final Integer instance,
            final boolean successful, final DittoHeaders dittoHeaders) {
        super(TYPE, serviceName, instance, successful ? HttpStatusCode.OK : HttpStatusCode.INTERNAL_SERVER_ERROR,
                dittoHeaders);
        this.successful = successful;
    }

    /**
     * Creates a new ChangeLogLevelResponse instance.
     *
     * @param serviceName the service name from which the DevOpsCommandResponse originated.
     * @param instance the instance index of the serviceName from which the DevOpsCommandResponse originated.
     * @param successful indicates whether the persistence snapshot was successful.
     * @param dittoHeaders the DittoHeaders of the repsonse.
     * @return the new ChangeLogLevelResponse instance.
     */
    public static ChangeLogLevelResponse of(@Nullable final String serviceName, @Nullable final Integer instance,
            final boolean successful, final DittoHeaders dittoHeaders) {
        return new ChangeLogLevelResponse(serviceName, instance, successful, dittoHeaders);
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
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
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
        return new CommandResponseJsonDeserializer<ChangeLogLevelResponse>(TYPE, jsonObject).deserialize(
                (statusCode) -> {
                    final String serviceName = jsonObject.getValue(DevOpsCommandResponse.JsonFields.JSON_SERVICE_NAME)
                            .orElse(null);
                    final Integer instance = jsonObject.getValue(DevOpsCommandResponse.JsonFields.JSON_INSTANCE)
                            .orElse(null);
                    final boolean successful = jsonObject.getValueOrThrow(JSON_SUCCESSFUL);
                    return ChangeLogLevelResponse.of(serviceName, instance, successful, dittoHeaders);
                });
    }

    /**
     * Returns whether the persistence snapshot was successful.
     *
     * @return whether the persistence snapshot was successful.
     */
    public boolean isSuccessful() {
        return successful;
    }

    @Override
    public ChangeLogLevelResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getServiceName().orElse(null), getInstance().orElse(null), successful, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_SUCCESSFUL, successful, predicate);
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
        final ChangeLogLevelResponse that = (ChangeLogLevelResponse) o;
        return that.canEqual(this) && Objects.equals(successful, that.successful) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ChangeLogLevelResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), successful);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", successful=" + successful + "]";
    }

}
