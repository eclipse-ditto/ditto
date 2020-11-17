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
import java.util.Set;
import java.util.function.Predicate;
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
 * Model of a set of string values with a group.
 */
public final class GroupedAckLabels {

    @Nullable private final String group;
    private final Set<String> ackLabels;

    private GroupedAckLabels(@Nullable final String group, final Set<String> ackLabels) {
        this.group = group;
        this.ackLabels = checkNotNull(ackLabels, "values");
    }

    // TODO: javadoc

    public static GroupedAckLabels of(final Set<String> values) {
        return new GroupedAckLabels(null, values);
    }

    public static GroupedAckLabels of(@Nullable final String group, final Set<String> values) {
        return new GroupedAckLabels(group, values);
    }

    public static GroupedAckLabels fromGrouped(final Grouped<String> grouped) {
        return of(grouped.getGroup().orElse(null), grouped.getValues());
    }

    // TODO: javadoc
    public static <K> Map<K, List<GroupedAckLabels>> deserializeORMultiMap(final ORMultiMap<K, String> orMultiMap,
            final Predicate<K> keyPredicate) {
        final Map<K, scala.collection.immutable.Set<String>> entries =
                CollectionConverters.asJava(orMultiMap.entries());
        return entries.entrySet()
                .stream()
                .filter(entry -> keyPredicate.test(entry.getKey()))
                .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), deserializeAckGroups(entry.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // TODO: javadoc
    public static GroupedAckLabels fromJsonString(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    // TODO: javadoc
    public static GroupedAckLabels fromJson(final JsonObject jsonObject) {
        final String group = jsonObject.getValue(JsonFields.GROUP).orElse(null);
        final Set<String> ackLabels = jsonObject.getValue(JsonFields.ACK_LABELS)
                .stream()
                .flatMap(JsonArray::stream)
                .map(JsonValue::asString)
                .collect(Collectors.toSet());
        return new GroupedAckLabels(group, ackLabels);
    }

    // TODO: javadoc
    public Stream<Pair<String, Set<String>>> streamAsGroupedPair() {
        if (group != null) {
            return Stream.of(Pair.create(group, ackLabels));
        } else {
            return Stream.empty();
        }
    }

    // TODO: javadoc
    public Stream<String> streamAckLabels() {
        return ackLabels.stream();
    }

    public JsonObject toJson() {
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.GROUP, group, field -> !field.getValue().isNull())
                .set(JsonFields.ACK_LABELS, ackLabels.stream()
                        .map(JsonFactory::newValue)
                        .collect(JsonCollectors.valuesToArray()))
                .build();
    }

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

    private static final class JsonFields {

        private static final JsonFieldDefinition<String> GROUP = JsonFactory.newStringFieldDefinition("g");

        private static final JsonFieldDefinition<JsonArray> ACK_LABELS = JsonFactory.newJsonArrayFieldDefinition("a");

    }
}
