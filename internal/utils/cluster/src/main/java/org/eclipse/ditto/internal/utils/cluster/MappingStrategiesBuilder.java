/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cluster;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.base.model.signals.JsonParsableRegistry;
import org.eclipse.ditto.base.model.signals.ShardedMessageEnvelope;
import org.eclipse.ditto.internal.utils.akka.PingCommand;
import org.eclipse.ditto.internal.utils.akka.PingCommandResponse;
import org.eclipse.ditto.internal.utils.akka.SimpleCommand;
import org.eclipse.ditto.internal.utils.akka.SimpleCommandResponse;
import org.eclipse.ditto.internal.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.json.JsonObject;

/**
 * A mutable builder with a fluent API for a Map containing mapping strategies. This builder mainly exists to eliminate
 * redundancy.
 */
@NotThreadSafe
public final class MappingStrategiesBuilder {

    /**
     * Failure message when json deserialization function is null.
     */
    private static final String ERROR_MESSAGE_JSON_DESERIALIZATION_FUNCTION = "JSON deserialization function";

    private final Map<String, JsonParsable<Jsonifiable<?>>> strategies;

    private MappingStrategiesBuilder() {
        strategies = new HashMap<>();
    }

    /**
     * Returns a new instance of {@code MappingStrategiesBuilder}.
     *
     * @return the instance.
     */
    public static MappingStrategiesBuilder newInstance() {
        final MappingStrategiesBuilder builder = new MappingStrategiesBuilder();

        // add the commonly known types:
        builder.add(DittoHeaders.class, jsonObject -> DittoHeaders.newBuilder(jsonObject).build());
        builder.add(ShardedMessageEnvelope.class, ShardedMessageEnvelope::fromJson);
        builder.add(SimpleCommand.class, SimpleCommand::fromJson);
        builder.add(SimpleCommandResponse.class, SimpleCommandResponse::fromJson);
        builder.add(PingCommand.class, PingCommand::fromJson);
        builder.add(PingCommandResponse.class, PingCommandResponse::fromJson);
        builder.add(StatusInfo.class, StatusInfo::fromJson);
        builder.add(StreamAck.class, StreamAck::fromJson);

        return builder;
    }

    /**
     * Adds the given registry to this builder.
     *
     * @param jsonParsableRegistry the registry to be added.
     * @return this builder instance to allow Method Chaining.
     * @throws NullPointerException if {@code jsonParsableRegistry} is {@code null}.
     */
    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    public MappingStrategiesBuilder add(final JsonParsableRegistry<? extends Jsonifiable> jsonParsableRegistry) {
        checkNotNull(jsonParsableRegistry, "jsonParsableRegistry");
        for (final String type : jsonParsableRegistry.getTypes()) {
            add(type, (JsonParsable<Jsonifiable<?>>) jsonParsableRegistry);
        }
        return this;
    }

    /**
     * Adds the given JSON deserialization function for the given class to this builder.
     *
     * @param clazz a class whose simple name is the key for {@code jsonDeserializer}.
     * @param jsonDeserializer a function for creating a particular Jsonifiable based on a JSON object.
     * @return this builder instance to allow Method Chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public MappingStrategiesBuilder add(final Class<?> clazz,
            final Function<JsonObject, Jsonifiable<?>> jsonDeserializer) {

        checkNotNull(clazz, "class");
        return add(clazz.getSimpleName(), jsonDeserializer);
    }

    /**
     * Adds the given JSON deserialization function for the given class to this builder.
     *
     * @param clazz a class whose simple name is the key for {@code jsonDeserializer}.
     * @param jsonDeserializer a function for creating a particular Jsonifiable based on a JSON object.
     * @return this builder instance to allow Method Chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public MappingStrategiesBuilder add(final Class<?> clazz,
            final BiFunction<JsonObject, DittoHeaders, Jsonifiable<?>> jsonDeserializer) {

        checkNotNull(clazz, "class");
        return add(clazz.getSimpleName(), jsonDeserializer::apply);
    }

    /**
     * Adds the given JSON deserialization function for the given type to this builder.
     *
     * @param type the key for {@code jsonDeserializer}.
     * @param jsonDeserializer a function for creating a particular Jsonifiable based on a JSON object.
     * @return this builder instance to allow Method Chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public MappingStrategiesBuilder add(final String type,
            final Function<JsonObject, Jsonifiable<?>> jsonDeserializer) {

        checkNotNull(jsonDeserializer, ERROR_MESSAGE_JSON_DESERIALIZATION_FUNCTION);

        // Translate simple Function to BiFunction ignoring the command headers
        return add(type, (jsonObject, dittoHeaders) -> jsonDeserializer.apply(jsonObject));
    }

    /**
     * Adds the given JSON deserialization function for the given type to this builder.
     *
     * @param type the key for {@code jsonDeserializer}.
     * @param jsonParsable a function for creating a particular Jsonifiable based on a JSON object.
     * @return this builder instance to allow Method Chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public MappingStrategiesBuilder add(final String type, final JsonParsable<Jsonifiable<?>> jsonParsable) {

        checkNotNull(type, "type");
        checkNotNull(jsonParsable, ERROR_MESSAGE_JSON_DESERIALIZATION_FUNCTION);

        // Translate simple Function to BiFunction ignoring the command headers
        strategies.put(type, jsonParsable);
        return this;
    }

    /**
     * Puts the given mapping strategies to this builder.
     *
     * @param mappingStrategies the mapping strategies to be put to this builder.
     * @param <T> the type of the mapping strategies to be put.
     * @return this builder instance to allow Method Chaining.
     * @throws NullPointerException if {@code mappingStrategies} is {@code null}.
     */
    public <T extends Map<String, JsonParsable<Jsonifiable<?>>>> MappingStrategiesBuilder putAll(
            final T mappingStrategies) {
        checkNotNull(mappingStrategies, "mappingStrategies");
        strategies.putAll(mappingStrategies);
        return this;
    }

    /**
     * Returns a modifiable Map containing all previously added strategies. It is suggested <em>not</em> to reuse this
     * instance after the {@code build} method was called.
     *
     * @return the Map.
     */
    public MappingStrategies build() {
        return DefaultMappingStrategies.of(strategies);
    }

}
