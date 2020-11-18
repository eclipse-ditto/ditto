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
package org.eclipse.ditto.signals.acks.base;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.common.ResponseType;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.base.entity.type.WithEntityType;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * An Acknowledgement contains all information about a successful (business) {@code ACK} or a not successful
 * {@code NACK}.
 * <p>
 * Can contain built-in {@link org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel Ditto ACK labels} as well as
 * custom ones emitted by external applications.
 * </p>
 *
 * @since 1.1.0
 */
public interface Acknowledgement extends CommandResponse<Acknowledgement>, WithOptionalEntity, WithEntityType {

    /**
     * Returns the type of an Acknowledgement for the context of the given entity type.
     *
     * @param entityType the type of the entity the Acknowledgement is meant for.
     * @return the type of the Acknowledgement.
     * @throws NullPointerException if {@code entityType} is {@code null}.
     */
    static String getType(final EntityType entityType) {
        return "acknowledgement." + checkNotNull(entityType, "entityType");
    }

    /**
     * Returns a new {@code Acknowledgement} for the specified parameters.
     *
     * @param label the label of the new Acknowledgement.
     * @param entityId the ID of the affected entity being acknowledged.
     * @param statusCode the status code (HTTP semantics) of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @param payload the optional payload of the Acknowledgement.
     * @return the ImmutableAcknowledgement.
     * @throws NullPointerException if one of the required parameters was {@code null}.
     */
    static Acknowledgement of(final AcknowledgementLabel label,
            final EntityIdWithType entityId,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        return AcknowledgementFactory.newAcknowledgement(label, entityId, statusCode, dittoHeaders, payload);
    }

    /**
     * Returns a new weak {@code Acknowledgement} for the specified parameters.
     *
     * @param label the label of the new Acknowledgement.
     * @param entityId the ID of the affected entity being acknowledged.
     * @param dittoHeaders the DittoHeaders.
     * @param payload the optional payload of the Acknowledgement.
     * @return the Acknowledgement.
     * @throws NullPointerException if one of the required parameters was {@code null}.
     * @since 1.5.0
     */
    static Acknowledgement weak(final AcknowledgementLabel label,
            final EntityIdWithType entityId,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        final DittoHeaders weakAckHeaders = dittoHeaders.toBuilder()
                .putHeader(DittoHeaderDefinition.WEAK_ACK.getKey(), Boolean.TRUE.toString())
                .build();
        return AcknowledgementFactory.newAcknowledgement(label, entityId, HttpStatusCode.OK, weakAckHeaders, payload);
    }

    /**
     * Returns a new {@code Acknowledgement} for the specified parameters.
     *
     * @param label the label of the new Acknowledgement.
     * @param entityId the ID of the affected entity being acknowledged.
     * @param statusCode the status code (HTTP semantics) of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @return the ImmutableAcknowledgement.
     * @throws NullPointerException if one of the required parameters was {@code null}.
     */
    static Acknowledgement of(final AcknowledgementLabel label,
            final EntityIdWithType entityId,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        return of(label, entityId, statusCode, dittoHeaders, null);
    }

    /**
     * Returns the label identifying the Acknowledgement.
     * May be a a built-in Ditto ACK label as well as custom one emitted by an external application.
     *
     * @return the label identifying the Acknowledgement.
     */
    AcknowledgementLabel getLabel();

    /**
     * Indicates whether this Acknowledgement is a successful one.
     *
     * @return {@code true} if this Acknowledgement is successful.
     */
    boolean isSuccess();

    /**
     * Indicates whether this Acknowledgement is a weak acknowledgement.
     * Weak acknowledgements are issued automatically by the service, if a subscriber declared to issued an
     * acknowledgement with this  {@link #getLabel()}, but was for some reason not allowed to receive the signal.
     *
     * @return true if this is a weak acknowledgement, false otherwise.
     * @since 1.5.0
     */
    boolean isWeak();

    /**
     * Indicates whether this Acknowledgement represents a timeout.
     *
     * @return {@code true} if this Acknowledgement is timed out.
     */
    boolean isTimeout();

    @Override
    default ResponseType getResponseType() {
        if (isSuccess()) {
            return ResponseType.RESPONSE;
        } else {
            return ResponseType.NACK;
        }
    }

    /**
     * Returns the status code of the Acknowledgement specifying whether it was a successful {@code ACK} or a
     * {@code NACK} where the status code is something else than {@code 2xx}.
     *
     * @return the status code of the Acknowledgement.
     */
    HttpStatusCode getStatusCode();

    /**
     * Returns the optional payload of the Acknowledgement.
     *
     * @return the optional payload.
     */
    @Override
    Optional<JsonValue> getEntity(JsonSchemaVersion schemaVersion);

    /**
     * Sets the optional payload of the Acknowledgement.
     *
     * @return the Acknowledgement with set payload.
     * @since 1.2.0
     */
    Acknowledgement setEntity(@Nullable JsonValue payload);

    /**
     * Returns all non hidden marked fields of this Acknowledgement.
     *
     * @return a JSON object representation of this Acknowledgement including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default String getManifest() {
        return getType();
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    default String getResourceType() {
        return getType();
    }

    @Override
    EntityIdWithType getEntityId();

    /**
     * Definition of fields of the JSON representation of an {@link Acknowledgement}.
     */
    final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        /**
         * Definition of the JSON field for the Acknowledgement's label.
         */
        public static final JsonFieldDefinition<String> LABEL =
                JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgement's entity ID.
         */
        public static final JsonFieldDefinition<String> ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgement's entity type.
         */
        public static final JsonFieldDefinition<String> ENTITY_TYPE =
                JsonFactory.newStringFieldDefinition("entityType", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgement's status code.
         */
        public static final JsonFieldDefinition<Integer> STATUS_CODE =
                JsonFactory.newIntFieldDefinition("status", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the optional Acknowledgement's payload.
         */
        public static final JsonFieldDefinition<JsonValue> PAYLOAD =
                JsonFactory.newJsonValueFieldDefinition("payload", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgement's DittoHeaders.
         */
        public static final JsonFieldDefinition<JsonObject> DITTO_HEADERS =
                JsonFactory.newJsonObjectFieldDefinition("headers", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

    }

}
