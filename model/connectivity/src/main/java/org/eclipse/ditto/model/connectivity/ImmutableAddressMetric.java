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
package org.eclipse.ditto.model.connectivity;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link AddressMetric}.
 */
@Immutable
final class ImmutableAddressMetric implements AddressMetric {

    private final ConnectionStatus status;
    @Nullable private final String statusDetails;
    private final long messageCount;
    @Nullable private final Instant lastMessageAt;

    private ImmutableAddressMetric(final ConnectionStatus status, @Nullable final String statusDetails,
            final long messageCount, @Nullable final Instant lastMessageAt) {
        this.status = status;
        this.statusDetails = statusDetails;
        this.messageCount = messageCount;
        this.lastMessageAt = lastMessageAt;
    }

    /**
     * Creates a new {@code ImmutableAddressMetric} instance.
     *
     * @param status the current status of the connection
     * @param statusDetails the optional status details
     * @param consumedMessages the current message count
     * @param lastMessageAt the timestamp when the last message was consumed/published
     * @return a new instance of ImmutableAddressMetric
     */
    public static ImmutableAddressMetric of(final ConnectionStatus status, @Nullable final String statusDetails,
            final long consumedMessages, @Nullable final Instant lastMessageAt) {
        return new ImmutableAddressMetric(status, statusDetails, consumedMessages, lastMessageAt);
    }

    @Override
    public ConnectionStatus getStatus() {
        return status;
    }

    @Override
    public Optional<String> getStatusDetails() {
        return Optional.ofNullable(statusDetails);
    }

    @Override
    public long getMessageCount() {
        return messageCount;
    }

    @Override
    public Optional<Instant> getLastMessageAt() {
        return Optional.ofNullable(lastMessageAt);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate);
        jsonObjectBuilder.set(JsonFields.STATUS, status.getName(), predicate);
        if (statusDetails != null) {
            jsonObjectBuilder.set(JsonFields.STATUS_DETAILS, statusDetails, predicate);
        }
        jsonObjectBuilder.set(JsonFields.MESSAGE_COUNT, messageCount, predicate);
        if (lastMessageAt != null) {
            jsonObjectBuilder.set(JsonFields.LAST_MESSAGE_AT, lastMessageAt.toString(), predicate);
        }
        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code AddressMetric} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the AddressMetric to be created.
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static AddressMetric fromJson(final JsonObject jsonObject) {
        final ConnectionStatus readConnectionStatus = ConnectionStatus.forName(
                jsonObject.getValueOrThrow(JsonFields.STATUS)).orElse(ConnectionStatus.UNKNOWN);
        final String readConnectionStatusDetails = jsonObject.getValue(JsonFields.STATUS_DETAILS)
                .orElse(null);
        final long readConsumedMessages = jsonObject.getValueOrThrow(JsonFields.MESSAGE_COUNT);
        final Instant readLastMessageAt = jsonObject.getValue(JsonFields.LAST_MESSAGE_AT).map(Instant::parse)
                .orElse(null);
        return ImmutableAddressMetric.of(readConnectionStatus, readConnectionStatusDetails, readConsumedMessages,
                readLastMessageAt);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {return true;}
        if (!(o instanceof ImmutableAddressMetric)) {return false;}
        final ImmutableAddressMetric that = (ImmutableAddressMetric) o;
        return status == that.status &&
                Objects.equals(statusDetails, that.statusDetails) &&
                messageCount == that.messageCount &&
                Objects.equals(lastMessageAt, that.lastMessageAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, statusDetails, messageCount, lastMessageAt);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "status=" + status +
                ", statusDetails=" + statusDetails +
                ", messageCount=" + messageCount +
                ", lastMessageAt=" + lastMessageAt +
                "]";
    }
}
