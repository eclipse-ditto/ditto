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
package org.eclipse.ditto.base.model.signals.acks;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.entity.type.WithEntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * An Acknowledgement contains all information about a successful (business) {@code ACK} or a not successful
 * {@code NACK}.
 * <p>
 * Can contain built-in {@link org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel Ditto ACK labels} as well as
 * custom ones emitted by external applications.
 * </p>
 *
 * @since 2.0.0
 */
@JsonParsableCommandResponse(type = Acknowledgement.TYPE)
@Immutable
public final class Acknowledgement implements CommandResponse<Acknowledgement>, WithOptionalEntity, WithEntityType,
        SignalWithEntityId<Acknowledgement> {

    /**
     * The type of {@code Acknowledgement} signals.
     * @since 2.3.0
     */
    public static final String TYPE = "acknowledgement";

    private static final String TRUE_STRING = Boolean.TRUE.toString();

    private final AcknowledgementLabel label;
    private final EntityId entityId;
    private final HttpStatus httpStatus;
    @Nullable private final JsonValue payload;
    private final DittoHeaders dittoHeaders;
    private final boolean isWeak;

    private Acknowledgement(final AcknowledgementLabel label,
            final EntityId entityId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        this.label = checkNotNull(label, "label");
        this.entityId = checkNotNull(entityId, "entityId");
        this.httpStatus = checkNotNull(httpStatus, "httpStatus");
        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder =
                checkNotNull(dittoHeaders, "dittoHeaders").isResponseRequired() ? dittoHeaders
                        .toBuilder()
                        .responseRequired(false) : dittoHeaders.toBuilder();
        this.dittoHeaders = dittoHeadersBuilder
                .removeHeader(DittoHeaderDefinition.REQUESTED_ACKS.getKey())
                .build();
        this.payload = payload;
        isWeak = TRUE_STRING.equalsIgnoreCase(this.dittoHeaders.get(DittoHeaderDefinition.WEAK_ACK.getKey()));
    }

    /**
     * Returns a new {@code Acknowledgement} for the specified parameters.
     *
     * @param label the label of the new Acknowledgement.
     * @param entityId the ID of the affected entity being acknowledged.
     * @param httpStatus the HTTP status of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @param payload the optional payload of the Acknowledgement.
     * @return the ImmutableAcknowledgement.
     * @throws NullPointerException if one of the required parameters was {@code null}.
     * @since 2.0.0
     */
    public static Acknowledgement of(final AcknowledgementLabel label,
            final EntityId entityId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        return new Acknowledgement(label, entityId, httpStatus, dittoHeaders, payload);
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
    public static Acknowledgement weak(final AcknowledgementLabel label,
            final EntityId entityId,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        final DittoHeaders weakAckHeaders = dittoHeaders.toBuilder()
                .putHeader(DittoHeaderDefinition.WEAK_ACK.getKey(), Boolean.TRUE.toString())
                .build();
        return of(label, entityId, HttpStatus.OK, weakAckHeaders, payload);
    }

    /**
     * Returns a new {@code Acknowledgement} for the specified parameters.
     *
     * @param label the label of the new Acknowledgement.
     * @param entityId the ID of the affected entity being acknowledged.
     * @param httpStatus the HTTP status of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @return the ImmutableAcknowledgement.
     * @throws NullPointerException if one of the required parameters was {@code null}.
     * @since 2.0.0
     */
    public static Acknowledgement of(final AcknowledgementLabel label,
            final EntityId entityId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return of(label, entityId, httpStatus, dittoHeaders, null);
    }


    /**
     * Returns the label identifying the Acknowledgement.
     * May be a a built-in Ditto ACK label as well as custom one emitted by an external application.
     *
     * @return the label identifying the Acknowledgement.
     */
    public AcknowledgementLabel getLabel() {
        return label;
    }

    @Override
    public EntityId getEntityId() {
        return entityId;
    }

    /**
     * Indicates whether this Acknowledgement is a successful one.
     *
     * @return {@code true} if this Acknowledgement is successful.
     */
    public boolean isSuccess() {
        return httpStatus.isSuccess();
    }

    /**
     * Indicates whether this Acknowledgement is a weak acknowledgement.
     * Weak acknowledgements are issued automatically by the service, if a subscriber declared to issued an
     * acknowledgement with this  {@link #getLabel()}, but was for some reason not allowed to receive the signal.
     *
     * @return true if this is a weak acknowledgement, false otherwise.
     * @since 1.5.0
     */
    public boolean isWeak() {
        return isWeak;
    }

    /**
     * Indicates whether this Acknowledgement represents a timeout.
     *
     * @return {@code true} if this Acknowledgement is timed out.
     */
    public boolean isTimeout() {
        return HttpStatus.REQUEST_TIMEOUT.equals(httpStatus);
    }

    /**
     * Returns the HTTP status of the Acknowledgement specifying whether it was a successful {@code ACK} or a
     * {@code NACK} where the status code is something else than {@code 2xx}.
     *
     * @return the HTTP status of the Acknowledgement.
     * @since 2.0.0
     */
    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * Returns the optional payload of the Acknowledgement.
     *
     * @return the optional payload.
     */
    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(payload);
    }

    /**
     * Sets the optional payload of the Acknowledgement.
     *
     * @param payload the payload to set as entity.
     * @return the Acknowledgement with set payload.
     * @since 1.2.0
     */
    public Acknowledgement setEntity(final @Nullable JsonValue payload) {
        if (payload != null) {
            return of(label, entityId, httpStatus, dittoHeaders, payload);
        }
        return of(label, entityId, httpStatus, dittoHeaders, null);
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public Acknowledgement setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(label, entityId, httpStatus, dittoHeaders, payload);
    }

    @Override
    public EntityType getEntityType() {
        return entityId.getEntityType();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * Parses the given JSON object to an {@code Acknowledgement}.
     *
     * @param jsonObject the JSON object to be parsed.
     * @param dittoHeaders will be ignored.
     * @return the Acknowledgement.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} misses a required field.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contained an unexpected value.
     */
    public static Acknowledgement fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new AcknowledgementJsonParser().apply(jsonObject);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(CommandResponse.JsonFields.TYPE, getType(), predicate);
        jsonObjectBuilder.set(JsonFields.LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.ENTITY_ID, entityId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.ENTITY_TYPE, getEntityType().toString(), predicate);
        jsonObjectBuilder.set(JsonFields.STATUS_CODE, httpStatus.getCode(), predicate);
        if (null != payload) {
            jsonObjectBuilder.set(JsonFields.PAYLOAD, payload, predicate);
        }
        jsonObjectBuilder.set(JsonFields.DITTO_HEADERS, dittoHeaders.toJson(), predicate);

        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Acknowledgement that = (Acknowledgement) o;
        return httpStatus.equals(that.httpStatus) &&
                label.equals(that.label) &&
                entityId.equals(that.entityId) &&
                Objects.equals(payload, that.payload) &&
                dittoHeaders.equals(that.dittoHeaders) &&
                isWeak == that.isWeak;
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, entityId, httpStatus, payload, dittoHeaders, isWeak);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "label=" + label +
                ", entityId=" + entityId +
                ", httpStatus=" + httpStatus +
                ", payload=" + payload +
                ", dittoHeaders=" + dittoHeaders +
                ", isWeak=" + isWeak +
                "]";
    }

    @Override
    public ResponseType getResponseType() {
        if (isSuccess()) {
            return ResponseType.RESPONSE;
        } else {
            return ResponseType.NACK;
        }
    }

    /**
     * Returns all non-hidden marked fields of this Acknowledgement.
     *
     * @return a JSON object representation of this Acknowledgement including only non-hidden marked fields.
     */
    @Override
    public JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    public String getManifest() {
        return getType();
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return getType();
    }

    /**
     * Definition of fields of the JSON representation of an {@link Acknowledgement}.
     */
    public static final class JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }

        /**
         * Definition of the JSON field for the Acknowledgement's label.
         */
        public static final JsonFieldDefinition<String> LABEL =
                JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgement's entity ID.
         */
        public static final JsonFieldDefinition<String> ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgement's entity type.
         */
        public static final JsonFieldDefinition<String> ENTITY_TYPE =
                JsonFactory.newStringFieldDefinition("entityType", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgement's status code.
         */
        public static final JsonFieldDefinition<Integer> STATUS_CODE =
                JsonFactory.newIntFieldDefinition("status", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the optional Acknowledgement's payload.
         */
        public static final JsonFieldDefinition<JsonValue> PAYLOAD =
                JsonFactory.newJsonValueFieldDefinition("payload", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * Definition of the JSON field for the Acknowledgement's DittoHeaders.
         */
        public static final JsonFieldDefinition<JsonObject> DITTO_HEADERS =
                JsonFactory.newJsonObjectFieldDefinition("headers", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

    }

}
