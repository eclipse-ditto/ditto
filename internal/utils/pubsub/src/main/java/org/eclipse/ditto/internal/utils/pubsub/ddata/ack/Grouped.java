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
package org.eclipse.ditto.internal.utils.pubsub.ddata.ack;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import akka.cluster.ddata.ORMultiMap;
import akka.japi.Pair;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Model of a set of values with an optional group.
 *
 * @param <T> type of the (optionally grouped) values.
 */
public final class Grouped<T> {

    @Nullable final String group;
    final Set<T> values;

    Grouped(@Nullable final String group, final Set<T> values) {
        this.group = group;
        this.values = checkNotNull(values, "values");
    }

    /**
     * Create a grouped set of values without group name.
     *
     * @param values the values.
     * @param <T> the type of values.
     * @return the grouped values.
     */
    public static <T> Grouped<T> of(final Set<T> values) {
        return new Grouped<>(null, values);
    }

    /**
     * Create a grouped set of values.
     *
     * @param group the group name, or null for values without a group name.
     * @param values the values.
     * @param <T> the type of values.
     * @return the grouped values.
     */
    public static <T> Grouped<T> of(@Nullable final String group, final Set<T> values) {
        return new Grouped<>(group, values);
    }

    /**
     * Retrieve the optional group name.
     *
     * @return the optional group name.
     */
    public Optional<String> getGroup() {
        return Optional.ofNullable(group);
    }

    /**
     * Retrieve the set of values.
     *
     * @return the set of values.
     */
    public Set<T> getValues() {
        return values;
    }

    /**
     * Return a stream containing a pair with guaranteed group name and values if this object has a group name,
     * or an empty stream otherwise.
     *
     * @return the stream.
     */
    public Stream<Pair<String, Set<T>>> streamAsGroupedPair() {
        if (group != null) {
            return Stream.of(Pair.create(group, values));
        } else {
            return Stream.empty();
        }
    }

    /**
     * Stream values of this object.
     *
     * @return a stream of values.
     */
    public Stream<T> streamValues() {
        return values.stream();
    }

    /**
     * Serialize this object as a JSON object.
     *
     * @return JSON representation of this object.
     */
    public JsonObject toJson() {
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.GROUP, group, field -> !field.getValue().isNull())
                .set(JsonFields.ACK_LABELS, values.stream()
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray()))
                .build();
    }

    /**
     * Serialize this object as a JSON string.
     *
     * @return JSON representation of this object.
     */
    public String toJsonString() {
        return toJson().toString();
    }

    /**
     * Deserialize grouped values from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @param deserializeValue deserializer of values.
     * @param <T> type of values.
     * @return grouped values.
     */
    public static <T> Grouped<T> fromJson(final JsonObject jsonObject, final Function<JsonValue, T> deserializeValue) {
        final String group = jsonObject.getValue(JsonFields.GROUP).orElse(null);
        final Set<T> ackLabels = jsonObject.getValue(JsonFields.ACK_LABELS)
                .stream()
                .flatMap(JsonArray::stream)
                .map(deserializeValue)
                .collect(Collectors.toSet());
        return new Grouped<>(group, ackLabels);
    }

    /**
     * Deserialize string values of an {@code ORMultiMap} as grouped acknowledgement labels in JSON format.
     *
     * @param orMultiMap the ORMultiMap.
     * @param valueDeserializer deserializer of values.
     * @param <K> the type of keys in the ORMultiMap.
     * @param <T> the type of values.
     * @return a multi-map from keys to grouped ack labels deserialized from each binding.
     */
    public static <K, T> Map<K, List<Grouped<T>>> deserializeORMultiMap(final ORMultiMap<K, String> orMultiMap,
            final Function<JsonValue, T> valueDeserializer) {
        final Map<K, scala.collection.immutable.Set<String>> entries =
                CollectionConverters.asJava(orMultiMap.entries());
        return entries.entrySet()
                .stream()
                .map(entry -> Pair.create(entry.getKey(), deserializeGroupedList(entry.getValue(), valueDeserializer)))
                .collect(Collectors.toMap(Pair::first, Pair::second));
    }

    private static <T> List<Grouped<T>> deserializeGroupedList(
            final scala.collection.immutable.Set<String> bindingValues,
            final Function<JsonValue, T> valueDeserializer) {
        return CollectionConverters.asJava(bindingValues)
                .stream()
                .map(s -> Grouped.fromJson(JsonObject.of(s), valueDeserializer))
                .toList();
    }

    // JSON field names are 1-character long to conserve space in the distributed data.
    private static final class JsonFields {

        private static final JsonFieldDefinition<String> GROUP = JsonFactory.newStringFieldDefinition("g");

        private static final JsonFieldDefinition<JsonArray> ACK_LABELS = JsonFactory.newJsonArrayFieldDefinition("a");

    }
}
