/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.gateway.service.endpoints.routes.wot;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.api.common.CommonCommandResponse;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.wot.model.ThingDescription;

/**
 * Response on a {@link RetrieveWotDiscoveryThingDirectory} command.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveWotDiscoveryThingDirectoryResponse.TYPE)
public final class RetrieveWotDiscoveryThingDirectoryResponse
        extends CommonCommandResponse<RetrieveWotDiscoveryThingDirectoryResponse>
        implements WithEntity<RetrieveWotDiscoveryThingDirectoryResponse> {

    /**
     * The type of the response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveWotDiscoveryThingDirectory.NAME;

    private static final JsonFieldDefinition<JsonObject> JSON_THING_DESCRIPTION =
            JsonFieldDefinition.ofJsonObject("thingDescription", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveWotDiscoveryThingDirectoryResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final var jsonObject = context.getJsonObject();
                        return new RetrieveWotDiscoveryThingDirectoryResponse(
                                ThingDescription.fromJson(jsonObject.getValueOrThrow(JSON_THING_DESCRIPTION)),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders());
                    });

    private final ThingDescription thingDescription;

    private RetrieveWotDiscoveryThingDirectoryResponse(final ThingDescription thingDescription,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveWotDiscoveryThingDirectoryResponse.class),
                dittoHeaders);
        this.thingDescription = checkNotNull(thingDescription, "thingDescription");
    }

    /**
     * Build a new {@code RetrieveWotDiscoveryThingDirectoryResponse}.
     *
     * @param thingDescription the thing description to respond.
     * @param dittoHeaders the headers for the response.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveWotDiscoveryThingDirectoryResponse of(final ThingDescription thingDescription,
            final DittoHeaders dittoHeaders) {
        checkNotNull(thingDescription, "thingDescription");
        return new RetrieveWotDiscoveryThingDirectoryResponse(thingDescription, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a RetrieveWotDiscoveryThingDirectoryResponse from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveWotDiscoveryThingDirectoryResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_THING_DESCRIPTION, thingDescription.toJson(), predicate);
    }

    @Override
    public RetrieveWotDiscoveryThingDirectoryResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new RetrieveWotDiscoveryThingDirectoryResponse(thingDescription, getHttpStatus(), dittoHeaders);
    }

    @Override
    public RetrieveWotDiscoveryThingDirectoryResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return new RetrieveWotDiscoveryThingDirectoryResponse(ThingDescription.fromJson(entity.asObject()),
                getHttpStatus(), getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return thingDescription.toJson();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (super.equals(o) && o instanceof RetrieveWotDiscoveryThingDirectoryResponse that) {
            return Objects.equals(thingDescription, that.thingDescription);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingDescription);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                "thingDescription=" + thingDescription +
                "]";
    }

}
