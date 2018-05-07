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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link SourceMetrics}.
 */
@Immutable
final class ImmutableSourceMetrics implements SourceMetrics {

    private final Map<String, AddressMetric> addressMetrics;
    private final long consumedMessages;

    private ImmutableSourceMetrics(final Map<String, AddressMetric> addressMetrics, final long consumedMessages) {
        this.addressMetrics = Collections.unmodifiableMap(new HashMap<>(addressMetrics));
        this.consumedMessages = consumedMessages;
    }

    /**
     * Creates a new {@code ImmutableSourceMetrics} instance.
     *
     * @param addressMetrics the AddressMetrics for each source
     * @param consumedMessages the total count of consumed messages on this source
     * @return a new instance of ImmutableSourceMetrics
     */
    public static ImmutableSourceMetrics of(final Map<String, AddressMetric> addressMetrics,
            final long consumedMessages) {
        return new ImmutableSourceMetrics(addressMetrics, consumedMessages);
    }

    @Override
    public Map<String, AddressMetric> getAddressMetrics() {
        return addressMetrics;
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
        jsonObjectBuilder.set(JsonFields.ADDRESS_METRICS, addressMetrics.entrySet().stream()
                .map(e -> JsonField.newInstance(e.getKey(), e.getValue().toJson()))
                .collect(JsonCollectors.fieldsToObject()), predicate);
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
        final Map<String, AddressMetric> readAddressMetrics = jsonObject.getValue(JsonFields.ADDRESS_METRICS)
                .map(obj -> obj.stream()
                        .collect(Collectors.toMap(
                                f -> f.getKey().toString(),
                                f -> ConnectivityModelFactory.addressMetricFromJson(f.getValue().asObject()))))
                .orElse(Collections.emptyMap());
        final long readConsumedMessages = jsonObject.getValueOrThrow(JsonFields.CONSUMED_MESSAGES);
        return ImmutableSourceMetrics.of(readAddressMetrics, readConsumedMessages);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {return true;}
        if (!(o instanceof ImmutableSourceMetrics)) {return false;}
        final ImmutableSourceMetrics that = (ImmutableSourceMetrics) o;
        return consumedMessages == that.consumedMessages &&
                Objects.equals(addressMetrics, that.addressMetrics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addressMetrics, consumedMessages);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "addressMetrics=" + addressMetrics +
                ", consumedMessages=" + consumedMessages +
                "]";
    }
}
