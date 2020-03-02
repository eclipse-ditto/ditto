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
package org.eclipse.ditto.signals.acks;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithOptionalEntity;

/**
 * An Acknowledgement contains all information about a successful (business) {@code ACK} or a not successful
 * {@code NACK}.
 * <p>
 * Can contain built-in {@link org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel Ditto ACK labels} as well as
 * custom ones emitted by external applications.
 * </p>
 * @since 1.1.0
 */
public interface Acknowledgement extends Signal<Acknowledgement>, WithOptionalEntity {

    /**
     * Type of the Acknowledgement.
     */
    String TYPE = "acknowledgement";

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
     * @throws IllegalArgumentException if {@code entityId} is empty.
     */
    static Acknowledgement of(final AcknowledgementLabel label,
            final CharSequence entityId,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        return AcknowledgementFactory.newAcknowledgement(label, entityId, statusCode, dittoHeaders, payload);
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
     * @throws IllegalArgumentException if {@code entityId} is empty.
     */
    static Acknowledgement of(final AcknowledgementLabel label,
            final CharSequence entityId,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        return of(label, entityId, statusCode, dittoHeaders, null);
    }

    /**
     * Returns a new {@code Acknowledgement} parsed from the given JSON object.
     *
     * @param jsonObject the JSON object to be parsed.
     * @return the Acknowledgement.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected 'Acknowledgement' format.
     */
    static Acknowledgement fromJson(final JsonObject jsonObject) {
        return AcknowledgementFactory.acknowledgementFromJson(jsonObject);
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
     * @return {@code true} when this Acknowledgement is successful.
     */
    boolean isSuccess();

    /**
     * Indicates whether this Acknowledgement is a failed one.
     * Does not resolve to {@code true} when this Acknowledgement represents a {@link #isTimeout() Timeout}.
     *
     * @return {@code true} when this Acknowledgement is failed.
     */
    boolean isFailed();

    /**
     * Indicates whether this Acknowledgement represents a timeout.
     *
     * @return {@code true} when this Acknowledgement is timed out.
     */
    boolean isTimeout();

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
    default String getType() {
        return TYPE;
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    default String getResourceType() {
        return getType();
    }

    /**
     * Definition of fields of the JSON representation of an {@link Acknowledgement}.
     */
    final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        /**
         * The type of the Acknowledge label.
         */
        static final JsonFieldDefinition<String> LABEL =
                JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * The type of the Acknowledge entity ID.
         */
        static final JsonFieldDefinition<String> ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * The type of the Acknowledge status code.
         */
        static final JsonFieldDefinition<Integer> STATUS_CODE =
                JsonFactory.newIntFieldDefinition("status", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * The type of the (optional) Acknowledge payload.
         */
        static final JsonFieldDefinition<JsonValue> PAYLOAD =
                JsonFactory.newJsonValueFieldDefinition("payload", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * The type of the Acknowledge DittoHeaders.
         */
        static final JsonFieldDefinition<JsonObject> DITTO_HEADERS =
                JsonFactory.newJsonObjectFieldDefinition("dittoHeaders", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

    }

}
