/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.gateway.service.endpoints.routes.whoami;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.api.common.CommonCommandResponse;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
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

/**
 * Response on a {@link Whoami} command.
 *
 * @since 1.2.0
 */
@Immutable
@JsonParsableCommandResponse(type = WhoamiResponse.TYPE)
public final class WhoamiResponse extends CommonCommandResponse<WhoamiResponse> implements WithEntity<WhoamiResponse> {

    /**
     * The type of the response.
     */
    public static final String TYPE = TYPE_PREFIX + Whoami.NAME;

    private static final JsonFieldDefinition<JsonObject> JSON_USER_INFO =
            JsonFieldDefinition.ofJsonObject("userInformation");

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<WhoamiResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final var jsonObject = context.getJsonObject();
                        return new WhoamiResponse(jsonObject.getValueOrThrow(JSON_USER_INFO),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders());
                    });

    // intentionally a JsonObject to be able to use different implementations of UserInformation
    private final JsonObject userInformation;

    private WhoamiResponse(final JsonObject userInformation,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        WhoamiResponse.class),
                dittoHeaders);
        this.userInformation = checkNotNull(userInformation, "userInformation");
    }

    /**
     * Build a new {@code WhoamiResponse}.
     *
     * @param userInformation the user information to respond.
     * @param dittoHeaders the headers for the reponse.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static WhoamiResponse of(final UserInformation userInformation, final DittoHeaders dittoHeaders) {
        checkNotNull(userInformation, "userInformation");
        return new WhoamiResponse(userInformation.toJson(), HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a WhoamiResponse from a JSON object.
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
    public static WhoamiResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(JSON_USER_INFO, userInformation);
    }

    @Override
    public WhoamiResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new WhoamiResponse(userInformation, getHttpStatus(), dittoHeaders);
    }

    @Override
    public WhoamiResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return new WhoamiResponse(entity.asObject(), getHttpStatus(), getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return userInformation;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (super.equals(o) && o instanceof WhoamiResponse) {
            final WhoamiResponse that = (WhoamiResponse) o;
            return Objects.equals(userInformation, that.userInformation);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userInformation);
    }

    @Override
    @Nonnull
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                "userInformation=" + userInformation +
                "]";
    }

    @Override
    @Nonnull
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

}
