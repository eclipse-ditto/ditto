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
package org.eclipse.ditto.services.utils.pubsub.ddata.ack;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * A set of acknowledgement labels together with an optional group name for storage in an {@code ORMultiMap}.
 */
public final class GroupedAckLabels {

    @Nullable private final String group;
    private final Set<String> ackLabels;

    private GroupedAckLabels(@Nullable final String group, final Set<String> ackLabels) {
        this.group = group;
        this.ackLabels = checkNotNull(ackLabels, "values");
    }

    /**
     * Create grouped ack labels from grouped strings.
     *
     * @param grouped a set of strings with an optional group name.
     * @return Grouped ack labels.
     */
    public static GroupedAckLabels fromGrouped(final Grouped<String> grouped) {
        return new GroupedAckLabels(grouped.getGroup().orElse(null), grouped.getValues());
    }

    /**
     * Deserialize string values of an {@code ORMultiMap} as grouped acknowledgement labels in JSON format.
     *
     * @param orMultiMap the ORMultiMap.
     * @param <K> the type of keys in the ORMultiMap.
     * @return a multi-map from keys to grouped ack labels deserialized from each binding.
     */
    public static <K> Map<K, List<GroupedAckLabels>> deserializeORMultiMap(final ORMultiMap<K, String> orMultiMap) {
        final Map<K, scala.collection.immutable.Set<String>> entries =
                CollectionConverters.asJava(orMultiMap.entries());
        return entries.entrySet()
                .stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), deserializeAckGroups(entry.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Deserialize grouped ack labels from a JSON string.
     *
     * @param jsonString the JSON string.
     * @return grouped ack labels.
     */
    public static GroupedAckLabels fromJsonString(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Deserialize grouped ack labels from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @return grouped ack labels.
     */
    public static GroupedAckLabels fromJson(final JsonObject jsonObject) {
        final String group = jsonObject.getValue(JsonFields.GROUP).orElse(null);
        final Set<String> ackLabels = jsonObject.getValue(JsonFields.ACK_LABELS)
                .stream()
                .flatMap(JsonArray::stream)
                .map(JsonValue::asString)
                .collect(Collectors.toSet());
        return new GroupedAckLabels(group, ackLabels);
    }

    /**
     * Return a stream containing a pair with guaranteed group name and ack labels if this object has a group name,
     * or an empty stream otherwise.
     *
     * @return the stream.
     */
    public Stream<Pair<String, Set<String>>> streamAsGroupedPair() {
        if (group != null) {
            return Stream.of(Pair.create(group, ackLabels));
        } else {
            return Stream.empty();
        }
    }

    /**
     * Stream ack labels of this object.
     *
     * @return a stream of ack labels.
     */
    public Stream<String> streamAckLabels() {
        return ackLabels.stream();
    }


    /**
     * Get the group name if any exists.
     *
     * @return the group name, or an empty optional.
     */
    public Optional<String> getGroup() {
        return Optional.ofNullable(group);
    }

    /**
     * Serialize this object as a JSON object.
     *
     * @return JSON representation of this object.
     */
    public JsonObject toJson() {
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.GROUP, group, field -> !field.getValue().isNull())
                .set(JsonFields.ACK_LABELS, ackLabels.stream()
                        .map(JsonFactory::newValue)
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

    @Override
    public int hashCode() {
        return Objects.hash(group, ackLabels);
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof GroupedAckLabels) {
            final GroupedAckLabels that = (GroupedAckLabels) other;
            return Objects.equals(group, that.group) && Objects.equals(ackLabels, that.ackLabels);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + toJsonString();
    }

    private static List<GroupedAckLabels> deserializeAckGroups(
            final scala.collection.immutable.Set<String> bindingValues) {
        return CollectionConverters.asJava(bindingValues)
                .stream()
                .map(GroupedAckLabels::fromJsonString)
                .collect(Collectors.toList());
    }

    // JSON field names are 1-character long to conserve space in the distributed data.
    private static final class JsonFields {

        private static final JsonFieldDefinition<String> GROUP = JsonFactory.newStringFieldDefinition("g");

        private static final JsonFieldDefinition<JsonArray> ACK_LABELS = JsonFactory.newJsonArrayFieldDefinition("a");

    }
}
