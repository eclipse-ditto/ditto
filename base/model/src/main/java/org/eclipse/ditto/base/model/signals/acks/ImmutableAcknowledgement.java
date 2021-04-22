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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Default implementation of {@link Acknowledgement}.
 * This class lacks a static {@code fromJson} factory method as JSON parsing is specific for different entity types.
 *
 * @param <T> the type of the entity ID this class knows.
 * @since 1.1.0
 */
@Immutable
final class ImmutableAcknowledgement<T extends EntityId> implements Acknowledgement {

    private static final String TRUE_STRING = Boolean.TRUE.toString();

    private final AcknowledgementLabel label;
    private final T entityId;
    private final HttpStatus httpStatus;
    @Nullable private final JsonValue payload;
    private final DittoHeaders dittoHeaders;
    private final boolean isWeak;

    private ImmutableAcknowledgement(final AcknowledgementLabel label,
            final T entityId,
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
     * Constructs a new ImmutableAcknowledgement object.
     *
     * @param label the label of the new Acknowledgement.
     * @param entityId the ID of the affected entity being acknowledged.
     * @param httpStatus the HTTP status.
     * @param dittoHeaders the DittoHeaders.
     * @param payload the optional payload.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <T extends EntityId> ImmutableAcknowledgement<T> of(final AcknowledgementLabel label,
            final T entityId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        return new ImmutableAcknowledgement<>(label, entityId, httpStatus, dittoHeaders, payload);
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
        return httpStatus.isSuccess();
    }

    @Override
    public boolean isWeak() {
        return isWeak;
    }

    @Override
    public boolean isTimeout() {
        return HttpStatus.REQUEST_TIMEOUT.equals(httpStatus);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(payload);
    }

    @Override
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
    public ImmutableAcknowledgement<T> setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(label, entityId, httpStatus, dittoHeaders, payload);
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
        final ImmutableAcknowledgement<?> that = (ImmutableAcknowledgement<?>) o;
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

}
