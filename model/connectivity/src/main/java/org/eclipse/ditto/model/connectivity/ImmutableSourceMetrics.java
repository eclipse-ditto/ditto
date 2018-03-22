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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link SourceMetrics}.
 */
@Immutable
final class ImmutableSourceMetrics implements SourceMetrics {

    private final Set<String> addresses;
    private final int consumerCount;
    private final ConnectionStatus status;
    @Nullable private final String statusDetails;
    private final long consumedMessages;

    private ImmutableSourceMetrics(final Set<String> addresses, final int consumerCount,
            final ConnectionStatus status, @Nullable final String statusDetails, final long consumedMessages) {
        this.addresses = Collections.unmodifiableSet(new HashSet<>(addresses));
        this.consumerCount = consumerCount;
        this.status = status;
        this.statusDetails = statusDetails;
        this.consumedMessages = consumedMessages;
    }

    /**
     * TODO Doc
     */
    public static ImmutableSourceMetrics of(final Set<String> addresses, final int consumerCount,
            final ConnectionStatus status, @Nullable final String statusDetails, final long consumedMessages) {
        return new ImmutableSourceMetrics(addresses, consumerCount, status, statusDetails, consumedMessages);
    }

    /**
     * TODO doc
     */
    public static ImmutableSourceMetrics of(final int consumerCount,
            final ConnectionStatus status, @Nullable final String statusDetails, final long consumedMessages,
            final String... addresses) {
        return new ImmutableSourceMetrics(new HashSet<>(Arrays.asList(addresses)), consumerCount, status, statusDetails,
                consumedMessages);
    }

    @Override
    public Set<String> getAddresses() {
        return addresses;
    }

    @Override
    public int getConsumerCount() {
        return consumerCount;
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
    public long getConsumedMessages() {
        return consumedMessages;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate);
        jsonObjectBuilder.set(JsonFields.ADDRESSES, addresses.stream()
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));

        jsonObjectBuilder.set(JsonFields.CONSUMER_COUNT, consumerCount, predicate);
        jsonObjectBuilder.set(JsonFields.STATUS, status.name(), predicate);
        if (statusDetails != null) {
            jsonObjectBuilder.set(JsonFields.STATUS_DETAILS, statusDetails, predicate);
        }
        jsonObjectBuilder.set(JsonFields.CONSUMED_MESSAGES, consumedMessages, predicate);
        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code SourceMetrics} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the SourceMetrics to be created.
     * @return a new SourceMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static SourceMetrics fromJson(final JsonObject jsonObject) {
        final Set<String> readSources = jsonObject.getValue(JsonFields.ADDRESSES)
                .map(array -> array.stream()
                        .map(JsonValue::asString)
                        .collect(Collectors.toSet())).orElse(Collections.emptySet());
        final int readConsumerCount = jsonObject.getValueOrThrow(JsonFields.CONSUMER_COUNT);
        final ConnectionStatus readConnectionStatus = ConnectionStatus.forName(
                jsonObject.getValueOrThrow(JsonFields.STATUS)).orElse(ConnectionStatus.UNKNOWN);
        final String readConnectionStatusDetails = jsonObject.getValue(JsonFields.STATUS_DETAILS)
                .orElse(null);
        final long readConsumedMessages = jsonObject.getValueOrThrow(JsonFields.CONSUMED_MESSAGES);
        return ImmutableSourceMetrics.of(readSources, readConsumerCount, readConnectionStatus,
                readConnectionStatusDetails, readConsumedMessages);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {return true;}
        if (!(o instanceof ImmutableSourceMetrics)) {return false;}
        final ImmutableSourceMetrics that = (ImmutableSourceMetrics) o;
        return consumerCount == that.consumerCount &&
                consumedMessages == that.consumedMessages &&
                Objects.equals(addresses, that.addresses) &&
                status == that.status &&
                Objects.equals(statusDetails, that.statusDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addresses, consumerCount, status, statusDetails, consumedMessages);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "addresses=" + addresses +
                ", consumerCount=" + consumerCount +
                ", status=" + status +
                ", statusDetails=" + statusDetails +
                ", consumedMessages=" + consumedMessages +
                "]";
    }
}
