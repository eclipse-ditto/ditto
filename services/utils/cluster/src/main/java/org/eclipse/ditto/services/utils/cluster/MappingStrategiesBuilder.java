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
package org.eclipse.ditto.services.utils.cluster;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.utils.akka.SimpleCommand;
import org.eclipse.ditto.services.utils.akka.SimpleCommandResponse;
import org.eclipse.ditto.services.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.eclipse.ditto.signals.base.JsonParsableRegistry;

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
    private final Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> strategies;

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
        builder.add(ShardedMessageEnvelope.class,
                jsonObject -> ShardedMessageEnvelope.fromJson(jsonObject)); // do not replace with lambda!
        builder.add(SimpleCommand.class,
                jsonObject -> SimpleCommand.fromJson(jsonObject)); // do not replace with lambda!
        builder.add(SimpleCommandResponse.class,
                jsonObject -> SimpleCommandResponse.fromJson(jsonObject)); // do not replace with lambda!
        builder.add(StatusInfo.class,
                jsonObject -> StatusInfo.fromJson(jsonObject)); // do not replace with lambda!
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
    public MappingStrategiesBuilder add(
            @Nonnull final JsonParsableRegistry<? extends Jsonifiable> jsonParsableRegistry) {
        checkNotNull(jsonParsableRegistry, "jsonParsableRegistry");
        final Set<String> types = jsonParsableRegistry.getTypes();
        for (final String type : types) {
            strategies.put(type, jsonParsableRegistry::parse);
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
    public MappingStrategiesBuilder add(@Nonnull final Class<?> clazz,
            @Nonnull final Function<JsonObject, Jsonifiable<?>> jsonDeserializer) {
        checkNotNull(clazz, "class");
        checkNotNull(jsonDeserializer, ERROR_MESSAGE_JSON_DESERIALIZATION_FUNCTION);
        // Translate simple Function to BiFunction ignoring the command headers
        strategies.put(clazz.getSimpleName(), (jsonObject, dittoHeaders) -> jsonDeserializer.apply(jsonObject));
        return this;
    }

    /**
     * Adds the given JSON deserialization function for the given class to this builder.
     *
     * @param klasse a class whose simple name is the key for {@code jsonDeserializer}.
     * @param jsonDeserializer a function for creating a particular Jsonifiable based on a JSON object.
     * @return this builder instance to allow Method Chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public MappingStrategiesBuilder add(@Nonnull final Class<?> klasse,
            @Nonnull final BiFunction<JsonObject, DittoHeaders, Jsonifiable<?>> jsonDeserializer) {
        checkNotNull(klasse, "class");
        checkNotNull(jsonDeserializer, ERROR_MESSAGE_JSON_DESERIALIZATION_FUNCTION);
        // Translate simple Function to BiFunction ignoring the command headers
        strategies.put(klasse.getSimpleName(), jsonDeserializer::apply);
        return this;
    }

    /**
     * Adds the given JSON deserialization function for the given type to this builder.
     *
     * @param type the key for {@code jsonDeserializer}.
     * @param jsonDeserializer a function for creating a particular Jsonifiable based on a JSON object.
     * @return this builder instance to allow Method Chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public MappingStrategiesBuilder add(@Nonnull final String type,
            @Nonnull final Function<JsonObject, Jsonifiable<?>> jsonDeserializer) {
        checkNotNull(type, "type");
        checkNotNull(jsonDeserializer, ERROR_MESSAGE_JSON_DESERIALIZATION_FUNCTION);
        // Translate simple Function to BiFunction ignoring the command headers
        strategies.put(type, (jsonObject, dittoHeaders) -> jsonDeserializer.apply(jsonObject));
        return this;
    }

    /**
     * Adds the given JSON deserialization function for the given type to this builder.
     *
     * @param type the key for {@code jsonDeserializer}.
     * @param jsonDeserializer a function for creating a particular Jsonifiable based on a JSON object.
     * @return this builder instance to allow Method Chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public MappingStrategiesBuilder add(@Nonnull final String type,
            @Nonnull final BiFunction<JsonObject, DittoHeaders, Jsonifiable<?>> jsonDeserializer) {
        checkNotNull(type, "type");
        checkNotNull(jsonDeserializer, ERROR_MESSAGE_JSON_DESERIALIZATION_FUNCTION);
        // Translate simple Function to BiFunction ignoring the command headers
        strategies.put(type, jsonDeserializer::apply);
        return this;
    }

    /**
     * Returns a modifiable Map containing all previously added strategies. It is suggested <em>not</em> to reuse this
     * instance after the {@code build} method was called.
     *
     * @return the Map.
     */
    @Nonnull
    public Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> build() {
        return strategies;
    }
}
