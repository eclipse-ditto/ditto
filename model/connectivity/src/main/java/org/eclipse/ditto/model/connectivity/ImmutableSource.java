/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link Source}.
 */
@Immutable
final class ImmutableSource implements Source {

    public static final int DEFAULT_CONSUMER_COUNT = 1;

    private final Set<String> addresses;
    private final int consumerCount;

    private ImmutableSource(final Set<String> addresses, final int consumerCount) {
        this.addresses = Collections.unmodifiableSet(new HashSet<>(addresses));
        this.consumerCount = consumerCount;
    }

    /**
     * Creates a new {@code ImmutableSource} instance with a default consumer count of {@value #DEFAULT_CONSUMER_COUNT}.
     *
     * @param addresses the addresses of this source
     * @return a new instance of ImmutableSource
     */
    public static ImmutableSource of(final String... addresses) {
        return new ImmutableSource(new HashSet<>(Arrays.asList(addresses)), DEFAULT_CONSUMER_COUNT);
    }

    /**
     * Creates a new {@code ImmutableSource} instance with a default consumer count of {@value #DEFAULT_CONSUMER_COUNT}.
     *
     * @param addresses the addresses of this source
     * @return a new instance of ImmutableSource
     */
    public static ImmutableSource of(final Set<String> addresses) {
        return new ImmutableSource(addresses, DEFAULT_CONSUMER_COUNT);
    }

    /**
     * Creates a new {@code ImmutableSource} instance.
     *
     * @param addresses the addresses of this source
     * @param consumerCount number of consumers (connections) that will be opened to the remote server
     * @return a new instance of ImmutableSource
     */
    public static ImmutableSource of(final Set<String> addresses, final int consumerCount) {
        return new ImmutableSource(addresses, consumerCount);
    }

    /**
     * Creates a new {@code ImmutableSource} instance.
     *
     * @param addresses the addresses of this source
     * @param consumerCount number of consumers (connections) that will be opened to the remote server
     * @return a new instance of ImmutableSource
     */
    public static ImmutableSource of(final int consumerCount, final String... addresses) {
        return new ImmutableSource(new HashSet<>(Arrays.asList(addresses)), consumerCount);
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
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(Source.JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate);
        jsonObjectBuilder.set(Source.JsonFields.ADDRESSES, addresses.stream()
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));

        jsonObjectBuilder.set(JsonFields.CONSUMER_COUNT, consumerCount, predicate);
        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code Source} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Source to be created.
     * @return a new Source which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Source fromJson(final JsonObject jsonObject) {
        final Set<String> readSources = jsonObject.getValue(JsonFields.ADDRESSES)
                .map(array -> array.stream()
                        .map(JsonValue::asString)
                        .collect(Collectors.toSet())).orElse(Collections.emptySet());
        final int readConsumerCount =
                jsonObject.getValue(Source.JsonFields.CONSUMER_COUNT).orElse(DEFAULT_CONSUMER_COUNT);
        return ImmutableSource.of(readSources, readConsumerCount);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableSource that = (ImmutableSource) o;
        return consumerCount == that.consumerCount &&
                Objects.equals(addresses, that.addresses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addresses, consumerCount);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "addresses=" + addresses +
                ", consumerCount=" + consumerCount +
                "]";
    }
}
