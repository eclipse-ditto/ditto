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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Default implementation of {@link Acknowledgement}.
 * This class lacks a static {@code fromJson} factory method as JSON parsing is specific for different entity types.
 *
 * @param <T> the type of the entity ID this class knows.
 * @since 1.1.0
 */
@Immutable
final class ImmutableAcknowledgement<T extends EntityIdWithType> implements Acknowledgement {

    private final AcknowledgementLabel label;
    private final T entityId;
    private final HttpStatusCode statusCode;
    @Nullable private final JsonValue payload;
    private final DittoHeaders dittoHeaders;

    private ImmutableAcknowledgement(final AcknowledgementLabel label,
            final T entityId,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        this.label = checkNotNull(label, "label");
        this.entityId = checkNotNull(entityId, "entityId");
        this.statusCode = checkNotNull(statusCode, "statusCode");
        this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders").isResponseRequired() ? dittoHeaders
                .toBuilder()
                .responseRequired(false)
                .build() : dittoHeaders;
        this.payload = payload;
    }

    /**
     * Constructs a new ImmutableAcknowledgement object.
     *
     * @param label the label of the new Acknowledgement.
     * @param entityId the ID of the affected entity being acknowledged.
     * @param statusCode the status code (HTTP semantics).
     * @param dittoHeaders the DittoHeaders.
     * @param payload the optional payload.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <T extends EntityIdWithType> ImmutableAcknowledgement<T> of(final AcknowledgementLabel label,
            final T entityId,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        return new ImmutableAcknowledgement<>(label, entityId, statusCode, dittoHeaders, payload);
    }

    @Override
    public AcknowledgementLabel getLabel() {
        return label;
    }

    @Override
    public T getEntityId() {
        return entityId;
    }

    @Override
    public boolean isSuccess() {
        if (DittoAcknowledgementLabel.LIVE_RESPONSE.equals(label)) {
            /*
             * Consider live responses only as failed acknowledgement when the response timed out.
             * Otherwise it would not be possible to respond with an error status code to live messages.
             */
            return !isTimeout();
        }
        return statusCode.isSuccess();
    }

    @Override
    public boolean isTimeout() {
        return HttpStatusCode.REQUEST_TIMEOUT == statusCode;
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
    public Acknowledgement setEntity(final @Nullable JsonValue payload) {
        if (payload != null) {
            return of(label, entityId, statusCode, dittoHeaders, payload);
        }
        return of(label, entityId, statusCode, dittoHeaders, null);
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public ImmutableAcknowledgement<T> setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(label, entityId, statusCode, dittoHeaders, payload);
    }

    @Override
    public EntityType getEntityType() {
        return entityId.getEntityType();
    }

    @Override
    public String getType() {
        return Acknowledgement.getType(getEntityType());
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(JsonFields.LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.ENTITY_ID, entityId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.ENTITY_TYPE, getEntityType().toString(), predicate);
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
        final ImmutableAcknowledgement<?> that = (ImmutableAcknowledgement<?>) o;
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
