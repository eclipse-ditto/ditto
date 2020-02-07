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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link Acknowledgement}.
 */
@Immutable
final class ImmutableAcknowledgement implements Acknowledgement {

    private final AcknowledgementLabel label;
    private final EntityId entityId;
    private final HttpStatusCode statusCode;
    @Nullable private final JsonValue payload;
    private final DittoHeaders dittoHeaders;

    private ImmutableAcknowledgement(final AcknowledgementLabel label,
            final EntityId entityId,
            final HttpStatusCode statusCode,
            @Nullable final JsonValue payload,
            final DittoHeaders dittoHeaders) {

        this.label = checkNotNull(label, "label");
        this.entityId = checkNotNull(entityId, "entityId");
        this.statusCode = statusCode;
        this.payload = payload;
        this.dittoHeaders = checkNotNull(dittoHeaders, "ditto headers");
    }

    /**
     * Returns a new {@code ImmutableAcknowledgement} for the specified parameters.
     *
     * @param label the label of the new Acknowledgement.
     * @param entityId the ID of the affected entity being acknowledged.
     * @param statusCode the status code (HTTP semantics) of the Acknowledgement.
     * @param payload the optional payload of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @return the ImmutableAcknowledgement.
     * @throws java.lang.NullPointerException if one of the required parameters was {@code null}.
     * @throws IllegalArgumentException if {@code entityId} is empty.
     */
    public static ImmutableAcknowledgement of(final AcknowledgementLabel label,
            final CharSequence entityId,
            final HttpStatusCode statusCode,
            @Nullable final JsonValue payload,
            final DittoHeaders dittoHeaders) {

        return new ImmutableAcknowledgement(label, DefaultEntityId.of(entityId), statusCode, payload, dittoHeaders);
    }

    /**
     * Returns a new {@code Acknowledgement} parsed from the specified {@code jsonObject}.
     *
     * @param jsonObject the JSON object.
     * @return the Acknowledgement.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected 'Acknowledgement' format.
     */
    public static ImmutableAcknowledgement fromJson(final JsonObject jsonObject) {
        final AcknowledgementLabel label = AcknowledgementLabel.of(jsonObject.getValueOrThrow(JsonFields.LABEL));
        final String extractedEntityId = jsonObject.getValueOrThrow(JsonFields.ENTITY_ID);
        final EntityId entityId = DefaultEntityId.of(extractedEntityId);
        final int extractedStatusCode = jsonObject.getValueOrThrow(JsonFields.STATUS_CODE);
        final HttpStatusCode statusCode = HttpStatusCode.forInt(extractedStatusCode)
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                        .fieldName(JsonFields.STATUS_CODE.getPointer().toString())
                        .description("Unsupported status code: " + extractedStatusCode)
                        .build()
                );
        @Nullable final JsonValue payload = jsonObject.getValue(JsonFields.PAYLOAD).orElse(null);
        final JsonObject jsonDittoHeaders = jsonObject.getValueOrThrow(JsonFields.DITTO_HEADERS);
        final DittoHeaders extractedDittoHeaders = DittoHeaders.newBuilder(jsonDittoHeaders).build();

        return of(label, entityId, statusCode, payload, extractedDittoHeaders);
    }

    @Override
    public AcknowledgementLabel getLabel() {
        return label;
    }

    @Override
    public EntityId getEntityId() {
        return entityId;
    }

    @Override
    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(payload);
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public Acknowledgement setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ImmutableAcknowledgement(label, entityId, statusCode, payload, dittoHeaders);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        jsonObjectBuilder.set(JsonFields.LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.ENTITY_ID, entityId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.STATUS_CODE, statusCode.toInt(), predicate);
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
        final ImmutableAcknowledgement that = (ImmutableAcknowledgement) o;
        return statusCode == that.statusCode &&
                label.equals(that.label) &&
                entityId.equals(that.entityId) &&
                Objects.equals(payload, that.payload) &&
                dittoHeaders.equals(that.dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, entityId, statusCode, payload, dittoHeaders);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "label=" + label +
                ", entityId=" + entityId +
                ", statusCode=" + statusCode +
                ", payload=" + payload +
                ", dittoHeaders=" + dittoHeaders +
                "]";
    }

}
