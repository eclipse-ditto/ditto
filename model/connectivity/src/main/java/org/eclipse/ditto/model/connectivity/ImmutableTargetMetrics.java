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
 * Immutable implementation of {@link TargetMetrics}.
 */
@Immutable
final class ImmutableTargetMetrics implements TargetMetrics {

    private final String address;
    private final Set<String> topics;
    private final ConnectionStatus status;
    @Nullable private final String statusDetails;
    private final long publishedMessages;


    private ImmutableTargetMetrics(final String address, final Set<String> topics,
            final ConnectionStatus status, @Nullable final String statusDetails, final long publishedMessages) {
        this.address = address;
        this.topics = Collections.unmodifiableSet(new HashSet<>(topics));
        this.status = status;
        this.statusDetails = statusDetails;
        this.publishedMessages = publishedMessages;
    }

    /**
     * TODO Doc
     */
    public static ImmutableTargetMetrics of(final String address, final Set<String> topics,
            final ConnectionStatus status, @Nullable final String statusDetails, final long consumedMessages) {
        return new ImmutableTargetMetrics(address, topics, status, statusDetails, consumedMessages);
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public Set<String> getTopics() {
        return topics;
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
    public long getPublishedMessages() {
        return publishedMessages;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate);
        jsonObjectBuilder.set(JsonFields.ADDRESS, address, predicate);
        jsonObjectBuilder.set(JsonFields.TOPICS, topics.stream()
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));

        jsonObjectBuilder.set(JsonFields.STATUS, status.name(), predicate);
        if (statusDetails != null) {
            jsonObjectBuilder.set(JsonFields.STATUS_DETAILS, statusDetails, predicate);
        }
        jsonObjectBuilder.set(JsonFields.PUBLISHED_MESSAGES, publishedMessages, predicate);
        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code TargetMetrics} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the TargetMetrics to be created.
     * @return a new TargetMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static TargetMetrics fromJson(final JsonObject jsonObject) {
        final String readAddress = jsonObject.getValueOrThrow(Target.JsonFields.ADDRESS);
        final Set<String> readTopics = jsonObject.getValue(Target.JsonFields.TOPICS)
                .map(array -> array.stream()
                        .map(JsonValue::asString)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
        final ConnectionStatus readConnectionStatus = ConnectionStatus.forName(
                jsonObject.getValueOrThrow(JsonFields.STATUS)).orElse(ConnectionStatus.UNKNOWN);
        final String readConnectionStatusDetails = jsonObject.getValue(JsonFields.STATUS_DETAILS)
                .orElse(null);
        final long readConsumedMessages = jsonObject.getValueOrThrow(JsonFields.PUBLISHED_MESSAGES);
        return ImmutableTargetMetrics.of(readAddress, readTopics, readConnectionStatus,
                readConnectionStatusDetails, readConsumedMessages);
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {return true;}
        if (!(o instanceof ImmutableTargetMetrics)) {return false;}
        final ImmutableTargetMetrics that = (ImmutableTargetMetrics) o;
        return publishedMessages == that.publishedMessages &&
                Objects.equals(address, that.address) &&
                Objects.equals(topics, that.topics) &&
                status == that.status &&
                Objects.equals(statusDetails, that.statusDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, topics, status, statusDetails, publishedMessages);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "target=" + address +
                ", topics=" + topics +
                ", status=" + status +
                ", statusDetails=" + statusDetails +
                ", publishedMessages=" + publishedMessages +
                "]";
    }
}
