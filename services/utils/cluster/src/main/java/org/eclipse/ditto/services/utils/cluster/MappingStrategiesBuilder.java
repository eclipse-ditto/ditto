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
package org.eclipse.ditto.services.utils.cluster;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.ThingConstants;
import org.eclipse.ditto.services.utils.akka.SimpleCommand;
import org.eclipse.ditto.services.utils.akka.SimpleCommandResponse;
import org.eclipse.ditto.services.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.acks.things.ThingAcknowledgementFactory;
import org.eclipse.ditto.signals.acks.things.ThingAcknowledgementsFactory;
import org.eclipse.ditto.signals.base.JsonParsableRegistry;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;

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

    private final Map<String, MappingStrategy> strategies;

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
        // If there will be more than one acknowledgement types, do not add them here via builder.
        // Instead provide an infrastructure for JSON serialization like for other signals, i. e. annotation,
        // registries etc.
        builder.add(Acknowledgement.getType(ThingConstants.ENTITY_TYPE),
                jsonObject -> ThingAcknowledgementFactory.fromJson(jsonObject)); // do not replace with lambda!
        builder.add(Acknowledgements.getType(ThingConstants.ENTITY_TYPE),
                jsonObject -> ThingAcknowledgementsFactory.fromJson(jsonObject)); // do not replace with lambda!
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
    public MappingStrategiesBuilder add(final JsonParsableRegistry<? extends Jsonifiable> jsonParsableRegistry) {
        checkNotNull(jsonParsableRegistry, "jsonParsableRegistry");
        final BiFunction<JsonObject, DittoHeaders, Jsonifiable<?>> jsonDeserializer = jsonParsableRegistry::parse;
        for (final String type : jsonParsableRegistry.getTypes()) {
            add(type, jsonDeserializer);
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
        return add(clazz.getSimpleName(), jsonDeserializer);
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
     * @param jsonDeserializer a function for creating a particular Jsonifiable based on a JSON object.
     * @return this builder instance to allow Method Chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public MappingStrategiesBuilder add(final String type,
            final BiFunction<JsonObject, DittoHeaders, Jsonifiable<?>> jsonDeserializer) {

        checkNotNull(type, "type");
        checkNotNull(jsonDeserializer, ERROR_MESSAGE_JSON_DESERIALIZATION_FUNCTION);

        // Translate simple Function to BiFunction ignoring the command headers
        strategies.put(type, jsonDeserializer::apply);
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
    public <T extends Map<String, MappingStrategy>> MappingStrategiesBuilder putAll(final T mappingStrategies) {
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
