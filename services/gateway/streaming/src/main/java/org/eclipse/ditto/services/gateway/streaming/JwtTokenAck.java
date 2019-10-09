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
package org.eclipse.ditto.services.gateway.streaming;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;

/**
 * Send for acknowledging that a JWT was received by the backend.
 */
@JsonParsableCommandResponse(type = JwtTokenAck.TYPE)
public final class JwtTokenAck implements Jsonifiable.WithPredicate<JsonObject, JsonField> {

    static final String TYPE = "gateway:jwt.token.ack";

    private static final JsonFieldDefinition<Boolean> JSON_VALID_JWT =
            JsonFactory.newBooleanFieldDefinition("validJwt", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<String> JSON_REASON_FOR_INVALIDITY =
            JsonFactory.newStringFieldDefinition("reasonForInvalidity", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final boolean validJwt;
    @Nullable private final String reasonForInvalidity;

    /**
     * Constructs a new acknowledge for when a JWT was received.
     */
    public JwtTokenAck(final boolean validJwt, @Nullable final String reasonForInvalidity) {
        this.validJwt = validJwt;
        this.reasonForInvalidity = reasonForInvalidity;
    }

    /**
     * @return the reason why the JWT is invalid if ({@link #validJwt} is {@code false}) otherwise {@code null}.
     */
    @Nullable
    public String getReasonForInvalidity() { return reasonForInvalidity; }

    /**
     * @return whether the JWT was valid ({@link #validJwt} is {@code true}) or invalid ({@link #validJwt}
     * is {@code false}).
     */
    public boolean isValidJwt() {
        return validJwt;
    }

    /**
     * Creates a new {@code JwtTokenAck} message from a JSON object.
     *
     * @param jsonObject the JSON object of which the JwtTokenAck is to be created.
     * @param dittoHeaders will be ignored
     * @return the JwtTokenAck message.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static JwtTokenAck fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final boolean validJwt = jsonObject.getValueOrThrow(JSON_VALID_JWT);
        final String reasonForInvalidity = jsonObject.getValueOrThrow(JSON_REASON_FOR_INVALIDITY);

        return new JwtTokenAck(validJwt, reasonForInvalidity);
    }

    @Override
    @Nonnull
    public JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    @Nonnull
    public JsonObject toJson(@Nonnull final JsonSchemaVersion schemaVersion,
            @Nonnull final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        final JsonObjectBuilder jsonObjectBuilder = JsonObject.newBuilder();
        jsonObjectBuilder.set(JSON_VALID_JWT, validJwt, predicate);
        jsonObjectBuilder.set(JSON_REASON_FOR_INVALIDITY, reasonForInvalidity, predicate);

        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JwtTokenAck that = (JwtTokenAck) o;
        return validJwt == that.validJwt &&
                Objects.equals(reasonForInvalidity, that.reasonForInvalidity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reasonForInvalidity, validJwt);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "reasonForInvalidity=" + reasonForInvalidity +
                ", validJwt=" + validJwt +
                "]";
    }
}
